package com.anthonyla.hotaru.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DeviceIdTest {
    @Test
    fun `per user ids are stable and distinct`() {
        val base = DeviceId("quest-abc")
        val a = base.forUser(UserId("u1"))
        assertEquals(a, base.forUser(UserId("u1")))
        assertNotEquals(a, base.forUser(UserId("u2")))
        assertNotEquals(a, base)
    }
}

class ServerVersionTest {
    @Test
    fun `parses and compares`() {
        assertEquals(ServerVersion(12, 2, 0), ServerVersion.parse("12.2"))
        assertEquals(ServerVersion(10, 11, 11), ServerVersion.parse("10.11.11"))
        assertTrue(ServerVersion.parse("12.0.0")!! >= ServerVersion.MINIMUM_SUPPORTED)
        assertTrue(ServerVersion.parse("10.11.11")!! < ServerVersion.MINIMUM_SUPPORTED)
        assertNull(ServerVersion.parse("nope"))
    }
}

class DisplayTitlesTest {
    private val ep = Episode(
        id = ItemId("e1"), name = "Pilot", seriesName = "Signal Lost", indexNumber = 2, parentIndexNumber = 1,
    )

    @Test
    fun `episode code and title`() {
        assertEquals("S01E02", DisplayTitles.episodeCode(ep))
        assertEquals("S01E02 · Pilot", DisplayTitles.episodeTitle(ep))
        assertEquals("S01E02-03", DisplayTitles.episodeCode(ep.copy(indexNumberEnd = 3)))
        assertEquals("Signal Lost · S01E02", DisplayTitles.episodeContext(ep))
    }

    @Test
    fun `runtime and clock`() {
        assertEquals("1h 42m", DisplayTitles.runtime((102).minutes.ticks))
        assertEquals("48m", DisplayTitles.runtime(48.minutes.ticks))
        assertEquals("1m", DisplayTitles.runtime(30.seconds.ticks))
        assertEquals("1:02:03", DisplayTitles.clock((3723).seconds.ticks))
        assertEquals("42:10", DisplayTitles.clock((2530).seconds.ticks))
    }
}

class StereoRulesTest {
    @Test
    fun `mvc is mono and full-frame formats halve the per-eye aspect`() {
        assertEquals(StereoLayout.Mono, StereoRules.layoutFor(Video3DFormat.MVC))
        assertEquals(16f / 9f, StereoRules.perEyeAspect(Video3DFormat.HalfSideBySide, 1920, 1080))
        assertEquals(16f / 9f, StereoRules.perEyeAspect(Video3DFormat.FullSideBySide, 3840, 1080))
        assertEquals(16f / 9f, StereoRules.perEyeAspect(Video3DFormat.FullTopAndBottom, 1920, 2160))
        assertEquals(16f / 9f, StereoRules.perEyeAspect(null, null, null))
    }

    @Test
    fun `infers projection from tags and shape`() {
        assertEquals(Projection.Equirect360, StereoRules.projectionFor("Trip_360_SBS.mp4"))
        assertEquals(Projection.Equirect180, StereoRules.projectionFor("Concert VR180.mkv"))
        assertEquals(Projection.Rectilinear, StereoRules.projectionFor("Aurora Drift (2024).mp4"))
        assertEquals(Projection.Rectilinear, StereoRules.projectionFor("Room 1800.mkv"))
        assertEquals(Projection.Rectilinear, StereoRules.projectionFor("clip_x360p.mp4"))
    }
}

class TrickplayTest {
    @Test
    fun `a position picks its thumbnail's sheet and cell, and the end of the video picks the last one`() {
        val sheets = listOf(OpaqueUrl("sheet0"), OpaqueUrl("sheet1"))
        val trickplay = Trickplay(sheets, emptyMap(), columns = 10, perSheet = 100, count = 130, interval = 10.seconds.ticks, thumbnailWidth = 320, thumbnailHeight = 180)

        assertEquals(TrickplayThumbnail(sheets[0], emptyMap(), x = 3 * 320, y = 2 * 180, width = 320, height = 180), trickplay.thumbnailAt(235.seconds.ticks))
        assertEquals(TrickplayThumbnail(sheets[1], emptyMap(), x = 9 * 320, y = 2 * 180, width = 320, height = 180), trickplay.thumbnailAt(30.minutes.ticks))
    }
}

class ResumePolicyTest {
    private fun movieAt(position: Ticks) = Movie(ItemId("m"), "Movie", runtime = 100.minutes.ticks, userData = UserData(resumePosition = position))

    @Test
    fun `ignores tiny and near-end positions`() {
        assertNull(ResumePolicy.resumePoint(movieAt(10.seconds.ticks)))
        assertNull(ResumePolicy.resumePoint(movieAt(99.minutes.ticks)))
        assertEquals(40.minutes.ticks, ResumePolicy.resumePoint(movieAt(40.minutes.ticks)))
    }
}
