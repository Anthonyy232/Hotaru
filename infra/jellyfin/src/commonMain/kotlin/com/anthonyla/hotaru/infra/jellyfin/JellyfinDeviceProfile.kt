package com.anthonyla.hotaru.infra.jellyfin

import com.anthonyla.hotaru.domain.DeviceCapabilities
import com.anthonyla.hotaru.domain.HdrFormat
import com.anthonyla.hotaru.domain.TextSubtitleFormat
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.model.deviceprofile.buildDeviceProfile

internal object JellyfinDeviceProfile {

    fun build(caps: DeviceCapabilities): DeviceProfile = buildDeviceProfile {
        name = "Hotaru"
        musicStreamingTranscodingBitrate = 1_280_000

        // Quest 3/3S always probe hardware h264 + hevc and AAC, so these lists are never empty.
        val videoCodecs = caps.videoCodecs.map { it.codec }
        val transcodeAudio = listOf("aac", "mp3", "ac3", "eac3", "opus", "flac").filter { it in caps.audioCodecs }
        val transcodeVideo = listOf("hevc", "h264").filter { it in videoCodecs }

        directPlayProfile {
            type = DlnaProfileType.VIDEO
            container(*caps.containers.toTypedArray())
            videoCodec(*videoCodecs.toTypedArray())
            audioCodec(*caps.audioCodecs.toTypedArray())
        }
        directPlayProfile {
            type = DlnaProfileType.AUDIO
            container("mp3", "flac", "ogg", "oga", "m4a", "m4b", "aac", "wav", "opus", "webm")
            audioCodec(*caps.audioCodecs.toTypedArray())
        }

        // fMP4 HLS only: Media3 plays it natively, and the server takes the first matching video profile anyway.
        transcodingProfile {
            type = DlnaProfileType.VIDEO
            protocol = MediaStreamProtocol.HLS
            container = "mp4"
            videoCodec(*transcodeVideo.toTypedArray())
            audioCodec(*transcodeAudio.toTypedArray())
            enableSubtitlesInManifest = true
            maxAudioChannels = caps.maxAudioChannels.toString()
            minSegments = 2
            segmentLength = 6
        }
        transcodingProfile {
            type = DlnaProfileType.AUDIO
            protocol = MediaStreamProtocol.HTTP
            container = "mp3"
            audioCodec("mp3")
            maxAudioChannels = "2"
        }

        for (codec in caps.videoCodecs) {
            codecProfile {
                type = CodecType.VIDEO
                this.codec = codec.codec
                conditions {
                    lowerThanOrEquals(ProfileConditionValue.WIDTH, codec.maxWidth)
                    lowerThanOrEquals(ProfileConditionValue.HEIGHT, codec.maxHeight)
                    lowerThanOrEquals(ProfileConditionValue.VIDEO_BIT_DEPTH, codec.maxBitDepth)
                    if (codec.profiles.isNotEmpty()) inCollection(ProfileConditionValue.VIDEO_PROFILE, codec.profiles.toList())
                    codec.maxLevel?.let { lowerThanOrEquals(ProfileConditionValue.VIDEO_LEVEL, it) }
                    val ranges = buildList {
                        add(VideoRangeType.SDR)
                        if (HdrFormat.HDR10 in codec.hdr) add(VideoRangeType.HDR10)
                        if (HdrFormat.HDR10Plus in codec.hdr) add(VideoRangeType.HDR10_PLUS)
                        if (HdrFormat.DolbyVision in codec.hdr) addAll(listOf(VideoRangeType.DOVI, VideoRangeType.DOVI_WITH_HDR10, VideoRangeType.DOVI_WITH_SDR))
                    }
                    inCollection(ProfileConditionValue.VIDEO_RANGE_TYPE, ranges.map { it.serialName })
                }
            }
        }
        codecProfile {
            type = CodecType.VIDEO_AUDIO
            conditions { lowerThanOrEquals(ProfileConditionValue.AUDIO_CHANNELS, caps.maxAudioChannels) }
        }

        for (fmt in TextSubtitleFormat.entries.flatMap { it.aliases.toList() }) {
            subtitleProfile(fmt, SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile(fmt, SubtitleDeliveryMethod.EMBED)
        }
        subtitleProfile("vtt", SubtitleDeliveryMethod.HLS)
        subtitleProfile("pgssub", SubtitleDeliveryMethod.EMBED)
        subtitleProfile("pgssub", SubtitleDeliveryMethod.ENCODE)
        subtitleProfile("dvdsub", SubtitleDeliveryMethod.EMBED)
        subtitleProfile("dvdsub", SubtitleDeliveryMethod.ENCODE)
        subtitleProfile("dvbsub", SubtitleDeliveryMethod.ENCODE)
    }
}
