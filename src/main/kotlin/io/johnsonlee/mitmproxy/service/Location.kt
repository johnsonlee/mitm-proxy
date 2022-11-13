package io.johnsonlee.mitmproxy.service

data class RegexLocation(
        val scheme: Regex? = null,
        val host: Regex? = null,
        val path: Regex? = null,
        val query: Regex? = null
) {
    init {
        require(scheme != null || host != null || path != null || query != null) {
            "At least one of scheme, host, path or query should be specified"
        }
    }
}

data class LiteralLocation(
        val scheme: String? = null,
        val host: String? = null,
        val path: String? = null,
        val query: String? = null
) {
    init {
        require(scheme != null || host != null || path != null || query != null) {
            "At least one of scheme, host, path or query should be specified"
        }
    }
}