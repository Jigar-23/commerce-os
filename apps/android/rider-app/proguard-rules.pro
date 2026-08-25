# Production ProGuard / R8 Optimization Rules for Commerce OS Rider App

# Retain generic signatures and reflection attributes essential for Retrofit + Kotlin Suspend functions
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Keep data models used in serialization (Retrofit / JSON / GSON)
-keep class com.commerceos.rider.model.** { *; }
-keepclassmembers class com.commerceos.rider.model.** { *; }
-keep class com.commerceos.rider.data.** { *; }
-keepclassmembers class com.commerceos.rider.data.** { *; }

# Keep GSON serialization
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Retrofit, OkHttp and Network APIs
-keep interface com.commerceos.rider.** { *; }
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-keepclassmembers class retrofit2.KotlinExtensions* { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# OkHttp & Okio
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keepclassmembers enum * { *; }

# Kotlin Coroutines and Continuation (crucial for Retrofit suspend return type reflection)
-keep class kotlin.coroutines.** { *; }
-keepclassmembers class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-dontwarn kotlin.Unit

# Keep Jetpack Compose Runtime & UI models
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep JavaScript Interface for Map WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
