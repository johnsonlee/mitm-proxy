package io.johnsonlee.mitmproxy.internal.middleware

import io.johnsonlee.mitmproxy.Middleware
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import org.slf4j.LoggerFactory

internal class MiddlewarePipeline private constructor(
        override val request: HttpRequest,
        internal val middlewares: List<Middleware>,
        private val index: Int
) : Middleware.Pipeline {

    internal constructor(request: HttpRequest, middlewares: List<Middleware>) : this(request, middlewares, 0)

    override fun invoke(): HttpResponse {
        val next = middlewares.getOrNull(index) ?: throw EndOfChainException(request)
        logger.debug("Proceed to middleware [{}]: {}", index, next.javaClass.name)
        val pipeline = MiddlewarePipeline(request, middlewares, index + 1)
        return next(pipeline)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MiddlewarePipeline::class.java)
    }

}

internal class EndOfChainException(
        internal val request: HttpRequest
) : Throwable()
