plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.omnitrack.app"
    compileSdk = 35

    val buildVersionCode = project.findProperty("versionCode")?.toString()?.toIntOrNull() ?: 1
    val buildVersionName = project.findProperty("versionName")?.toString() ?: "1.0.0"

    defaultConfig {
        applicationId = "com.omnitrack.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName

        val envTraktClientId = System.getenv("TRAKT_CLIENT_ID") ?: project.findProperty("TRAKT_CLIENT_ID")?.toString() ?: ""
        val envTraktClientSecret = System.getenv("TRAKT_CLIENT_SECRET") ?: project.findProperty("TRAKT_CLIENT_SECRET")?.toString() ?: ""
        val envMalClientId = System.getenv("MAL_CLIENT_ID") ?: project.findProperty("MAL_CLIENT_ID")?.toString() ?: ""
        val envSimklClientId = System.getenv("SIMKL_CLIENT_ID") ?: project.findProperty("SIMKL_CLIENT_ID")?.toString() ?: ""

        buildConfigField("String", "TRAKT_CLIENT_ID", "\"$envTraktClientId\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"$envTraktClientSecret\"")
        buildConfigField("String", "MAL_CLIENT_ID", "\"$envMalClientId\"")
        buildConfigField("String", "SIMKL_CLIENT_ID", "\"$envSimklClientId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/release.jks")
            storePassword = "omnitrack_release_key"
            keyAlias = "omnitrack"
            keyPassword = "omnitrack_release_key"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.palette.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coil 3 for High-Performance Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)

    // Haze for Liquid Glass Backdrop Blur
    implementation(libs.haze)

    // Media3 ExoPlayer for Hero Trailer Surface & Video Playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Jetpack DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
}
