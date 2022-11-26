package io.johnsonlee.mitmproxy.internal.middleware

import io.johnsonlee.mitmproxy.Middleware
import io.johnsonlee.mitmproxy.getValue
import io.johnsonlee.mitmproxy.internal.proxy.MitmFilters
import io.johnsonlee.mitmproxy.internal.util.toRequest
import io.johnsonlee.mitmproxy.internal.util.toResponse
import io.netty.handler.codec.http.HttpResponse
import okhttp3.OkHttpClient

internal class ProxyToServerMiddleware(
        private val filters: MitmFilters
) : Middleware {

    private val okHttpClient: OkHttpClient by filters

    override fun invoke(pipeline: Middleware.Pipeline): HttpResponse {
        val request = pipeline.request
        return okHttpClient.newCall(request.toRequest(filters.originalScheme)).execute().use { response ->
            response.toResponse(request.protocolVersion())
        }
    }

}