# kotlin-logging (under the Jellyfin SDK) has an slf4j 2 path, never taken with the slf4j 1 binding the app ships.
-dontwarn org.slf4j.spi.CallerBoundaryAware
-dontwarn org.slf4j.spi.LoggingEventBuilder

# The Spatial SDK tells systems apart by class, so R8 must not merge them, the app's included, and its native side calls
# into its classes by name.
-keep class com.meta.spatial.** { *; }
-keep class * extends com.meta.spatial.core.SystemBase
# Kept whole, it refers to an OkHttp shim and an annotation the app never loads.
-dontwarn com.meta.okhttp3compat.**
-dontwarn kotlinx.parcelize.Parcelize
