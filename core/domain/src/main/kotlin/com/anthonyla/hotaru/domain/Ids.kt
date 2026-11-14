package com.anthonyla.hotaru.domain

import kotlinx.serialization.Serializable
import java.util.UUID

@JvmInline
value class ServerId(val value: String) {
    init { require(value.isNotBlank()) { "ServerId must not be blank" } }
    override fun toString(): String = value
}

@JvmInline
value class UserId(val value: String) {
    init { require(value.isNotBlank()) { "UserId must not be blank" } }
    override fun toString(): String = value
}

@JvmInline
@Serializable
value class ItemId(val value: String) {
    init { require(value.isNotBlank()) { "ItemId must not be blank" } }
    override fun toString(): String = value
}

@JvmInline
value class MediaSourceId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class PlaySessionId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class DeviceId(val value: String) {
    init { require(value.isNotBlank()) { "DeviceId must not be blank" } }
    override fun toString(): String = value

    /** Jellyfin keeps one token per DeviceId, so each user needs their own to stay signed in side by side. */
    fun forUser(userId: UserId): DeviceId = DeviceId(UUID.nameUUIDFromBytes("$value:${userId.value}".encodeToByteArray()).toString())
}

/** A URL the server handed us that we must treat as opaque (e.g. TranscodingUrl). */
@JvmInline
value class OpaqueUrl(val value: String) {
    override fun toString(): String = value
}
