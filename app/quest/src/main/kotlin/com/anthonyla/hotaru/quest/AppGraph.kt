package com.anthonyla.hotaru.quest

import android.content.Context
import com.anthonyla.hotaru.application.ports.ClientInfo
import com.anthonyla.hotaru.application.stage.Stage
import com.anthonyla.hotaru.presentation.AppShellModel
import com.anthonyla.hotaru.presentation.ImageUrls
import com.anthonyla.hotaru.presentation.player.PlayerViewModel
import com.anthonyla.hotaru.ui.screens.ScreenViewModels
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import kotlin.time.Clock

@Singleton
@DependencyGraph(AppScope::class)
interface AppGraph : ScreenViewModels {
    val shell: AppShellModel
    val stage: Stage
    val player: PlayerViewModel
    val imageUrls: ImageUrls
    val devSignIn: DevSignIn
    val okHttp: OkHttpClient

    /** App-lifetime work on the main thread: the shell, playback and realtime outlive any screen. */
    @Provides
    @Singleton
    private fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    private fun okHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    private fun clock(): Clock = Clock.System

    @Provides
    private fun clientInfo(): ClientInfo = ClientInfo("Hotaru", BuildConfig.VERSION_NAME)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AppGraph
    }
}
