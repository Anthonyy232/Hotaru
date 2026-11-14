package com.anthonyla.hotaru.infra.jellyfin

import com.anthonyla.hotaru.application.ports.ItemQuery
import com.anthonyla.hotaru.application.ports.PlaybackInfoRequest
import com.anthonyla.hotaru.application.ports.PlaybackInfoResult
import com.anthonyla.hotaru.application.ports.ServerEvent
import com.anthonyla.hotaru.application.ports.Settings
import com.anthonyla.hotaru.domain.ItemKind
import com.anthonyla.hotaru.domain.SubtitleChoice
import com.anthonyla.hotaru.domain.Ticks
import com.anthonyla.hotaru.testing.FakeCapabilitiesProvider
import com.anthonyla.hotaru.testing.FakeDeviceIdentity
import com.anthonyla.hotaru.testing.getOrThrow
import com.anthonyla.hotaru.testing.NoopLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Contract test against any live Jellyfin 12+ server; skipped unless HOTARU_DEV_SERVER, HOTARU_USER and
 * HOTARU_PASSWORD are set. Read-only: asserts nothing about library contents and never writes user data.
 */
class LiveServerIntegrationTest {
    private val baseUrl = System.getenv("HOTARU_DEV_SERVER")
    private val user = System.getenv("HOTARU_USER")
    private val password = System.getenv("HOTARU_PASSWORD")

    @Test
    fun `sign in, browse and resolve playback against a live server`() = runTest(timeout = 2.minutes) {
        assumeTrue(baseUrl != null && user != null && password != null, "HOTARU_DEV_SERVER / HOTARU_USER / HOTARU_PASSWORD not set")
        val factory = JellyfinConnectionFactory(FakeDeviceIdentity(), NoopLogger, object : PlatformContext() {}, OkHttpClient())
        val publicServer = factory.publicServer(baseUrl!!)

        val info = publicServer.info().getOrThrow()
        assertTrue(info.version.major >= 12, "server ${info.version}")

        val session = publicServer.authenticateByName(user!!, password!!).getOrThrow()
        val conn = factory.connect(baseUrl, session)
        try {
            assertEquals(session.account.userId, conn.account.currentUser().getOrThrow().userId)

            val views = conn.library.views().getOrThrow()
            views.forEach { conn.library.items(ItemQuery(parentId = it.id, limit = 20)).getOrThrow() }
            // User-scoped endpoints without a userId: the server resolves the user from the token.
            conn.library.resume(limit = 20).getOrThrow()
            conn.library.nextUp(limit = 20).getOrThrow()

            val videos = conn.library.items(ItemQuery(kinds = setOf(ItemKind.Movie, ItemKind.Episode), limit = 20)).getOrThrow()
            videos.items.firstOrNull()?.let { video ->
                assertEquals(video.id, conn.library.item(video.id).getOrThrow().id)
                val caps = FakeCapabilitiesProvider.quest3Like()
                val pbi = conn.playback.playbackInfo(PlaybackInfoRequest(video.id, Ticks.ZERO, null, SubtitleChoice.Default, Settings().maxStreamingBitrate, caps)).getOrThrow()
                conn.playback.timeline(video.id, assertIs<PlaybackInfoResult.Playable>(pbi, "playback info: $pbi").sources.first().id).getOrThrow()
            }

            conn.realtime.postCapabilities().getOrThrow()
            // The socket runs on real time, so step out of the virtual clock.
            withContext(Dispatchers.Default) {
                withTimeout(15.seconds) { conn.realtime.events.first { it is ServerEvent.Connection && it.connected } }
            }
        } finally {
            conn.account.logout()
        }
    }
}
