package io.johnsonlee.mitmproxy.configuration

import io.johnsonlee.mitmproxy.internal.ssl.CertificateSniffingMitmManager
import org.littleshoot.proxy.HttpFiltersSource
import org.littleshoot.proxy.HttpProxyServerBootstrap
import org.littleshoot.proxy.impl.DefaultHttpProxyServer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
internal class MitmProxyConfiguration {

    @Bean
    fun httpProxyServer(
            @Value("\${spring.application.name}") applicationName: String,
            @Value("\${mitmproxy.port:8888}") port: Int,
            @Value("\${mitmproxy.allow-local-only:false}") allowLocalOnly: Boolean,
            @Value("\${mitmproxy.connect-timeout:PT60S}") connectTimeout: Duration,
            filtersSource: HttpFiltersSource,
    ): HttpProxyServerBootstrap = DefaultHttpProxyServer.bootstrap()
            .withPort(port)
            .withAllowLocalOnly(allowLocalOnly)
            .withProxyAlias(applicationName)
            .withConnectTimeout(connectTimeout.toMillis().toInt())
            .withManInTheMiddle(CertificateSniffingMitmManager())
            .withFiltersSource(filtersSource)

}