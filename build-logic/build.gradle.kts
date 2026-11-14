plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.kotlin.compose)
    implementation(libs.plugin.agp)
    implementation(libs.plugin.metro)
    // Makes the generated `libs` accessor usable from precompiled script plugins.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
