plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("hotaru.dependency.lint")
    id("hotaru.metro")
}

description = "Meta Quest hybrid app: a panel window for browsing and in-window playback, and an immersive Spatial SDK cinema, around the shared screens and the Media3 engine."

android {
    namespace = "com.anthonyla.hotaru.quest"
    compileSdk = HotaruAndroid.COMPILE_SDK
    defaultConfig {
        applicationId = "com.anthonyla.hotaru.quest"
        minSdk = HotaruAndroid.MIN_SDK
        targetSdk = HotaruAndroid.COMPILE_SDK
        versionCode = 1
        versionName = "0.1.0"
        //noinspection ChromeOsAbiSupport: the Spatial SDK ships arm64 only
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    signingConfigs {
        // The release key and its passwords stay out of the repo: set these in ~/.gradle/gradle.properties.
        create("release") {
            storeFile = providers.gradleProperty("hotaru.keystore").orNull?.let(::file)
            storePassword = providers.gradleProperty("hotaru.keystorePassword").orNull
            keyAlias = providers.gradleProperty("hotaru.keyAlias").orNull
            keyPassword = providers.gradleProperty("hotaru.keyPassword").orNull
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs["release"]
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST")
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.ui.screens)
    implementation(projects.infra.jellyfin)
    implementation(projects.infra.persistence)
    implementation(projects.platform.android)
    implementation(projects.platform.quest)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.network.okhttp)
    runtimeOnly(libs.slf4j.android)     // the Jellyfin SDK's logging binding
    constraints {
        // material3 and Coil only ask for core 1.16; resolve the current release instead.
        implementation(libs.androidx.core.ktx)
    }
}
