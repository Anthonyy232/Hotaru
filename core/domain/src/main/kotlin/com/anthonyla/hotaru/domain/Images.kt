package com.anthonyla.hotaru.domain

import kotlinx.serialization.Serializable

enum class ImageType { Primary, Backdrop, Thumb }

/** The tag is part of the cache key: a new tag means new bytes. */
@Serializable
data class ImageRef(
    val itemId: ItemId,
    val type: ImageType,
    val tag: String,
    val index: Int = 0,
)

@Serializable
data class ImageSet(
    val primary: ImageRef? = null,
    val backdrops: List<ImageRef> = emptyList(),
    val thumb: ImageRef? = null,
    /** Images inherited from a parent (e.g. series backdrop for an episode). */
    val parentBackdrops: List<ImageRef> = emptyList(),
    val parentThumb: ImageRef? = null,
) {
    val backdrop: ImageRef? get() = backdrops.firstOrNull() ?: parentBackdrops.firstOrNull()
    val anyThumb: ImageRef? get() = thumb ?: parentThumb
}

/** Pixels; the server never upscales past the source. */
data class ImageSpec(val fillWidth: Int?, val fillHeight: Int?)
