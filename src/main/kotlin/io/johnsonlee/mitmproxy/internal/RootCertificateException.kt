package io.johnsonlee.mitmproxy.internal

internal class RootCertificateException(
        message: String,
        cause: Throwable
) : Exception(message, cause)