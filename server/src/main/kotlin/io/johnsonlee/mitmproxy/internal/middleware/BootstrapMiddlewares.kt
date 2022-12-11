package io.johnsonlee.mitmproxy.internal.middleware

import io.johnsonlee.mitmproxy.Middleware
import java.util.function.Supplier

internal fun interface BootstrapMiddlewares : Supplier<List<Middleware>> {

    companion object {

        @JvmSynthetic
        internal fun of(middlewares: List<Middleware>) = BootstrapMiddlewares { middlewares }

    }

}