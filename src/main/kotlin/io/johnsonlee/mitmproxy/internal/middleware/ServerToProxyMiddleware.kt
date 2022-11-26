package io.johnsonlee.mitmproxy.internal.middleware

import io.johnsonlee.mitmproxy.Middleware
import io.netty.handler.codec.http.HttpResponse

internal class ServerToProxyMiddleware(
        private val response: HttpResponse
) : Middleware {

    override fun invoke(pipeline: Middleware.Pipeline) = response

}