plugins {
    id("hotaru.kotlin.jvm")
    id("hotaru.metro")
    alias(libs.plugins.kotlin.serialization)
}

description = "Use cases, ports (interfaces) and application policies. Depends on domain only."

dependencies {
    api(projects.core.domain)
    testImplementation(projects.infra.testing)
}
