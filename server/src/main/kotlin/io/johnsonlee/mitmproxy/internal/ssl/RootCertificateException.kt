package io.johnsonlee.mitmproxy.internal.ssl

internal class RootCertificateException(
        message: String,
        cause: Throwable
) : Exception(message, cause)