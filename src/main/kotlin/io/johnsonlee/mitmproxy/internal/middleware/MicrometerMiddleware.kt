package io.johnsonlee.mitmproxy.internal.middleware

import io.johnsonlee.mitmproxy.Middleware
import io.johnsonlee.mitmproxy.getValue
import io.johnsonlee.mitmproxy.internal.proxy.MitmFilters
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.netty.handler.codec.http.HttpHeaderNames.HOST
import io.netty.handler.codec.http.HttpMessage
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpUtil
import org.littleshoot.proxy.impl.ProxyUtils.stripHost

internal class MicrometerMiddleware(
        filters: MitmFilters,
) : Middleware {

    private val meterRegistry: MeterRegistry by filters

    override fun invoke(pipeline: Middleware.Pipeline): HttpResponse {
        val request = pipeline.request
        val response = pipeline()
        val host = request.headers()[HOST]
        val method = request.method().name()
        val uri = stripHost(request.uri())
        val summarize: HttpMessage.(String, String) -> DistributionSummary = { name, desc ->
            val self = this
            DistributionSummary.builder(name)
                    .baseUnit("bytes")
                    .tag("host", host)
                    .tag("path", uri.substringBefore('?'))
                    .tag("method", method)
                    .tag("uri", uri)
                    .apply {
                        if (self is HttpResponse) {
                            tag("status", self.status().code().toString())
                        }
                    }
                    .description(desc)
                    .register(meterRegistry)
        }

        Counter.builder("mitmproxy.requests")
                .description("The number of requests")
                .tag("host", host)
                .tag("method", method)
                .tag("uri", uri)
                .register(meterRegistry)
                .increment()
        request.summarize("mitmproxy.request.size", "The content length of request")
                .record(HttpUtil.getContentLength(request).toDouble())
        response.summarize("mitmproxy.response.size", "The content length of response")
                .record(HttpUtil.getContentLength(response).toDouble())
        return response
    }

}