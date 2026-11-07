import java.util.Properties
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.commerceos.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.commerceos.android"

        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val defaultDevUrl = System.getenv("COMMERCEOS_DEV_API_URL") ?: "https://commerce-os-api.onrender.com"
        val localProps = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localFile.inputStream().use { localProps.load(it) }
        }
        val mapsApiKey: String = System.getenv("GOOGLE_MAPS_API_KEY")
            ?: System.getenv("MAPS_API_KEY")
            ?: localProps.getProperty("MAPS_API_KEY")
            ?: localProps.getProperty("GOOGLE_MAPS_API_KEY")
            ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "API_BASE_URL", "\"$defaultDevUrl\"")
        buildConfigField("boolean", "CLEARTEXT_ENABLED", "true")
        buildConfigField("boolean", "ALLOW_BASE_URL_OVERRIDE", "true")
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            val keystoreFile = if (!keystorePath.isNullOrBlank()) file(keystorePath) else null
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("KEY_ALIAS")
            val keyPasswordEnv = System.getenv("KEY_PASSWORD")

            val isReleaseTask = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            if (isReleaseTask) {
                if (keystoreFile == null || !keystoreFile.exists()) {
                    throw GradleException("Release build signing failed: KEYSTORE_PATH is required and must point to a valid keystore file.")
                }
                if (keystorePassword.isNullOrBlank() || keyAliasEnv.isNullOrBlank() || keyPasswordEnv.isNullOrBlank()) {
                    throw GradleException("Release build signing failed: KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD must be provided via secure environment variables.")
                }
            }

            // Invalidate historically exposed signing certificate from git history (SHA-256: 08:5D:02:68...)
            val revokedCertSha256 = "085D0268C184C1F0F963219B37EFFDC2ECCED8717B4027730A79475B1481EDD6"
            if (keystoreFile != null && keystoreFile.exists() && !keystorePassword.isNullOrBlank() && !keyAliasEnv.isNullOrBlank()) {
                val cert = try {
                    val ks = KeyStore.getInstance(if (keystoreFile.name.endsWith(".jks", ignoreCase = true)) "JKS" else "PKCS12")
                    keystoreFile.inputStream().use { ks.load(it, keystorePassword.toCharArray()) }
                    ks.getCertificate(keyAliasEnv)
                } catch (_: Exception) {
                    try {
                        val ksFallback = KeyStore.getInstance("PKCS12")
                        keystoreFile.inputStream().use { ksFallback.load(it, keystorePassword.toCharArray()) }
                        ksFallback.getCertificate(keyAliasEnv)
                    } catch (_: Exception) { null }
                }

                if (cert != null) {
                    val md = MessageDigest.getInstance("SHA-256")
                    val fp = md.digest(cert.encoded).joinToString("") { b -> "%02X".format(b) }
                    if (fp.equals(revokedCertSha256, ignoreCase = true)) {
                        throw GradleException("REVOKED_SIGNING_KEY_REJECTED: The provided keystore matches the historically exposed certificate (SHA-256: 08:5D:02:68...). This key has been permanently revoked and rotated.")
                    }
                }
            }

            if (keystoreFile != null && keystoreFile.exists() && !keystorePassword.isNullOrBlank() && !keyAliasEnv.isNullOrBlank() && !keyPasswordEnv.isNullOrBlank()) {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            val prodUrl = System.getenv("COMMERCEOS_PROD_API_URL") ?: "https://commerce-os-api.onrender.com"
            buildConfigField("String", "API_BASE_URL", "\"$prodUrl\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "CLEARTEXT_ENABLED", "false")
            buildConfigField("boolean", "ALLOW_BASE_URL_OVERRIDE", "false")
        }
        debug {
            val debugUrl = System.getenv("COMMERCEOS_DEV_API_URL") ?: "https://commerce-os-api.onrender.com"
            buildConfigField("String", "API_BASE_URL", "\"$debugUrl\"")
            buildConfigField("boolean", "CLEARTEXT_ENABLED", "true")
            buildConfigField("boolean", "ALLOW_BASE_URL_OVERRIDE", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ROOM DATABASE (Local Persistent Cache)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // RETROFIT & OKHTTP REST NETWORK CLIENT
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // COIL IMAGE LOADING: URL loading, memory/disk caching, crossfade, placeholder/error.
    implementation("io.coil-kt:coil-compose:2.6.0")

    // MAPS & GEOLOCATION
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.maps.android:android-maps-utils:3.8.0")
    implementation("com.google.android.libraries.places:places:3.4.0")

    // FIREBASE AUTH (GOOGLE PHONE SMS)
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-auth-ktx")

    // UNIT TESTING
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

