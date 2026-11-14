package com.anthonyla.hotaru.presentation.settings

import com.anthonyla.hotaru.application.auth.SignOut
import com.anthonyla.hotaru.application.ports.AudioMode
import com.anthonyla.hotaru.application.ports.ClientInfo
import com.anthonyla.hotaru.application.ports.SegmentAction
import com.anthonyla.hotaru.application.ports.Settings
import com.anthonyla.hotaru.application.ports.SettingsStore
import com.anthonyla.hotaru.application.ports.SubtitleSize
import com.anthonyla.hotaru.application.session.SessionManager
import com.anthonyla.hotaru.domain.Account
import com.anthonyla.hotaru.domain.Server
import com.anthonyla.hotaru.presentation.ViewModel
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

data class SettingsState(
    val settings: Settings = Settings(),
    val server: Server? = null,
    val account: Account? = null,
    val appVersion: String = "",
) {
    val accountLine: String get() = "${account?.name ?: "—"} on ${server?.name ?: "—"}"
    val serverAddress: String get() = server?.baseUrl.orEmpty()
}

object SettingsOptions {
    val maxStreamingBitrate = listOf(20_000_000 to "20 Mbps", 40_000_000 to "40 Mbps", 80_000_000 to "80 Mbps", 120_000_000 to "120 Mbps", 200_000_000 to "Original")
    val audioMode = listOf(AudioMode.DownmixStereo to "Downmix on device", AudioMode.ServerStereoAac to "Server stereo AAC")
    val subtitleSize = listOf(SubtitleSize.Small to "Small", SubtitleSize.Medium to "Medium", SubtitleSize.Large to "Large", SubtitleSize.ExtraLarge to "Extra large")
    val segmentAction = listOf(SegmentAction.Skip to "Skip automatically", SegmentAction.Prompt to "Ask", SegmentAction.Ignore to "Never")
}

sealed interface SettingsIntent {
    data class Update(val transform: (Settings) -> Settings) : SettingsIntent
    data class SignOut(val forget: Boolean) : SettingsIntent
}

@AssistedInject
class SettingsViewModel(
    @Assisted scope: CoroutineScope,
    private val store: SettingsStore,
    sessions: SessionManager,
    private val signOut: SignOut,
    client: ClientInfo,
) : ViewModel<SettingsState, SettingsIntent>(SettingsState(appVersion = client.version), scope) {

    @AssistedFactory
    fun interface Factory {
        fun create(scope: CoroutineScope): SettingsViewModel
    }

    private var signOutJob: Job? = null

    init {
        launch { store.settings.collect { s -> update { it.copy(settings = s) } } }
        launch { sessions.active.collect { a -> update { it.copy(server = a?.server, account = a?.account) } } }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.Update -> launch { store.update(intent.transform) }
            is SettingsIntent.SignOut -> signOutJob = signOutJob?.takeIf { it.isActive } ?: launch { signOut(intent.forget) }
        }
    }
}
