package io.johnsonlee.mitmproxy.internal.proxy

import io.johnsonlee.mitmproxy.Middleware
import io.johnsonlee.mitmproxy.getValue
import io.johnsonlee.mitmproxy.internal.middleware.BootstrapMiddlewares
import io.johnsonlee.mitmproxy.internal.middleware.EndOfChainException
import io.johnsonlee.mitmproxy.internal.middleware.FlowRecordingMiddleware
import io.johnsonlee.mitmproxy.internal.middleware.MapToLocalMiddleware
import io.johnsonlee.mitmproxy.internal.middleware.MapToRemoteMiddleware
import io.johnsonlee.mitmproxy.internal.middleware.MicrometerMiddleware
import io.johnsonlee.mitmproxy.internal.middleware.MiddlewarePipeline
import io.johnsonlee.mitmproxy.internal.middleware.ServerToProxyMiddleware
import io.johnsonlee.mitmproxy.internal.util.release
import io.johnsonlee.mitmproxy.internal.util.retain
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import org.littleshoot.proxy.HttpFiltersAdapter
import org.littleshoot.proxy.impl.ClientToProxyConnection
import org.springframework.context.ApplicationContext

internal class MitmFilters(
        private val application: ApplicationContext,
        originalRequest: HttpRequest,
        ctx: ChannelHandlerContext?
) : HttpFiltersAdapter(originalRequest, ctx), ApplicationContext by application {

    val bootstrap: BootstrapMiddlewares by application

    val originalScheme: String
        get() = if ((ctx?.handler() as? ClientToProxyConnection)?.sslEngine == null) "http" else "https"

    lateinit var request: HttpRequest

    lateinit var outbound: MiddlewarePipeline

    override fun proxyToServerRequest(httpObject: HttpObject?): HttpResponse? {
        this.request = (httpObject as? HttpRequest)?.takeIf {
            it.method() != HttpMethod.CONNECT
        }?.retain() ?: return null

        val middlewares = mutableListOf<Middleware>()
        middlewares += bootstrap.get()
        middlewares += MapToLocalMiddleware(this)
        middlewares += MapToRemoteMiddleware(this)
        // middlewares += ProxyToServerMiddleware(this)
        this.outbound = MiddlewarePipeline(this.request, middlewares)

        return try {
            outbound()
        } catch (e: EndOfChainException) {
            null
        }
    }

    override fun serverToProxyResponse(httpObject: HttpObject?): HttpObject? {
        val response = (httpObject as? HttpResponse)?.retain() ?: return null
        val middlewares = mutableListOf<Middleware>()
        middlewares += bootstrap.get()
        middlewares += MicrometerMiddleware(this)
        middlewares += FlowRecordingMiddleware(this)
        middlewares += ServerToProxyMiddleware(response)
        val inbound = MiddlewarePipeline(this.request, middlewares)

        try {
            return inbound()
        } finally {
            request.release()
            response.release()
        }
    }

}