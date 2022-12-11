package io.johnsonlee.mitmproxy.internal.util

internal class MillisecondsDuration {
    private val ts = System.currentTimeMillis()
    override fun toString(): String = (System.currentTimeMillis() - ts).toString()
}