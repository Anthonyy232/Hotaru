plugins {
    id("hotaru.kmp.library")
    id("hotaru.metro")
}

description = "Jellyfin server adapter: implements the application gateways on top of the official Kotlin SDK. SDK types never leave this module."

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.application)
            implementation(libs.jellyfin.core)
            implementation(libs.okhttp)
        }
        jvmTest.dependencies {
            implementation(projects.infra.testing)
            // The SDK logs through slf4j but ships no binding; hosts pick one, tests use the simple logger.
            runtimeOnly(libs.slf4j.simple)
        }
    }
}
