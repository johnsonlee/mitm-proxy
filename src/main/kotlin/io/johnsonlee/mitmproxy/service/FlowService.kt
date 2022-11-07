package io.johnsonlee.mitmproxy.service

import org.springframework.stereotype.Service
import java.net.URL
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

@Service
class FlowService {

    private val _id = AtomicInteger(0)

    private val _flows = ConcurrentLinkedDeque<Flow>()

    val flows: List<Flow>
        get() = _flows.toList()

    @JvmSynthetic
    internal fun nextId() = _id.getAndIncrement()

    @JvmSynthetic
    internal operator fun plusAssign(flow: Flow) {
        _flows += flow
    }

    operator fun get(id: Int): Flow? {
        return _flows.firstOrNull { it.id == id }
    }

}

data class Flow(val id: Int, val request: Request, val response: Response) {

    data class Request(val method: String, val url: URL, val headers: Map<String, String>, val body: Any?)

    data class Response(val status: Int, val headers: Map<String, String>, val body: Any?)

    val host: String by lazy {
        request.url.host
    }

    val path: String by lazy {
        request.url.path
    }

}