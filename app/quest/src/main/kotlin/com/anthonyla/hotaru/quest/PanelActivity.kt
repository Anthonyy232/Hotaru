package com.anthonyla.hotaru.quest

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.anthonyla.hotaru.application.ports.VideoRenderTarget
import com.anthonyla.hotaru.application.stage.Stage
import com.anthonyla.hotaru.domain.StageMode
import com.anthonyla.hotaru.platform.quest.DevBridge
import com.anthonyla.hotaru.platform.quest.isDebuggable
import com.anthonyla.hotaru.ui.kit.HotaruTheme
import com.anthonyla.hotaru.ui.screens.WindowContent
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PanelActivity : ComponentActivity() {
    private val graph get() = (application as HotaruApp).graph
    private val devBridge = DevBridge(this, "window", { window.decorView }, mapOf("back" to { graph.shell.back() }))
    /** Finishing to hand over to immersive is not a close; its onDestroy can come after the mode has moved on. */
    private var handedOver = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isDebuggable) signInFromExtras()
        devBridge.register()
        graph.shell.windowOpened()
        onBackPressedDispatcher.addCallback(this) { graph.shell.back() }
        lifecycleScope.launch {
            // windowOpened brings immersive playback here first; after that, playback going immersive takes the viewer there.
            graph.stage.state.map { it.mode }.dropWhile { it == StageMode.Immersive }.first { it == StageMode.Immersive }
            handedOver = true
            startActivity(Intent(this@PanelActivity, ImmersiveActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finishAndRemoveTask()
        }
        setContent {
            HotaruTheme { WindowContent(graph.shell, graph, graph.player) { VideoSurface(graph.stage, it) } }
        }
    }

    override fun onDestroy() {
        devBridge.unregister()
        if (isFinishing && !handedOver) graph.shell.windowClosed()
        super.onDestroy()
    }

    private fun signInFromExtras() {
        val server = intent.getStringExtra("server")
        val user = intent.getStringExtra("user")
        if (server != null && user != null) graph.devSignIn(server, user, intent.getStringExtra("password") ?: "")
    }
}

@Composable
private fun VideoSurface(stage: Stage, modifier: Modifier) {
    AndroidExternalSurface(modifier) {
        onSurface { surface, width, height ->
            var target = VideoRenderTarget(surface, width, height)
            stage.surfaceChanged(target)
            surface.onChanged { w, h -> target = VideoRenderTarget(surface, w, h); stage.surfaceChanged(target) }
            surface.onDestroyed { stage.surfaceDestroyed(target) }
        }
    }
}
