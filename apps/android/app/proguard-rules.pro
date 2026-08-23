# Production ProGuard / R8 Optimization Rules for Commerce OS Customer App

# Keep data models used in serialization (Retrofit / JSON)
-keepclassmembers class com.commerceos.android.model.** { *; }
-keep class com.commerceos.android.model.** { *; }

# Keep Retrofit & OkHttp interfaces
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep Jetpack Compose Runtime & UI models
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Coroutines
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep JavaScript Interface for Leaflet / Map WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
