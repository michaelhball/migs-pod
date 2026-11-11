# MigsPod R8 rules.
# Room, Media3, Coil and coroutines ship their own consumer rules, and the app does
# no reflection of its own, so nothing here needs a -keep.

# Optional TLS providers OkHttp looks for at runtime.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
