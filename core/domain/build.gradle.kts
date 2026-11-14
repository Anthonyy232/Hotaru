plugins {
    id("hotaru.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

description = "Pure domain model: entities, value objects and rules. No I/O; @Serializable only so the item cache stores MediaItem as is."

dependencies {
    api(libs.kotlinx.serialization.core)
}
