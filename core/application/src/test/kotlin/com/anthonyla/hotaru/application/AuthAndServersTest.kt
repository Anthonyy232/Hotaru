package com.anthonyla.hotaru.application

import com.anthonyla.hotaru.application.auth.SignOut
import com.anthonyla.hotaru.application.session.SessionEvent
import com.anthonyla.hotaru.domain.ServerVersion
import com.anthonyla.hotaru.testing.ApplicationHarness
import com.anthonyla.hotaru.testing.getOrThrow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AuthAndServersTest {

    @Test
    fun `adding rejects servers older than 12`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.fake.version = ServerVersion(10, 11, 11)
        val error = assertIs<Outcome.Failure>(h.addServer("http://fake")).error
        assertIs<AppError.ServerIncompatible>(error)
        assertEquals(ServerVersion(10, 11, 11), error.found)
    }

    @Test
    fun `adding reports an unreachable server as a network error`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        assertIs<AppError.Network>(assertIs<Outcome.Failure>(h.addServer("nowhere.local")).error)
    }

    @Test
    fun `password sign in stores the token per user and activates the session`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val server = h.signedIn()
        val active = assertNotNull(h.sessions.active.value)
        assertEquals("Quest", active.account.name)
        assertEquals(server.id, active.server.id)
        assertEquals(1, h.secure.map.size)
        assertEquals(1, h.accounts.size)
    }

    @Test
    fun `switching user keeps the token, forgetting the account revokes it`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        SignOut(h.sessions)(forgetAccount = false)
        assertEquals(1, h.fake.validTokens.size)
        h.restoreSession().getOrThrow()
        SignOut(h.sessions)(forgetAccount = true)
        assertTrue(h.fake.validTokens.isEmpty())
    }

    @Test
    fun `quick connect polls through network failures until approved`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val server = h.server()
        val code = h.quickConnect.start(server).getOrThrow()
        assertEquals("123456", code.code)
        var outcome: Outcome<*>? = null
        val job = launch { outcome = h.quickConnect.await(server, code) }
        advanceTimeBy(12.seconds)
        h.fake.failNetwork = true
        advanceTimeBy(12.seconds)
        assertNull(outcome)
        h.fake.failNetwork = false
        h.fake.quickConnectApproved = true
        advanceTimeBy(5.seconds)
        job.join()
        assertIs<Outcome.Success<*>>(outcome)
        assertNotNull(h.sessions.active.value)
    }

    @Test
    fun `restore session validates the token and survives offline`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        SignOut(h.sessions)(forgetAccount = false)
        assertNull(h.sessions.active.value)

        assertIs<Outcome.Success<*>>(h.restoreSession())
        assertNotNull(h.sessions.active.value)

        SignOut(h.sessions)(forgetAccount = false)
        h.fake.failNetwork = true
        assertIs<Outcome.Success<*>>(h.restoreSession())
        h.fake.failNetwork = false

        h.fake.validTokens.clear()
        SignOut(h.sessions)(forgetAccount = false)
        assertIs<AppError.Unauthorized>(assertIs<Outcome.Failure>(h.restoreSession()).error)
        assertIs<SessionEvent.Unauthorized>(h.sessions.events.value)
        assertTrue(h.secure.map.isEmpty())
    }

    @Test
    fun `a 401 mid-session ends it, but not a newer session that replaced it`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val server = h.signedIn()
        val stale = assertNotNull(h.sessions.active.value)
        val staleToken = h.secure.map.values.single()
        h.signIn(server, "Quest", "quest").getOrThrow()
        h.fake.validTokens.remove(staleToken)
        assertIs<AppError.Unauthorized>(assertIs<Outcome.Failure>(stale.connection.library.views()).error)
        assertNotNull(h.sessions.active.value)

        h.fake.validTokens.clear()
        assertIs<AppError.Unauthorized>(assertIs<Outcome.Failure>(h.loadHome()).error)
        assertNull(h.sessions.active.value)
        assertIs<SessionEvent.Unauthorized>(h.sessions.events.value)
    }

    @Test
    fun `forgetting an offline account clears its local session and token`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.fake.failNetwork = true
        SignOut(h.sessions)(forgetAccount = true)
        assertNull(h.sessions.active.value)
        assertTrue(h.secure.map.isEmpty())
        assertEquals(0, h.accounts.size)
    }

    @Test
    fun `forgetting an account finishes revocation after its screen is cancelled`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val signOut = launch(start = CoroutineStart.LAZY) { SignOut(h.sessions)(forgetAccount = true) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            h.sessions.events.first { it is SessionEvent.SignedOut }
            signOut.cancel()
        }

        signOut.start()
        runCurrent()

        assertTrue(signOut.isCancelled)
        assertTrue(h.fake.validTokens.isEmpty())
        assertTrue(h.secure.map.isEmpty())
        assertEquals(0, h.accounts.size)
    }
}
