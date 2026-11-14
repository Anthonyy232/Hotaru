package com.anthonyla.hotaru.domain

import kotlin.time.Instant

data class ServerVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ServerVersion> {
    override fun compareTo(other: ServerVersion): Int =
        compareValuesBy(this, other, ServerVersion::major, ServerVersion::minor, ServerVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /** Jellyfin 12.0 disabled legacy auth and is what SDK 1.9 targets. */
        val MINIMUM_SUPPORTED: ServerVersion = ServerVersion(12, 0, 0)

        fun parse(text: String): ServerVersion? {
            val parts = text.trim().split('.').map { it.takeWhile(Char::isDigit) }
            val nums = parts.mapNotNull { it.toIntOrNull() }
            if (nums.isEmpty()) return null
            return ServerVersion(nums[0], nums.getOrElse(1) { 0 }, nums.getOrElse(2) { 0 })
        }
    }
}

data class Server(
    val id: ServerId,
    val name: String,
    val baseUrl: String,
    val version: ServerVersion,
    val lastSeen: Instant,
)

/** What `/System/Info/Public` tells an unauthenticated client. */
data class ServerInfo(
    val id: ServerId,
    val name: String,
    val version: ServerVersion,
)

data class Account(
    val serverId: ServerId,
    val userId: UserId,
    val name: String,
    val deviceId: DeviceId,
)

data class Session(
    val account: Account,
    val accessToken: String,
)
