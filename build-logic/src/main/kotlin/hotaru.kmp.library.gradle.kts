import org.gradle.accessors.dm.LibrariesForLibs

/** For non-UI multiplatform modules that need a real Android target (Room generates per-target code). */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("hotaru.dependency.lint")
}

val libs = the<LibrariesForLibs>()

kotlin {
    jvmToolchain(21)
    jvm()
    android {
        namespace = project.hotaruNamespace
        compileSdk = HotaruAndroid.COMPILE_SDK
        minSdk = HotaruAndroid.MIN_SDK
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
