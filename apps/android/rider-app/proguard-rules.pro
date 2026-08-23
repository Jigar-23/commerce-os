# Production ProGuard / R8 Optimization Rules for Commerce OS Rider App

# Keep data models used in serialization
-keepclassmembers class com.commerceos.rider.model.** { *; }
-keep class com.commerceos.rider.model.** { *; }

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

# Keep JavaScript Interface for Map WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
