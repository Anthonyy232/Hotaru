package com.anthonyla.hotaru.infra.jellyfin

import org.jellyfin.sdk.JellyfinOptions

actual abstract class PlatformContext

internal actual fun JellyfinOptions.Builder.applyPlatformContext(platformContext: PlatformContext) = Unit
