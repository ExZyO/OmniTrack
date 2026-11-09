package com.omnitrack.app.updater

import android.content.Context
import com.omnitrack.app.BuildConfig
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AppUpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    private val githubRepo = "ExZyO/OmniTrack"

    suspend fun checkForUpdate(context: Context): AppRelease? = withContext(Dispatchers.IO) {
        val currentVer = BuildConfig.VERSION_NAME
        val currentCode = BuildConfig.VERSION_CODE.toLong()
        AppLogger.i("UpdateChecker", "Checking for updates. Current version: $currentVer (code $currentCode)")

        try {
            // Concurrent Multi-Source Version Query (Fast dual-channel update engine)
            // 1. Direct GitHub Releases API (Has complete assets and release notes)
            val releaseApiDeferred = async { fetchFromGithubRelease() }

            // 2. Raw version.json (Bypasses GitHub API rate limits completely)
            val versionJsonDeferred = async { fetchFromRawVersionJson() }

            val apiRelease = releaseApiDeferred.await()
            val rawRelease = versionJsonDeferred.await()

            // Merge sources: prefer API release for notes/assets, fallback to raw manifest
            val candidate = apiRelease ?: rawRelease ?: return@withContext null

            val isNew = isNewerVersion(
                remoteVersion = candidate.version,
                remoteCode = candidate.versionCode,
                currentVersion = currentVer,
                currentCode = currentCode
            )

            if (isNew) {
                AppLogger.i("UpdateChecker", "Newer version available: ${candidate.version}")
                candidate.copy(isNewer = true)
            } else {
                AppLogger.i("UpdateChecker", "App is up to date (${candidate.version})")
                null
            }
        } catch (e: Exception) {
            AppLogger.e("UpdateChecker", "Failed to check for updates: ${e.message}", e)
            null
        }
    }

    private fun fetchFromGithubRelease(): AppRelease? {
        val url = "https://api.github.com/repos/$githubRepo/releases/latest?t=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("Cache-Control", "no-store")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = json.parseToJsonElement(body).jsonObject

                val tagName = root["tag_name"]?.jsonPrimitive?.content ?: ""
                val releaseNotes = root["body"]?.jsonPrimitive?.content ?: ""
                val htmlUrl = root["html_url"]?.jsonPrimitive?.content ?: ""

                var apkUrl = ""
                val assets = root["assets"]?.jsonArray
                if (assets != null) {
                    val isDebug = com.omnitrack.app.BuildConfig.DEBUG
                    for (asset in assets) {
                        val name = asset.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                        if (isDebug) {
                            if (name.endsWith("-debug.apk", ignoreCase = true)) {
                                apkUrl = asset.jsonObject["browser_download_url"]?.jsonPrimitive?.content ?: ""
                                break
                            }
                        } else {
                            if (name.endsWith(".apk", ignoreCase = true) && !name.contains("-debug", ignoreCase = true)) {
                                apkUrl = asset.jsonObject["browser_download_url"]?.jsonPrimitive?.content ?: ""
                                break
                            }
                        }
                    }
                    if (apkUrl.isBlank()) {
                        for (asset in assets) {
                            val name = asset.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.jsonObject["browser_download_url"]?.jsonPrimitive?.content ?: ""
                                break
                            }
                        }
                    }
                }

                val buildCode = extractBuildNumber(tagName)
                AppRelease(
                    version = tagName.removePrefix("v"),
                    versionCode = buildCode,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl,
                    releasePageUrl = htmlUrl
                )
            }
        }.getOrNull()
    }

    private fun fetchFromRawVersionJson(): AppRelease? {
        val url = "https://raw.githubusercontent.com/$githubRepo/main/version.json?t=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-store")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val manifest = json.decodeFromString<VersionManifest>(body)
                val downloadUrl = if (com.omnitrack.app.BuildConfig.DEBUG) {
                    manifest.debugApkUrl.takeIf { it.isNotBlank() } ?: manifest.apkUrl
                } else {
                    manifest.apkUrl
                }
                AppRelease(
                    version = manifest.version.removePrefix("v"),
                    versionCode = manifest.versionCode,
                    releaseNotes = manifest.releaseNotes,
                    apkDownloadUrl = downloadUrl,
                    releasePageUrl = "https://github.com/$githubRepo/releases"
                )
            }
        }.getOrNull()
    }

    /**
     * Compares remote version with local installed version:
     * - First checks build numbers if in "1.0.0-bX" format
     * - Then parses standard semver major.minor.patch
     */
    private fun isNewerVersion(
        remoteVersion: String,
        remoteCode: Long,
        currentVersion: String,
        currentCode: Long
    ): Boolean {
        // Compare build numbers if present (e.g. 1.0.0-b8 vs 1.0.0-b9)
        val remoteBuild = extractBuildNumber(remoteVersion)
        val localBuild = extractBuildNumber(currentVersion)

        if (remoteBuild > 0 && localBuild > 0) {
            return remoteBuild > localBuild
        }

        // Compare versionCode if remote explicitly provided it
        if (remoteCode > 0 && currentCode > 0 && remoteCode != currentCode) {
            return remoteCode > currentCode
        }

        // Standard semver comparison
        val remoteParts = parseSemver(remoteVersion)
        val localParts = parseSemver(currentVersion)

        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }

        return false
    }

    private fun parseSemver(version: String): List<Int> {
        val clean = version.removePrefix("v").substringBefore("-")
        return clean.split(".").mapNotNull { it.toIntOrNull() }
    }

    private fun extractBuildNumber(version: String): Long {
        val regex = Regex("-b(\\d+)")
        val match = regex.find(version)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }
}
