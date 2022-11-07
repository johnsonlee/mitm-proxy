package io.johnsonlee.mitmproxy.internal

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import io.johnsonlee.mitmproxy.service.Flow
import io.johnsonlee.mitmproxy.service.FlowService
import io.johnsonlee.mitmproxy.service.RegexLocation
import io.johnsonlee.mitmproxy.service.MappingService
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpMessage
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH
import io.netty.handler.codec.http.HttpHeaderNames.HOST
import io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMessage
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.http.HttpMethod.permitsRequestBody
import org.littleshoot.proxy.HttpFiltersAdapter
import org.littleshoot.proxy.impl.ClientToProxyConnection
import org.littleshoot.proxy.impl.ProxyUtils.stripHost
import org.slf4j.LoggerFactory

internal class MitmProxyHttpFilters(
        private val objectMapper: ObjectMapper,
        private val okHttpClient: OkHttpClient,
        private val mappingService: MappingService,
        private val flowService: FlowService,
        private val originalRequest: HttpRequest,
        private val ctx: ChannelHandlerContext?
) : HttpFiltersAdapter(originalRequest, ctx) {

    private val logger = LoggerFactory.getLogger(MitmProxyHttpFilters::class.java)

    private val originalScheme: String
        get() = if ((ctx.handler() as? ClientToProxyConnection)?.sslEngine == null) "http" else "https"

    private val originalHost: String by lazy {
        originalRequest.headers()[HOST] ?: originalRequest.uri().toHttpUrlOrNull()?.host ?: ""
    }

    private val originalPath: String by lazy {
        stripHost(originalRequest.uri()).substringBefore('?')
    }

    private val originalQuery: String by lazy {
        stripHost(originalRequest.uri()).substringAfter('?', "")
    }

    init {
        (originalRequest as? FullHttpMessage)?.retain()
    }

    override fun clientToProxyRequest(httpObject: HttpObject?): HttpResponse? {
        return (httpObject as? HttpRequest)?.run {
            mapToLocal() ?: mapToRemote()
        }?.also(::recordFlow)
    }

    override fun serverToProxyResponse(httpObject: HttpObject?): HttpObject? {
        return httpObject.also(::recordFlow)
    }

    private fun recordFlow(httpObject: HttpObject?) {
        (httpObject as? HttpResponse)?.let { response ->
            flowService += Flow(
                    id = flowService.nextId(),
                    request = Flow.Request(
                            method = originalRequest.method().name(),
                            url = HttpUrl.Builder()
                                    .scheme(originalScheme)
                                    .host(originalHost)
                                    .encodedPath(originalPath)
                                    .encodedQuery(originalQuery)
                                    .build()
                                    .toUrl(),
                            headers = originalRequest.headers().toMap(),
                            body = originalRequest.body()
                    ),
                    response = Flow.Response(
                            status = response.status().code(),
                            headers = response.headers().toMap(),
                            body = response.body()
                    )
            )
        }

        (originalRequest as? FullHttpMessage)?.release()
    }

    private fun HttpRequest.mapToLocal(): HttpResponse? {
        return mappingService.locals.entries.firstOrNull { (from, _) ->
            from matches this
        }?.let { (_, to) ->
            DefaultFullHttpResponse(
                    protocolVersion(),
                    HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(to.inputStream.readBytes()),
                    DefaultHttpHeaders().add(CONTENT_LENGTH, to.contentLength()),
                    DefaultHttpHeaders()
            )
        }
    }

    private fun HttpRequest.mapToRemote(): HttpResponse? {
        val remote = mappingService.remotes.entries.firstOrNull { (from, _) ->
            from matches this
        }?.value ?: return null

        val remoteHost = remote.host ?: headers()[HOST]
        val remotePort = remoteHost.substringAfter(':', "").takeIf(String::isNotBlank)?.toIntOrNull()?.takeIf { it in 1..65535 }
        val remoteUrl = HttpUrl.Builder().apply {
            scheme(remote.scheme ?: "http")
            host(remoteHost.substringBefore(':'))
            remotePort?.let(::port)
            stripHost(uri()).takeIf(String::isNotEmpty)?.let(::encodedPath)
            uri().substringAfter('?', "").takeIf(String::isNotEmpty)?.let(::encodedQuery)
        }.build()

        val request = Request.Builder().apply {
            url(remoteUrl)
            headers().forEach { (name, value) ->
                addHeader(name, value)
            }
            method(method().name(), if (permitsRequestBody(method().name()) && this is FullHttpRequest) {
                ByteBufUtil.getBytes(content()).toRequestBody(HttpUtil.getMimeType(this)?.toString()?.toMediaTypeOrNull())
            } else null)
        }.build()

        return okHttpClient.newCall(request).execute().use { response ->
            DefaultFullHttpResponse(
                    protocolVersion(),
                    HttpResponseStatus(response.code, response.message),
                    Unpooled.wrappedBuffer(response.body?.bytes() ?: ByteArray(0)),
                    DefaultHttpHeaders().apply {
                        response.headers.forEach { (name, value) ->
                            add(name, value)
                        }
                        add(CONTENT_LENGTH, response.body?.contentLength() ?: 0)
                    },
                    DefaultHttpHeaders()
            )
        }
    }

    private fun HttpMessage.body(): Any? {
        (this as? FullHttpMessage)?.takeIf {
            content().capacity() > 0
        } ?: return null

        val contentType = HttpUtil.getMimeType(this)
        val contentEncoding = headers()[CONTENT_ENCODING]

        if (contentEncoding == null && contentType != null) {
            val charset = HttpUtil.getCharset(this)
            val body = content().toString(charset)

            if (APPLICATION_JSON.contentEqualsIgnoreCase(contentType)) {
                try {
                    return objectMapper.readTree(body)
                } catch (e: JacksonException) {
                    logger.error("Failed to parse JSON", e)
                }
            }

            return body
        }

        return "(${HttpUtil.getContentLength(this, 0)} bytes)"
    }

    private infix fun RegexLocation.matches(request: HttpRequest): Boolean {
        val fromScheme = if ((ctx.handler() as? ClientToProxyConnection)?.sslEngine == null) "http" else "https"
        val fromHost = request.headers()[HOST]
        val fromPath = stripHost(request.uri()).substringBefore('?')
        val fromQuery = request.uri().substringAfter('?', "")

        return (scheme == null || scheme.matches(fromScheme))
                && (host == null || fromHost == null || host.matches(fromHost))
                && (path == null || path.matches(fromPath))
                && (query == null || query.matches(fromQuery))
    }

}

private fun HttpHeaders.toMap(): Map<String, String> {
    return entries().associate { (name, _) ->
        name to getAsString(name)
    }
}
