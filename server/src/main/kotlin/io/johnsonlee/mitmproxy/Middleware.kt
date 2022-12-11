package io.johnsonlee.mitmproxy

import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse

fun interface Middleware {

    interface Pipeline {
        val request: HttpRequest
        operator fun invoke(): HttpResponse
    }

    operator fun invoke(pipeline: Pipeline): HttpResponse

}
