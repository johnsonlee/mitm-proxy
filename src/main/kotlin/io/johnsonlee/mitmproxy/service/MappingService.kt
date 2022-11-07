package io.johnsonlee.mitmproxy.service

import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class MappingService {

    private val _locals = ConcurrentHashMap<RegexLocation, Resource>()

    private val _remotes = ConcurrentHashMap<RegexLocation, LiteralLocation>()

    internal val locals: Map<RegexLocation, Resource>
        get() = _locals.toMap()

    internal val remotes: Map<RegexLocation, LiteralLocation>
        get() = _remotes.toMap()

    fun mapToLocal(from: RegexLocation, local: Resource) {
        _locals[from] = local
    }

    fun mapToRemote(from: RegexLocation, remote: LiteralLocation) {
        _remotes[from] = remote
    }

}
