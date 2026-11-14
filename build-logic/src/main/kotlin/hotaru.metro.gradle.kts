import dev.zacsweers.metro.gradle.MetroPluginExtension
import org.gradle.accessors.dm.LibrariesForLibs

/**
 * Metro compile-time DI. Plain injection uses the JSR-330 annotations (jakarta.inject `@Inject`,
 * `@Singleton`), so core classes carry no DI framework types; Metro's own annotations appear only
 * where JSR-330 has no equivalent (contributions, assisted factories, the graph). Apply after the
 * Kotlin convention plugin.
 */

// Applied imperatively: the Metro plugin needs the Kotlin plugin, which the accessor generation for a plugins block lacks.
apply(plugin = "dev.zacsweers.metro")

val libs = the<LibrariesForLibs>()

configure<MetroPluginExtension> {
    interop { includeJakarta() }
}

dependencies {
    add(if (plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) "commonMainImplementation" else "implementation", libs.jakarta.inject)
}
