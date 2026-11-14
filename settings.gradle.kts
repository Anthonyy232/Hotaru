pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "hotaru"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":core:domain",
    ":core:application",
    ":core:presentation",
    ":infra:jellyfin",
    ":infra:persistence",
    ":infra:testing",
    ":ui:kit",
    ":ui:screens",
    ":platform:android",
    ":platform:quest",
    ":app:quest",
)
