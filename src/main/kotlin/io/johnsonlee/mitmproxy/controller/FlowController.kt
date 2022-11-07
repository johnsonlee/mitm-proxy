package io.johnsonlee.mitmproxy.controller

import io.johnsonlee.mitmproxy.service.Flow
import io.johnsonlee.mitmproxy.service.FlowService
import org.springframework.beans.factory.annotation.Autowired
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
    ): Map<Int, String> {
        val filters = listOfNotNull<(Flow) -> Boolean>(method?.let {
            { r -> Regex(it, RegexOption.IGNORE_CASE).matches(r.request.method) }
        }, host?.let {
            { r -> Regex(it, RegexOption.IGNORE_CASE).matches(r.host) }
        }, path?.let {
            { r -> Regex(it, RegexOption.IGNORE_CASE).matches(r.path) }
        })

        return filters.fold(flowService.flows) { records, filter ->
            records.filter(filter)
        }.associate {
            it.id to "${it.request.method} ${it.request.url}"
        }
    }

    @GetMapping("/{id}")
    operator fun get(@PathVariable id: Int): Flow? {
        return flowService[id]
    }
}