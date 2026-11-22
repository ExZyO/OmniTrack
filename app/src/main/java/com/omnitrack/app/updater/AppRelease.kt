package com.omnitrack.app.updater

import kotlinx.serialization.Serializable

@Serializable
data class AppRelease(
    val version: String,
    val versionCode: Long = 0,
    val releaseNotes: String = "",
    val apkDownloadUrl: String = "",
    val releasePageUrl: String = "",
    val isNewer: Boolean = false,
    val isBuilding: Boolean = false,
    val buildRunNumber: Long = 0,
    val buildCommitMessage: String = "",
    val actionsUrl: String = "https://github.com/ExZyO/OmniTrack/actions",
    val latestRunConclusion: String = ""
)

@Serializable
data class VersionManifest(
    val version: String = "",
    val versionCode: Long = 0,
    val apkUrl: String = "",
    val debugApkUrl: String = "",
    val releaseNotes: String = ""
)
