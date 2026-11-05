package com.omnitrack.app.updater

import kotlinx.serialization.Serializable

@Serializable
data class AppRelease(
    val version: String,
    val versionCode: Long = 0,
    val releaseNotes: String = "",
    val apkDownloadUrl: String = "",
    val releasePageUrl: String = "",
    val isNewer: Boolean = false
)

@Serializable
data class VersionManifest(
    val version: String = "",
    val versionCode: Long = 0,
    val apkUrl: String = "",
    val releaseNotes: String = ""
)
