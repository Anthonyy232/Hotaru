package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.application.auth.SignOut
import com.anthonyla.hotaru.presentation.connect.SignInIntent
import com.anthonyla.hotaru.presentation.connect.SignInViewModel
import com.anthonyla.hotaru.testing.ApplicationHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    @Test
    fun `sign in view model starts quick connect and signs in on approval`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val server = h.server()
        val vm = SignInViewModel(backgroundScope, server, h.connections, h.quickConnect, h.signIn, h.restoreSession)
        runCurrent()
        val s = vm.state.value
        assertEquals("123456", s.quickConnectCode?.code)
        assertEquals(2, s.users.size)
        h.fake.quickConnectApproved = true
        advanceTimeBy(5.seconds); runCurrent()
        assertEquals("Quest", h.sessions.active.value?.account?.name)
    }

    @Test
    fun `a saved user resumes with one tap, another goes to the password until tapped again`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val server = h.signedIn()
        SignOut(h.sessions)(forgetAccount = false)
        val vm = SignInViewModel(backgroundScope, server, h.connections, h.quickConnect, h.signIn, h.restoreSession)
        runCurrent()
        val (quest, guest) = vm.state.value.users

        vm.onIntent(SignInIntent.SelectUser(guest))
        runCurrent()
        assertNull(h.sessions.active.value)
        assertEquals(guest, vm.state.value.selectedUser)
        assertNull(vm.state.value.shownQuickConnectCode)
        vm.onIntent(SignInIntent.SelectUser(guest))
        assertNull(vm.state.value.selectedUser)

        vm.onIntent(SignInIntent.SelectUser(quest))
        runCurrent()
        assertEquals("Quest", h.sessions.active.value?.account?.name)
    }

    @Test
    fun `a quick connect code that can't be fetched shows why and can be asked for again`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val server = h.server()
        val vm = SignInViewModel(backgroundScope, server, h.connections, h.quickConnect, h.signIn, h.restoreSession)
        runCurrent()
        h.fake.failNetwork = true
        vm.onIntent(SignInIntent.UseQuickConnect)
        runCurrent()
        assertEquals("Can't reach the server: fake offline", vm.state.value.error)
        assertTrue(vm.state.value.canUseQuickConnect)

        h.fake.failNetwork = false
        vm.onIntent(SignInIntent.UseQuickConnect)
        runCurrent()
        assertNull(vm.state.value.error)
        assertEquals("123456", vm.state.value.shownQuickConnectCode?.code)
    }

    @Test
    fun `password path replaces quick connect`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val server = h.server()
        val vm = SignInViewModel(backgroundScope, server, h.connections, h.quickConnect, h.signIn, h.restoreSession)
        runCurrent()
        vm.onIntent(SignInIntent.SelectUser(vm.state.value.users.first()))
        assertNull(vm.state.value.shownQuickConnectCode)
        assertTrue(vm.state.value.canUseQuickConnect)
        vm.onIntent(SignInIntent.UseQuickConnect)
        runCurrent()
        assertEquals("123456", vm.state.value.shownQuickConnectCode?.code)
        vm.onIntent(SignInIntent.UsePassword)
        vm.onIntent(SignInIntent.PasswordChanged("wrong"))
        vm.onIntent(SignInIntent.Submit)
        runCurrent()
        assertEquals("Sign-in failed. Check the name and password.", vm.state.value.error)
        vm.onIntent(SignInIntent.PasswordChanged("quest"))
        vm.onIntent(SignInIntent.Submit)
        runCurrent()
        assertEquals("Quest", h.sessions.active.value?.account?.name)
    }
}
