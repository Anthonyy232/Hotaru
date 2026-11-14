package com.anthonyla.hotaru.infra.jellyfin

import android.content.Context
import org.jellyfin.sdk.JellyfinOptions

actual typealias PlatformContext = Context

internal actual fun JellyfinOptions.Builder.applyPlatformContext(platformContext: PlatformContext) {
    context = platformContext
}
