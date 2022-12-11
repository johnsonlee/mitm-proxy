package io.johnsonlee.mitmproxy.internal.util

import io.netty.handler.codec.http.HttpHeaders

internal class Headers internal constructor(private val headers: HttpHeaders) : Map<String, String> {

    override val entries: Set<Map.Entry<String, String>>
        get() = headers.entries().toSet()

    override val keys: Set<String>
        get() = headers.names()

    override val size: Int
        get() = headers.size()

    override val values: Collection<String>
        get() = headers.map(MutableMap.MutableEntry<String, String>::value)

    override fun isEmpty(): Boolean {
        return headers.isEmpty
    }

    override fun get(key: String): String? {
        return headers.getAll(key)?.joinToString(", ")
    }

    override fun containsValue(value: String): Boolean {
        return headers.entries().any { it.value == value }
    }

    override fun containsKey(key: String): Boolean {
        return headers.contains(key)
    }

}