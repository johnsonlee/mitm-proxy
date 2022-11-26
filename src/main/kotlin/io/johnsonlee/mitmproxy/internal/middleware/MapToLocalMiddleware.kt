package io.johnsonlee.mitmproxy.internal.middleware

import io.johnsonlee.mitmproxy.Middleware
import io.johnsonlee.mitmproxy.getValue
import io.johnsonlee.mitmproxy.internal.proxy.MitmFilters
import io.johnsonlee.mitmproxy.internal.util.matches
import io.johnsonlee.mitmproxy.service.MappingService
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus

internal class MapToLocalMiddleware(
        private val filters: MitmFilters
) : Middleware {

    private val mappingService: MappingService by filters

    override fun invoke(pipeline: Middleware.Pipeline): HttpResponse {
        val request = pipeline.request
        val local = mappingService.locals.entries.firstOrNull { (from, _) ->
            (from.scheme == null || from.scheme matches filters.originalScheme) && (request matches from)
        }?.value ?: return pipeline()

        return DefaultFullHttpResponse(
                request.protocolVersion(),
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(local.inputStream.readBytes()),
                DefaultHttpHeaders().add(CONTENT_LENGTH, local.contentLength()),
                DefaultHttpHeaders()
        )

    }

}
