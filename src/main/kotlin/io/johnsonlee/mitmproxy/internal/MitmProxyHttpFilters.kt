package io.johnsonlee.mitmproxy.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.johnsonlee.mitmproxy.service.MappingService
import io.johnsonlee.mitmproxy.service.Flow
import io.johnsonlee.mitmproxy.service.FlowService
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpMessage
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON
import io.netty.handler.codec.http.HttpMessage
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import org.littleshoot.proxy.HttpFiltersAdapter
import org.littleshoot.proxy.impl.ClientToProxyConnection
import java.net.URI

internal class MitmProxyHttpFilters(
        private val objectMapper: ObjectMapper,
        private val mappingService: MappingService,
        private val flowService: FlowService,
        private val originalRequest: HttpRequest,
        private val ctx: ChannelHandlerContext?
) : HttpFiltersAdapter(originalRequest, ctx) {

    init {
        (originalRequest as? FullHttpMessage)?.retain()
    }


    override fun clientToProxyRequest(httpObject: HttpObject?): HttpResponse? {
        return mapToLocal(httpObject)
    }

    override fun proxyToServerRequest(httpObject: HttpObject?): HttpResponse? {
        return mapToRemote(httpObject)
    }

    override fun serverToProxyResponse(httpObject: HttpObject?): HttpObject? {
        return httpObject.also(::recordFlow)
    }

    private fun recordFlow(httpObject: HttpObject?) {
        (httpObject as? HttpResponse)?.let { response ->
            val uri = URI.create(originalRequest.uri())
            val hostname = originalRequest.headers()[HttpHeaderNames.HOST]
            val protocol = if ((ctx.handler() as? ClientToProxyConnection)?.sslEngine == null) "http" else "https"

            flowService += Flow(
                    id = flowService.nextId(),
                    request = Flow.Request(
                            method = originalRequest.method().name(),
                            uri = if (uri.isAbsolute) uri else URI.create("${protocol}://${hostname}${uri.path}"),
                            headers = originalRequest.headers().associate { (name, _) ->
                                name to originalRequest.headers().getAsString(name)
                            },
                            body = originalRequest.body()
                    ),
                    response = Flow.Response(
                            status = response.status().code(),
                            headers = response.headers().associate { (name, _) ->
                                name to response.headers().getAsString(name)
                            },
                            body = response.body()
                    )
            )
        }

        (originalRequest as? FullHttpMessage)?.release()
    }

    private fun mapToLocal(httpObject: HttpObject?): HttpResponse? {
        return (httpObject as? HttpRequest)?.let { request ->
            mappingService.locals.entries.firstOrNull { (from, _) ->
                from(request)
            }?.let { (_, to) ->
                DefaultFullHttpResponse(
                        request.protocolVersion(),
                        HttpResponseStatus.OK,
                        Unpooled.wrappedBuffer(to.inputStream.readBytes()),
                        DefaultHttpHeaders().add(HttpHeaderNames.CONTENT_LENGTH, to.contentLength()),
                        DefaultHttpHeaders()
                )
            }
        }
    }

    private fun mapToRemote(httpObject: HttpObject?): HttpResponse? {
        (httpObject as? HttpRequest)?.let { request ->
            mappingService.remotes.entries.firstOrNull { (from, _) ->
                from(request)
            }?.value?.let { remote ->
                val originalHost = request.headers()[HttpHeaderNames.HOST]
                val originalUri = request.uri()

                request.uri = remote.toURI().toString()
                request.headers()
                        .remove(HttpHeaderNames.HOST)
                        .add(HttpHeaderNames.HOST, remote.host)
                        .add("X-Original-Host", originalHost)
                        .add("X-Original-Uri", originalUri)
            }
        }

        return null
    }

    private fun HttpMessage.body(): Any? {
        (this as? FullHttpMessage)?.takeIf {
            content().capacity() > 0
        } ?: return null

        val contentType = HttpUtil.getMimeType(this)
        val contentEncoding = headers()[HttpHeaderNames.CONTENT_ENCODING]

        if (contentEncoding == null && contentType != null) {
            val charset = HttpUtil.getCharset(this)
            val body = content().toString(charset)

            if (APPLICATION_JSON.contentEqualsIgnoreCase(contentType)) {
                return objectMapper.readTree(body)
            }

            return body
        }

        return "(${HttpUtil.getContentLength(this, 0)} bytes)"
    }

}