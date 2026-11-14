package com.anthonyla.hotaru.application

import com.anthonyla.hotaru.testing.ApplicationHarness
import com.anthonyla.hotaru.testing.Fixtures
import com.anthonyla.hotaru.testing.getOrThrow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class LibraryTest {
    @Test
    fun `item detail caches only its own fetch, for 10 minutes`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val aurora = Fixtures.auroraDrift.id
        assertTrue(h.loadHome().getOrThrow().rows.any { row -> row.items.any { it.id == aurora } })
        h.fake.failNetwork = true
        assertIs<Outcome.Failure>(h.itemDetail(aurora))
        h.fake.failNetwork = false
        val first = h.itemDetail(aurora).getOrThrow()
        h.fake.failNetwork = true
        assertEquals(first, h.itemDetail(aurora).getOrThrow())
        h.clock.advance(11.minutes)
        assertIs<Outcome.Failure>(h.itemDetail(aurora))
    }

    @Test
    fun `a cached item never reaches another user of the server`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val server = h.signedIn()
        h.itemDetail(Fixtures.auroraDrift.id).getOrThrow()
        h.signIn(server, "Guest", "guest").getOrThrow()
        h.fake.failNetwork = true
        assertIs<Outcome.Failure>(h.itemDetail(Fixtures.auroraDrift.id))
    }
}
