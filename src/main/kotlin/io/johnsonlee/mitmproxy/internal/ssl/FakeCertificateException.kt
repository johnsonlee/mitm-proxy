package io.johnsonlee.mitmproxy.internal.ssl

internal class FakeCertificateException(
        message: String,
        cause: Throwable
) : RuntimeException(message, cause)