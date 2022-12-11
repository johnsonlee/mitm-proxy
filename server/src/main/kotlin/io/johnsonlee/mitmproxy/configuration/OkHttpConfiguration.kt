package io.johnsonlee.mitmproxy.configuration

import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
internal class OkHttpConfiguration {

    @Bean
    fun okHttpClient(
            @Value("\${mitmproxy.okhttp.connect-timeout:PT30S}") connectTimeout: Duration,
            @Value("\${mitmproxy.okhttp.read-timeout:PT30S}") readTimeout: Duration,
            @Value("\${mitmproxy.okhttp.write-timeout:PT30S}") writeTimeout: Duration,
    ): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(connectTimeout)
            .readTimeout(readTimeout)
            .writeTimeout(writeTimeout)
            .build()

}