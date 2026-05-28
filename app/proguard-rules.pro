# MigsPod ProGuard/R8 Rules

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Coil (uses OkHttp internally)
-dontwarn coil.**

# Room - keep generated implementations
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# Keep data classes used with RSS/JSON parsing
-keep class com.migspod.data.** { *; }
