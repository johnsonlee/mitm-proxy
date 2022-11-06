package io.johnsonlee.mitmproxy.internal

internal class FakeCertificateException(
        message: String,
        cause: Throwable
) : RuntimeException(message, cause)