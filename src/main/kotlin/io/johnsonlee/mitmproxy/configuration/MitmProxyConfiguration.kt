package io.johnsonlee.mitmproxy.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import io.johnsonlee.mitmproxy.internal.CertificateSniffingMitmManager
import io.johnsonlee.mitmproxy.internal.FlowTracker
import io.johnsonlee.mitmproxy.internal.MitmProxyHttpFilters
import io.johnsonlee.mitmproxy.service.FlowService
import io.johnsonlee.mitmproxy.service.MappingService
import io.micrometer.core.instrument.MeterRegistry
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpRequest
import okhttp3.OkHttpClient
import org.littleshoot.proxy.HttpFilters
import org.littleshoot.proxy.HttpFiltersSourceAdapter
import org.littleshoot.proxy.HttpProxyServerBootstrap
import org.littleshoot.proxy.impl.DefaultHttpProxyServer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private const val MAX_BUFFER_SIZE = 10 * 1024 * 1024

private val MITMPROXY_ALLOW_LOCAL_ONLY: String? by System.getenv().withDefault { null }

@Configuration
class MitmProxyConfiguration {

    @Bean
    fun httpProxyServer(
            @Value("\${mitmproxy.port:8888}") port: Int,
            objectMapper: ObjectMapper,
            okHttpClient: OkHttpClient,
            mappingService: MappingService,
            flowService: FlowService,
            meterRegistry: MeterRegistry
    ): HttpProxyServerBootstrap = DefaultHttpProxyServer.bootstrap()
            .withPort(port)
            .withAllowLocalOnly(MITMPROXY_ALLOW_LOCAL_ONLY?.toBoolean() ?: true)
            .withProxyAlias("mitmproxy")
            .withConnectTimeout(60_000)
            .withManInTheMiddle(CertificateSniffingMitmManager())
            .plusActivityTracker(FlowTracker())
            .withFiltersSource(object : HttpFiltersSourceAdapter() {
                override fun getMaximumRequestBufferSizeInBytes(): Int = MAX_BUFFER_SIZE
                override fun getMaximumResponseBufferSizeInBytes(): Int = MAX_BUFFER_SIZE
                override fun filterRequest(
                        originalRequest: HttpRequest,
                        ctx: ChannelHandlerContext?
                ): HttpFilters = MitmProxyHttpFilters(
                        objectMapper,
                        okHttpClient,
                        mappingService,
                        flowService,
                        meterRegistry,
                        originalRequest,
                        ctx
                )
            })

}