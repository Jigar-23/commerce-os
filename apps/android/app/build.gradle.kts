plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
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
        buildConfigField("String", "API_BASE_URL", "\"$defaultDevUrl\"")
        buildConfigField("boolean", "CLEARTEXT_ENABLED", "true")
        buildConfigField("boolean", "ALLOW_BASE_URL_OVERRIDE", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

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
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")

    // UNIT TESTING
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

