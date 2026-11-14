plugins {
    id("hotaru.android.library")
    id("hotaru.metro")
}

description = "Android adapters for the Quest app: Keystore secure store, device identity, logging, the Media3 engine (hardware decode, Jellyfin FFmpeg audio, subtitles burned into the frame) and the MediaCodec capability probe."

dependencies {
    api(projects.core.application)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.effect)
    implementation(libs.media3.ui)
    implementation(libs.ass.media)
    implementation(libs.ass.kt)
    implementation(libs.okhttp)
    // Media3 finds these by reflection: HLS through DefaultMediaSourceFactory, FFmpeg through DefaultRenderersFactory.
    runtimeOnly(libs.media3.exoplayer.hls)
    runtimeOnly(libs.media3.ffmpeg.decoder)
}
