plugins {
    id("hotaru.kmp.library")
    id("hotaru.metro")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

description = "Room 3 (KMP) stores for servers, accounts, the item cache and settings (JSON blob in a preference table)."

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.application)
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(projects.infra.testing)
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspJvm", libs.room.compiler)
    add("kspAndroid", libs.room.compiler)
}
