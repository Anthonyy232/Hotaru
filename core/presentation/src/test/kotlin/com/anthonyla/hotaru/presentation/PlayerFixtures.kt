package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.presentation.player.PlayerViewModel
import com.anthonyla.hotaru.testing.ApplicationHarness
import kotlinx.coroutines.test.TestScope

internal fun TestScope.playerViewModel(h: ApplicationHarness) = PlayerViewModel(backgroundScope, h.playback, h.stage, h.settings)

internal fun TestScope.shell(h: ApplicationHarness, player: PlayerViewModel = playerViewModel(h)) =
    AppShellModel(backgroundScope, h.sessions, h.restoreSession, h.loadViews, h.playback, h.realtime, h.stage, player)
