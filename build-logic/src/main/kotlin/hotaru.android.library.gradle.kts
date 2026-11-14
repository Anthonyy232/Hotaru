import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.android.library")
    id("hotaru.dependency.lint")
}

val libs = the<LibrariesForLibs>()

android {
    namespace = project.hotaruNamespace
    compileSdk = HotaruAndroid.COMPILE_SDK
    defaultConfig {
        minSdk = HotaruAndroid.MIN_SDK
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
