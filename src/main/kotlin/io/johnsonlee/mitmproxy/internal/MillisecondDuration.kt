package io.johnsonlee.mitmproxy.internal

internal class MillisecondsDuration {
    private val ts = System.currentTimeMillis()
    override fun toString(): String = (System.currentTimeMillis() - ts).toString()
}