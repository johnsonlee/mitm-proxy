package io.johnsonlee.mitmproxy.controller

import io.johnsonlee.mitmproxy.service.Flow
import io.johnsonlee.mitmproxy.service.FlowService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders.CONTENT_LENGTH
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/flow")
class FlowController(@Autowired private val flowService: FlowService) {

    @GetMapping("")
    fun index(
            @RequestParam("method", required = false) method: String?,
            @RequestParam("host", required = false) host: String?,
            @RequestParam("path", required = false) path: String?,
    ): List<SimpleFlow> {
        val filters = listOfNotNull<(Flow) -> Boolean>(method?.let {
            { r -> Regex(it, RegexOption.IGNORE_CASE).matches(r.request.method) }
        }, host?.let {
            { r -> Regex(it, RegexOption.IGNORE_CASE).matches(r.host) }
        }, path?.let {
            { r -> Regex(it, RegexOption.IGNORE_CASE).matches(r.path) }
        })

        return filters.fold(flowService.flows) { records, filter ->
            records.filter(filter)
        }.map {
            SimpleFlow(
                    id = it.id,
                    protocol = it.protocol,
                    host = it.host,
                    path = it.path,
                    status = it.response.status,
                    size = it.response.headers[CONTENT_LENGTH]?.toLongOrNull() ?: 0,
                    duration = 0L
            )
        }
    }

    @GetMapping("/{id}")
    operator fun get(@PathVariable id: Long): Flow? {
        return flowService[id]
    }
}

data class SimpleFlow(
        val id: Long,
        val protocol: String,
        val host: String,
        val path: String,
        val status: Int,
        val size: Long,
        val duration: Long,
)

data class SimpleRequest(
        val method: String,
        val url: String,
        val headers: Map<String, String>,
        val body: Any?,
)

data class SimpleResponse(
        val status: Int,
        val headers: Map<String, String>,
        val body: Any?,
)