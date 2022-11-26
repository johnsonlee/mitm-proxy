package io.johnsonlee.mitmproxy.internal.util

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import io.johnsonlee.mitmproxy.service.RegexLocation
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpMessage
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH
import io.netty.handler.codec.http.HttpHeaderNames.HOST
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMessage
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.http.HttpMethod
import org.littleshoot.proxy.impl.ProxyUtils.stripHost
import org.slf4j.LoggerFactory

internal object HttpExtension {
    internal val logger = LoggerFactory.getLogger(HttpExtension::class.java)
}

@JvmSynthetic
internal infix fun HttpRequest.matches(from: RegexLocation): Boolean {
    val host = headers()[HOST]
    val uri = uri()
    val path = stripHost(uri).substringBefore('?')
    val query = uri.substringAfter('?', "")

    return (from.host == null || host == null || from.host.matches(host))
            && (from.path == null || from.path.matches(path))
            && (from.query == null || from.query.matches(query))
}

@JvmSynthetic
internal fun HttpMessage.body(objectMapper: ObjectMapper): Any? {
    (this as? FullHttpMessage)?.takeIf {
        content().capacity() > 0
    } ?: return null

    val contentType = HttpUtil.getMimeType(this)
    val contentEncoding = headers()[CONTENT_ENCODING]
    val contentLength = HttpUtil.getContentLength(this, 0)

    if (contentEncoding == null && (contentType != null || contentLength in 1..(5 * 1024 * 1024))) {
        val charset = HttpUtil.getCharset(this)
        val body = content().toString(charset)

        if (HttpHeaderValues.APPLICATION_JSON.contentEqualsIgnoreCase(contentType)) {
            try {
                return objectMapper.readTree(body)
            } catch (e: JacksonException) {
                HttpExtension.logger.error("Failed to parse JSON", e)
            }
        }

        return body
    }

    return "(${contentLength} bytes)"
}

@JvmSynthetic
internal fun HttpRequest.toRequest(scheme: String = "https"): Request {
    val uri = uri()
    val host = headers()[HOST]
    val port = if (scheme == "https") "443" else "80"
    val url = HttpUrl.Builder()
            .scheme(scheme)
            .host(host.substringBefore(':'))
            .port(host.substringAfter(':', port).toInt())
            .encodedPath(stripHost(uri).substringBefore('?'))
            .encodedQuery(uri.substringAfter('?', ""))
            .build()
            .toUrl()
    return Request.Builder()
            .url(url)
            .headers(Headers(headers()).toHeaders())
            .method(method().name(), if (HttpMethod.permitsRequestBody(method().name()) && this is FullHttpRequest) {
                ByteBufUtil.getBytes(content())
                        .toRequestBody(HttpUtil.getMimeType(this)?.toString()?.toMediaTypeOrNull())
            } else null).build()
}

@JvmSynthetic
internal fun Response.toResponse(protocolVersion: HttpVersion = HttpVersion.HTTP_1_1): HttpResponse {
    return DefaultFullHttpResponse(
            protocolVersion,
            HttpResponseStatus(code, message),
            Unpooled.wrappedBuffer(body?.bytes() ?: ByteArray(0)),
            headers.fold(DefaultHttpHeaders().add(CONTENT_LENGTH, body?.contentLength()
                    ?: 0)) { headers, (name, value) ->
                headers.add(name, value)
            },
            DefaultHttpHeaders()
    )
}

@JvmSynthetic
internal fun <T : HttpMessage> T.retain(): T = apply {
    (this as? FullHttpMessage)?.retain()
}

@JvmSynthetic
internal fun <T : HttpMessage> T.release(): T = apply {
    (this as? FullHttpMessage)?.release()
}