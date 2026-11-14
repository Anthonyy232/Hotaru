import org.gradle.api.Project

object HotaruAndroid {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 34
}

val Project.hotaruNamespace: String get() = "com.anthonyla.hotaru." + path.removePrefix(":").replace(':', '.')
