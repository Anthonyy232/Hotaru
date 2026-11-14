package com.anthonyla.hotaru.infra.jellyfin

import com.anthonyla.hotaru.testing.FakeCapabilitiesProvider
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceProfileTest {
    private val caps = FakeCapabilitiesProvider.quest3Like()

    @Test
    fun `video codec conditions follow the probed capabilities`() {
        val profile = JellyfinDeviceProfile.build(caps)
        val hevc = profile.codecProfiles.single { it.type == CodecType.VIDEO && it.codec == "hevc" }
        assertEquals("183", hevc.conditions.single { it.property == ProfileConditionValue.VIDEO_LEVEL }.value)
        assertEquals("10", hevc.conditions.single { it.property == ProfileConditionValue.VIDEO_BIT_DEPTH }.value)
        assertEquals("SDR|HDR10", hevc.conditions.single { it.property == ProfileConditionValue.VIDEO_RANGE_TYPE }.value)
        val vp9 = profile.codecProfiles.single { it.type == CodecType.VIDEO && it.codec == "vp9" }
        assertTrue(vp9.conditions.none { it.property == ProfileConditionValue.VIDEO_LEVEL }, "unknown level adds no condition")
    }

    @Test
    fun `the channel limit caps both direct play and the video transcode`() {
        val stereo = JellyfinDeviceProfile.build(caps.copy(maxAudioChannels = 2))
        assertEquals("2", stereo.codecProfiles.single { it.type == CodecType.VIDEO_AUDIO }.conditions.single { it.property == ProfileConditionValue.AUDIO_CHANNELS }.value)
        assertEquals("2", stereo.transcodingProfiles.single { it.container == "mp4" }.maxAudioChannels)
    }
}
