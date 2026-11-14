package com.anthonyla.hotaru.infra.jellyfin

import org.jellyfin.sdk.JellyfinOptions

/** What the SDK needs from the host: the Android `Context` on Android, nothing on the JVM. */
expect abstract class PlatformContext

internal expect fun JellyfinOptions.Builder.applyPlatformContext(platformContext: PlatformContext)
