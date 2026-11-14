package com.anthonyla.hotaru.application.servers

import com.anthonyla.hotaru.application.AppError
import com.anthonyla.hotaru.application.failure
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.ServerConnectionFactory
import com.anthonyla.hotaru.application.ports.ServerStore
import com.anthonyla.hotaru.application.success
import com.anthonyla.hotaru.domain.Server
import com.anthonyla.hotaru.domain.ServerVersion
import jakarta.inject.Inject
import kotlin.time.Clock

/** Tries candidate URLs in order; the first that answers wins, must be compatible, and is stored. */
class AddServer @Inject constructor(private val connections: ServerConnectionFactory, private val servers: ServerStore, private val clock: Clock) {
    suspend operator fun invoke(input: String): Outcome<Server> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return AppError.InvalidInput("Enter a server address").failure()
        val candidates = connections.addressCandidates(trimmed)
        if (candidates.isEmpty()) return AppError.InvalidInput("Could not understand that address").failure()
        lateinit var lastError: AppError
        for (candidate in candidates) {
            when (val result = connections.publicServer(candidate).info()) {
                is Outcome.Success -> {
                    val info = result.value
                    if (info.version < ServerVersion.MINIMUM_SUPPORTED) return AppError.ServerIncompatible(info.version, ServerVersion.MINIMUM_SUPPORTED).failure()
                    val server = Server(id = info.id, name = info.name, baseUrl = candidate.trimEnd('/'), version = info.version, lastSeen = clock.now())
                    servers.upsert(server)
                    return server.success()
                }
                is Outcome.Failure -> lastError = result.error
            }
        }
        return lastError.failure()
    }
}
