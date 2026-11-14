plugins {
    id("hotaru.kotlin.jvm")
    id("hotaru.metro")
}

description = "View models, navigation and the app shell. No UI toolkit."

dependencies {
    api(projects.core.application)
    testImplementation(projects.infra.testing)
}
