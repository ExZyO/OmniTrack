import java.io.File
import java.util.Properties

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

    val localProps = Properties().apply {
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { load(it) }
        }
    }

    fun readSecretProp(name: String): String {
        return System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: project.findProperty(name)?.toString()?.takeIf { it.isNotBlank() }
            ?: localProps.getProperty(name)?.takeIf { it.isNotBlank() }
            ?: ""
    }

    fun escapeBuildConfig(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    val buildVersionCode = project.findProperty("versionCode")?.toString()?.toIntOrNull() ?: 1
    val buildVersionName = project.findProperty("versionName")?.toString() ?: "1.0.0"

    defaultConfig {
        applicationId = "com.omnitrack.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName

        val envAnilistClientId = readSecretProp("ANILIST_CLIENT_ID")
        val envTraktClientId = readSecretProp("TRAKT_CLIENT_ID")
        val envTraktClientSecret = readSecretProp("TRAKT_CLIENT_SECRET")
        val envMalClientId = readSecretProp("MAL_CLIENT_ID")
        val envSimklClientId = readSecretProp("SIMKL_CLIENT_ID")
        val envSimklClientSecret = readSecretProp("SIMKL_CLIENT_SECRET")
        val envBangumiClientId = readSecretProp("BANGUMI_CLIENT_ID")
        val envBangumiClientSecret = readSecretProp("BANGUMI_CLIENT_SECRET")
        val envComickClientId = readSecretProp("COMICK_CLIENT_ID")

        buildConfigField("String", "ANILIST_CLIENT_ID", "\"${escapeBuildConfig(envAnilistClientId)}\"")
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${escapeBuildConfig(envTraktClientId)}\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"${escapeBuildConfig(envTraktClientSecret)}\"")
        buildConfigField("String", "MAL_CLIENT_ID", "\"${escapeBuildConfig(envMalClientId)}\"")
        buildConfigField("String", "SIMKL_CLIENT_ID", "\"${escapeBuildConfig(envSimklClientId)}\"")
        buildConfigField("String", "SIMKL_CLIENT_SECRET", "\"${escapeBuildConfig(envSimklClientSecret)}\"")
        buildConfigField("String", "BANGUMI_CLIENT_ID", "\"${escapeBuildConfig(envBangumiClientId)}\"")
        buildConfigField("String", "BANGUMI_CLIENT_SECRET", "\"${escapeBuildConfig(envBangumiClientSecret)}\"")
        buildConfigField("String", "COMICK_CLIENT_ID", "\"${escapeBuildConfig(envComickClientId)}\"")

        manifestPlaceholders["appName"] = "OmniTrack"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val releaseStorePath = readSecretProp("KEYSTORE_FILE")
    val releaseStorePassword = readSecretProp("KEYSTORE_PASSWORD")
    val releaseKeyAlias = readSecretProp("KEY_ALIAS")
    val releaseKeyPassword = readSecretProp("KEY_PASSWORD")
    val releaseKeystoreFile = if (releaseStorePath.isNotBlank()) {
        val candidate = File(releaseStorePath)
        if (candidate.isAbsolute) candidate else rootProject.file(releaseStorePath)
    } else {
        null
    }
    val hasReleaseSigning = releaseKeystoreFile != null &&
        releaseKeystoreFile.exists() &&
        releaseStorePassword.isNotBlank() &&
        releaseKeyAlias.isNotBlank() &&
        releaseKeyPassword.isNotBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            manifestPlaceholders["appName"] = "OmniTrack"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            manifestPlaceholders["appName"] = "OmniTrack (Debug)"
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

    // Coil 3 for High-Performance Image Loading & Animated GIF Support
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.okhttp)

    // Haze for Liquid Glass Backdrop Blur
    implementation(libs.haze)

    // Smooth drag-and-drop list reordering
    implementation(libs.reorderable)

    // Media3 ExoPlayer for Hero Trailer Surface & Video Playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Jetpack DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jsoup)

    debugImplementation(libs.androidx.ui.tooling)
}
