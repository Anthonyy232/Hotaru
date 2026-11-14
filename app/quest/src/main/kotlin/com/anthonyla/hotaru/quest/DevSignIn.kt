package com.anthonyla.hotaru.quest

import com.anthonyla.hotaru.application.auth.SignInWithPassword
import com.anthonyla.hotaru.application.logFailure
import com.anthonyla.hotaru.application.ports.Logger
import com.anthonyla.hotaru.application.servers.AddServer
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Signs in from the `server`/`user`/`password` intent extras, skipping the connect flow (see tools/quest-deploy.ps1). */
class DevSignIn @Inject constructor(
    private val scope: CoroutineScope,
    private val addServer: AddServer,
    private val signIn: SignInWithPassword,
    private val logger: Logger,
) {
    operator fun invoke(server: String, user: String, password: String) {
        scope.launch {
            val added = addServer(server).getOrNull() ?: run { logger.warn(TAG, { "could not add $server" }); return@launch }
            signIn(added, user, password).logFailure(logger, TAG, "sign-in")
        }
    }

    private companion object { const val TAG = "Dev" }
}
