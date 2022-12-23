package io.johnsonlee.mitmproxy.internal.middleware

import com.fasterxml.jackson.databind.ObjectMapper
import io.johnsonlee.mitmproxy.Middleware
import io.johnsonlee.mitmproxy.getValue
import io.johnsonlee.mitmproxy.internal.proxy.MitmFilters
import io.johnsonlee.mitmproxy.internal.util.Headers
import io.johnsonlee.mitmproxy.internal.util.body
import io.johnsonlee.mitmproxy.service.Flow
import io.johnsonlee.mitmproxy.service.FlowService
import io.netty.handler.codec.http.HttpHeaderNames.HOST
import io.netty.handler.codec.http.HttpResponse
import org.littleshoot.proxy.impl.ClientToProxyConnection
import org.littleshoot.proxy.impl.ProxyUtils.stripHost
import java.net.URL

internal class FlowRecordingMiddleware(
        private val filters: MitmFilters
) : Middleware {

    private val t0 = System.currentTimeMillis()

    private val flowService: FlowService by filters

    private val objectMapper: ObjectMapper by filters

    private val clientToProxy: ClientToProxyConnection? = filters.context?.handler() as? ClientToProxyConnection

    override fun invoke(pipeline: Middleware.Pipeline): HttpResponse {
        val request = pipeline.request
        val response = pipeline()
        val duration = System.currentTimeMillis() - t0
        val url = "${filters.originalScheme}://${request.headers()[HOST]}${stripHost(request.uri())}"
        val ssl = clientToProxy?.sslEngine?.sslParameters

        flowService += Flow(
                id = flowService.nextId(),
                client = Flow.ClientInfo(clientToProxy?.clientAddress?.toString()),
                ssl = Flow.SslInfo(
                        cipherSuites = ssl?.cipherSuites?.toList() ?: emptyList(),
                        protocols = ssl?.protocols?.toList() ?: emptyList(),
                ),
                duration = duration,
                request = Flow.Request(
                        method = request.method().name(),
                        url = URL(url),
                        headers = Headers(request.headers()),
                        body = request.body(objectMapper)
                ),
                response = Flow.Response(
                        status = response.status().code(),
                        headers = Headers(response.headers()),
                        body = response.body(objectMapper)
                )
        )

        return response
    }

}