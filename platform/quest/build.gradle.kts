plugins {
    id("hotaru.android.compose")
}

description = "Quest immersive cinema: Meta Spatial SDK scene with Compose controls that follow a direct-to-surface cinema screen (flat, curved, 180, 360, stereo; grabbable and resizable by hand), passthrough, dimming and fades; plus the debug bridge."

dependencies {
    api(projects.core.application)
    implementation(libs.spatial.sdk)
    implementation(libs.spatial.sdk.vr)
    // QuestSceneHost extends AppSystemActivity, so subclasses compile against the toolkit (which exposes the core SDK).
    api(libs.spatial.sdk.toolkit)
    implementation(libs.spatial.sdk.compose)
    implementation(libs.spatial.sdk.isdk)
    implementation(libs.androidx.compose.ui)
}
