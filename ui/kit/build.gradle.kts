plugins {
    id("hotaru.android.compose")
}

description = "A visionOS-style design system for Hotaru: glass, type and color tokens over Meta's Spatial UI set, plus the app's own pieces: media cards and rows, server images, the seek bar and window layout."

dependencies {
    api(projects.core.presentation)
    api(libs.spatial.sdk.uiset)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.coil.compose)
    implementation(libs.coil.network.core)
    implementation(libs.androidx.window.core)
}
