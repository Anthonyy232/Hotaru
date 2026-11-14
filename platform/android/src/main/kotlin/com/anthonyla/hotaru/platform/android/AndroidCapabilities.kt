package com.anthonyla.hotaru.platform.android

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat
import com.anthonyla.hotaru.application.ports.DeviceCapabilitiesProvider
import com.anthonyla.hotaru.application.ports.Logger
import com.anthonyla.hotaru.domain.DeviceCapabilities
import com.anthonyla.hotaru.domain.HdrFormat
import com.anthonyla.hotaru.domain.VideoCodecCapability
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TreeMap

/**
 * Software video decoders are ignored so the server never direct-plays something the XR2 cannot decode in hardware.
 */
@Singleton
@ContributesBinding(AppScope::class)
class AndroidCapabilitiesProvider(private val logger: Logger) : DeviceCapabilitiesProvider {

    @Volatile private var cached: DeviceCapabilities? = null

    override suspend fun capabilities(): DeviceCapabilities = cached ?: withContext(Dispatchers.Default) { probe().also { cached = it } }

    private fun probe(): DeviceCapabilities {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        val video = listOf(
            MediaFormat.MIMETYPE_VIDEO_AVC to "h264",
            MediaFormat.MIMETYPE_VIDEO_HEVC to "hevc",
            MediaFormat.MIMETYPE_VIDEO_AV1 to "av1",
            MediaFormat.MIMETYPE_VIDEO_VP9 to "vp9",
            MediaFormat.MIMETYPE_VIDEO_MPEG2 to "mpeg2video",
        ).mapNotNull { (mime, codec) -> videoCapability(list, mime, codec) }
        val audio = listOf(
            MediaFormat.MIMETYPE_AUDIO_OPUS to "opus", MediaFormat.MIMETYPE_AUDIO_VORBIS to "vorbis", MediaFormat.MIMETYPE_AUDIO_AC4 to "ac4",
        ).filter { (mime, _) -> list.any { it.supports(mime) } }.map { it.second }.toSet() +
            // The bundled Jellyfin FFmpeg build decodes these to PCM in software (cheap enough for audio); Media3 plays raw PCM itself.
            setOf("aac", "ac3", "eac3", "dts", "truehd", "alac", "flac", "mp3", "pcm_s16le", "pcm_s24le")

        val caps = DeviceCapabilities(
            videoCodecs = video,
            audioCodecs = audio,
            containers = setOf("mp4", "m4v", "mkv", "webm", "mov", "ts", "m2ts", "ogv", "3gp"),
            // FFmpeg decodes up to 7.1 and Android downmixes it to the headset's stereo.
            maxAudioChannels = 8,
        )
        logger.info(TAG) { "probed: video=${video.map { "${it.codec}@${it.maxWidth}x${it.maxHeight}/${it.maxBitDepth}b L${it.maxLevel} hdr=${it.hdr}" }} audio=$audio" }
        return caps
    }

    private fun videoCapability(list: List<MediaCodecInfo>, mime: String, codec: String): VideoCodecCapability? {
        val infos = list.filter { it.isHardwareAccelerated && it.supports(mime) }
        if (infos.isEmpty()) return null
        var maxW = 0; var maxH = 0; var maxLevel = 0; var bitDepth = 8
        val profiles = mutableSetOf<String>(); val hdr = mutableSetOf<HdrFormat>()
        for (info in infos) {
            val c = info.getCapabilitiesForType(mime)
            maxW = maxOf(maxW, c.videoCapabilities!!.supportedWidths.upper)
            maxH = maxOf(maxH, c.videoCapabilities!!.supportedHeights.upper)
            for (pl in c.profileLevels) {
                maxLevel = maxOf(maxLevel, pl.level)
                when (mime) {
                    MediaFormat.MIMETYPE_VIDEO_AVC -> when (pl.profile) {
                        CodecProfileLevel.AVCProfileBaseline -> profiles += "baseline"
                        CodecProfileLevel.AVCProfileConstrainedBaseline -> profiles += "constrained baseline"
                        CodecProfileLevel.AVCProfileMain -> profiles += "main"
                        CodecProfileLevel.AVCProfileHigh -> profiles += "high"
                        CodecProfileLevel.AVCProfileHigh10 -> { profiles += "high 10"; bitDepth = 10 }
                    }
                    MediaFormat.MIMETYPE_VIDEO_HEVC -> when (pl.profile) {
                        CodecProfileLevel.HEVCProfileMain -> profiles += "main"
                        CodecProfileLevel.HEVCProfileMain10 -> { profiles += "main 10"; bitDepth = 10 }
                        CodecProfileLevel.HEVCProfileMain10HDR10 -> { profiles += "main 10"; bitDepth = 10; hdr += HdrFormat.HDR10 }
                        CodecProfileLevel.HEVCProfileMain10HDR10Plus -> { profiles += "main 10"; bitDepth = 10; hdr += HdrFormat.HDR10; hdr += HdrFormat.HDR10Plus }
                    }
                    MediaFormat.MIMETYPE_VIDEO_AV1 -> when (pl.profile) {
                        CodecProfileLevel.AV1ProfileMain8 -> profiles += "main"
                        CodecProfileLevel.AV1ProfileMain10 -> { profiles += "main"; bitDepth = 10 }
                        CodecProfileLevel.AV1ProfileMain10HDR10 -> { profiles += "main"; bitDepth = 10; hdr += HdrFormat.HDR10 }
                        CodecProfileLevel.AV1ProfileMain10HDR10Plus -> { profiles += "main"; bitDepth = 10; hdr += HdrFormat.HDR10; hdr += HdrFormat.HDR10Plus }
                    }
                    MediaFormat.MIMETYPE_VIDEO_VP9 -> when (pl.profile) {
                        CodecProfileLevel.VP9Profile0 -> profiles += "profile 0"
                        CodecProfileLevel.VP9Profile2 -> { profiles += "profile 2"; bitDepth = 10 }
                        CodecProfileLevel.VP9Profile2HDR -> { profiles += "profile 2"; bitDepth = 10; hdr += HdrFormat.HDR10 }
                    }
                }
            }
        }
        // Dolby Vision is advertised via its own MIME; only claim it when a hardware decoder exists.
        if (mime == MediaFormat.MIMETYPE_VIDEO_HEVC && list.any { it.isHardwareAccelerated && it.supports(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) }) hdr += HdrFormat.DolbyVision
        return VideoCodecCapability(codec, profiles, jellyfinLevels[mime]?.floorEntry(maxLevel)?.value, maxW, maxH, bitDepth, hdr)
    }

    private fun MediaCodecInfo.supports(mime: String) = supportedTypes.any { it.equals(mime, ignoreCase = true) }

    /**
     * MediaCodec level flags to the integers Jellyfin compares `VideoLevel` against: AVC uses
     * level x10 (5.1 = 51), HEVC uses level x30 (5.1 = 153), AV1 uses seq_level_idx (5.1 = 13).
     */
    private val jellyfinLevels = mapOf(
        MediaFormat.MIMETYPE_VIDEO_AVC to TreeMap(mapOf(
            CodecProfileLevel.AVCLevel3 to 30, CodecProfileLevel.AVCLevel31 to 31, CodecProfileLevel.AVCLevel32 to 32,
            CodecProfileLevel.AVCLevel4 to 40, CodecProfileLevel.AVCLevel41 to 41, CodecProfileLevel.AVCLevel42 to 42,
            CodecProfileLevel.AVCLevel5 to 50, CodecProfileLevel.AVCLevel51 to 51, CodecProfileLevel.AVCLevel52 to 52,
            CodecProfileLevel.AVCLevel6 to 60, CodecProfileLevel.AVCLevel61 to 61, CodecProfileLevel.AVCLevel62 to 62,
        )),
        MediaFormat.MIMETYPE_VIDEO_HEVC to TreeMap(mapOf(
            CodecProfileLevel.HEVCMainTierLevel4 to 120, CodecProfileLevel.HEVCMainTierLevel41 to 123,
            CodecProfileLevel.HEVCMainTierLevel5 to 150, CodecProfileLevel.HEVCMainTierLevel51 to 153, CodecProfileLevel.HEVCMainTierLevel52 to 156,
            CodecProfileLevel.HEVCMainTierLevel6 to 180, CodecProfileLevel.HEVCMainTierLevel61 to 183, CodecProfileLevel.HEVCMainTierLevel62 to 186,
        )),
        MediaFormat.MIMETYPE_VIDEO_AV1 to TreeMap(mapOf(
            CodecProfileLevel.AV1Level4 to 8, CodecProfileLevel.AV1Level41 to 9, CodecProfileLevel.AV1Level5 to 12, CodecProfileLevel.AV1Level51 to 13,
            CodecProfileLevel.AV1Level52 to 14, CodecProfileLevel.AV1Level6 to 16, CodecProfileLevel.AV1Level61 to 17, CodecProfileLevel.AV1Level62 to 18,
        )),
    )

    companion object {
        private const val TAG = "Capabilities"
    }
}
