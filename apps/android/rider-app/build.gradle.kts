import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.commerceos.rider"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.commerceos.rider"
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
        }
        debug {
            val debugUrl = System.getenv("COMMERCEOS_DEV_API_URL") ?: "https://commerce-os-api.onrender.com"
            buildConfigField("String", "API_BASE_URL", "\"$debugUrl\"")
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

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.firebase:firebase-messaging-ktx:23.4.1")
    implementation("com.google.android.play:integrity:1.2.0")

    // MAPS & GEOLOCATION
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.json:json:20240303")
}
