package com.anthonyla.hotaru.domain

data class VideoCodecCapability(
    val codec: String,
    val profiles: Set<String>,
    /** Highest level in Jellyfin's integer notation (AVC 5.1 = 51, HEVC 5.1 = 153, AV1 seq_level_idx). Null = unknown. */
    val maxLevel: Int?,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitDepth: Int,
    val hdr: Set<HdrFormat>,
)

data class DeviceCapabilities(
    val videoCodecs: List<VideoCodecCapability>,
    val audioCodecs: Set<String>,
    val containers: Set<String>,
    val maxAudioChannels: Int,
) {
    fun withoutHdr(): DeviceCapabilities = copy(videoCodecs = videoCodecs.map { it.copy(hdr = emptySet()) })
}
