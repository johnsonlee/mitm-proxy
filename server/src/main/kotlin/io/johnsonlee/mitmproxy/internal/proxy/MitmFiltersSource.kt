package io.johnsonlee.mitmproxy.internal.proxy

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpRequest
import org.littleshoot.proxy.HttpFilters
import org.littleshoot.proxy.HttpFiltersSourceAdapter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component
import org.springframework.util.unit.DataSize

@Component
internal class MitmFiltersSource(
        @Value("\${mitmproxy.max-buffer-size:100MB}")
        private val maxBufSize: DataSize
) : HttpFiltersSourceAdapter(), ApplicationContextAware {

    private lateinit var application: ApplicationContext

    override fun getMaximumRequestBufferSizeInBytes(): Int = maxBufSize.toBytes().toInt()

    override fun getMaximumResponseBufferSizeInBytes(): Int = maxBufSize.toBytes().toInt()

    override fun filterRequest(originalRequest: HttpRequest, ctx: ChannelHandlerContext?): HttpFilters {
        val context by lazy { application }
        return MitmFilters(context, originalRequest, ctx)
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.application = applicationContext
    }

}