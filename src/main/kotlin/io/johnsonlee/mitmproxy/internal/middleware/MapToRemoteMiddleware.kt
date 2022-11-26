package io.johnsonlee.mitmproxy.internal.middleware

import io.johnsonlee.mitmproxy.Middleware
import io.johnsonlee.mitmproxy.getValue
import io.johnsonlee.mitmproxy.internal.proxy.MitmFilters
import io.johnsonlee.mitmproxy.internal.util.matches
import io.johnsonlee.mitmproxy.internal.util.toRequest
import io.johnsonlee.mitmproxy.internal.util.toResponse
import io.johnsonlee.mitmproxy.service.MappingService
import io.netty.handler.codec.http.HttpHeaderNames.HOST
import io.netty.handler.codec.http.HttpResponse
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.littleshoot.proxy.impl.ProxyUtils.stripHost

internal class MapToRemoteMiddleware(
        private val filters: MitmFilters
) : Middleware {

    private val mappingService: MappingService by filters

    private val okHttpClient: OkHttpClient by filters

    override fun invoke(pipeline: Middleware.Pipeline): HttpResponse {
        val request = pipeline.request
        val uri = request.uri()
        val remote = mappingService.remotes.entries.firstOrNull { (from, _) ->
            (from.scheme == null || from.scheme matches filters.originalScheme) && (request matches from)
        }?.value ?: return pipeline()
        val remoteHost = remote.host ?: request.headers()[HOST]
        val remotePort = remoteHost.substringAfter(':', "").takeIf(String::isNotBlank)?.toIntOrNull()?.takeIf { it in 1..65535 }
        val remoteUrl = HttpUrl.Builder().apply {
            scheme(remote.scheme ?: "http")
            host(remoteHost.substringBefore(':'))
            remotePort?.let(::port)
            stripHost(uri).substringBefore('?').takeIf(String::isNotEmpty)?.let(::encodedPath)
            uri.substringAfter('?', "").takeIf(String::isNotEmpty)?.let(::encodedQuery)
        }.build()
        val okRequest = request.toRequest(filters.originalScheme).newBuilder().url(remoteUrl).build()
        return okHttpClient.newCall(okRequest).execute().use {
            it.toResponse(request.protocolVersion())
        }

    }

}