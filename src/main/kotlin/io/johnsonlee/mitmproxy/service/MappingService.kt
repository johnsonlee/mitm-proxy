package io.johnsonlee.mitmproxy.service

import io.netty.handler.codec.http.HttpRequest
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

@Service
class MappingService {

    private val _locals = ConcurrentHashMap<MapFrom, Resource>()

    private val _remotes = ConcurrentHashMap<MapFrom, URL>()

    internal val locals: Map<MapFrom, Resource>
        get() = _locals.toMap()

    internal val remotes: Map<MapFrom, URL>
        get() = _remotes.toMap()

    fun mapToLocal(from: MapFrom, local: Resource) {
        _locals[from] = local
    }

    fun mapToRemote(from: MapFrom, remote: URL) {
        _remotes[from] = remote
    }

}

typealias MapFrom = (HttpRequest) -> Boolean
