plugins {
    id("hotaru.kotlin.jvm")
}

description = "In-memory fakes for every port, fixture data and the harness wiring them into the use cases, shared by tests."

dependencies {
    api(projects.core.application)
    api(libs.kotlinx.coroutines.core)
}
