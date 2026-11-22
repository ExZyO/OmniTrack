package com.omnitrack.app.updater

import android.content.Context
import com.omnitrack.app.BuildConfig
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class GithubBuildStatus(
    val runNumber: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val isBuilding: Boolean,
    val htmlUrl: String,
    val commitMsg: String
)

class AppUpdateChecker(
    private val client: OkHttpClient = com.omnitrack.app.data.network.NetworkClient.sharedClient.newBuilder()
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
            // Concurrent 3-Channel Update Engine (GitHub Releases API + Raw Manifest + GitHub Actions Build Status)
            val releaseApiDeferred = async { fetchFromGithubRelease() }
            val versionJsonDeferred = async { fetchFromRawVersionJson() }
            val buildStatusDeferred = async { fetchBuildStatus() }

            val apiBase = releaseApiDeferred.await()
            val rawRelease = versionJsonDeferred.await()
            val buildStatus = buildStatusDeferred.await()

            val candidate = when {
                apiBase != null && rawRelease != null &&
                    apiBase.apkSha256.isBlank() &&
                    (rawRelease.version == apiBase.version || rawRelease.versionCode == apiBase.versionCode) ->
                    apiBase.copy(apkSha256 = rawRelease.apkSha256)
                apiBase != null -> apiBase
                else -> rawRelease
            }
            val isReleaseNewer = candidate != null && isNewerVersion(
                remoteVersion = candidate.version,
                remoteCode = candidate.versionCode,
                currentVersion = currentVer,
                currentCode = currentCode
            )

            val isActivelyBuildingNewer = buildStatus != null &&
                buildStatus.isBuilding &&
                (buildStatus.runNumber > currentCode || isReleaseNewer)

            when {
                isActivelyBuildingNewer -> {
                    val runNum = buildStatus!!.runNumber
                    AppLogger.i("UpdateChecker", "GitHub Actions build #$runNum is currently compiling (${buildStatus.status})")
                    AppRelease(
                        version = "1.0.0-b$runNum",
                        versionCode = runNum,
                        releaseNotes = buildStatus.commitMsg.ifBlank { candidate?.releaseNotes.orEmpty() },
                        apkDownloadUrl = if (isReleaseNewer) candidate?.apkDownloadUrl.orEmpty() else "",
                        releasePageUrl = candidate?.releasePageUrl?.ifBlank {
                            "https://github.com/$githubRepo/releases"
                        } ?: "https://github.com/$githubRepo/releases",
                        apkSha256 = if (isReleaseNewer) candidate?.apkSha256.orEmpty() else "",
                        isNewer = true,
                        isBuilding = true,
                        buildRunNumber = runNum,
                        buildCommitMessage = buildStatus.commitMsg,
                        actionsUrl = buildStatus.htmlUrl,
                        latestRunConclusion = buildStatus.conclusion.orEmpty()
                    )
                }

                candidate != null && isReleaseNewer -> {
                    AppLogger.i("UpdateChecker", "Newer version available: ${candidate.version}")
                    candidate.copy(
                        isNewer = true,
                        isBuilding = false,
                        buildRunNumber = buildStatus?.runNumber ?: candidate.versionCode,
                        buildCommitMessage = buildStatus?.commitMsg.orEmpty(),
                        actionsUrl = buildStatus?.htmlUrl ?: "https://github.com/$githubRepo/actions",
                        latestRunConclusion = buildStatus?.conclusion.orEmpty()
                    )
                }

                else -> {
                    AppLogger.i("UpdateChecker", "App is up to date (current code $currentCode)")
                    null
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UpdateChecker", "Failed to check for updates: ${e.message}", e)
            null
        }
    }

    suspend fun fetchBuildStatus(): GithubBuildStatus? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$githubRepo/actions/runs?per_page=5&t=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("Cache-Control", "no-store")
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val root = json.parseToJsonElement(body).jsonObject
                val runs = root["workflow_runs"]?.jsonArray ?: return@runCatching null
                if (runs.isEmpty()) return@runCatching null

                val matchingRegex = Regex("build\\s*android|apk|omnitrack", RegexOption.IGNORE_CASE)
                val apkRun = runs.firstOrNull { el ->
                    val name = el.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    matchingRegex.containsMatchIn(name)
                }?.jsonObject ?: runs.first().jsonObject

                val status = apkRun["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val conclusion = apkRun["conclusion"]?.jsonPrimitive?.contentOrNull
                val isBuilding = status.equals("in_progress", ignoreCase = true) ||
                    status.equals("queued", ignoreCase = true) ||
                    status.equals("requested", ignoreCase = true) ||
                    status.equals("waiting", ignoreCase = true)

                val runNum = apkRun["run_number"]?.jsonPrimitive?.longOrNull ?: 0L
                val name = apkRun["name"]?.jsonPrimitive?.contentOrNull ?: "Build Android APK"
                val htmlUrl = apkRun["html_url"]?.jsonPrimitive?.contentOrNull
                    ?: "https://github.com/$githubRepo/actions"
                val commitMsg = apkRun["head_commit"]?.jsonObject
                    ?.get("message")?.jsonPrimitive?.contentOrNull
                    ?.lineSequence()?.firstOrNull()?.trim().orEmpty()

                GithubBuildStatus(
                    runNumber = runNum,
                    name = name,
                    status = status,
                    conclusion = conclusion,
                    isBuilding = isBuilding,
                    htmlUrl = htmlUrl,
                    commitMsg = commitMsg
                )
            }
        }.getOrNull()
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

                val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: ""
                val releaseNotes = root["body"]?.jsonPrimitive?.contentOrNull ?: ""
                val htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull ?: ""

                var apkUrl = ""
                var apkSha256 = ""
                val assets = root["assets"]?.jsonArray
                if (assets != null) {
                    val isDebug = BuildConfig.DEBUG
                    for (asset in assets) {
                        val obj = asset.jsonObject
                        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (isDebug) {
                            if (name.endsWith("-debug.apk", ignoreCase = true)) {
                                apkUrl = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: ""
                                apkSha256 = obj["digest"]?.jsonPrimitive?.contentOrNull
                                    ?.removePrefix("sha256:")?.trim().orEmpty()
                                break
                            }
                        } else {
                            if (name.endsWith(".apk", ignoreCase = true) && !name.contains("-debug", ignoreCase = true)) {
                                apkUrl = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: ""
                                apkSha256 = obj["digest"]?.jsonPrimitive?.contentOrNull
                                    ?.removePrefix("sha256:")?.trim().orEmpty()
                                break
                            }
                        }
                    }
                    if (apkUrl.isBlank()) {
                        for (asset in assets) {
                            val obj = asset.jsonObject
                            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: ""
                                apkSha256 = obj["digest"]?.jsonPrimitive?.contentOrNull
                                    ?.removePrefix("sha256:")?.trim().orEmpty()
                                break
                            }
                        }
                    }
                }

                if (apkSha256.isBlank()) {
                    apkSha256 = extractSha256FromReleaseNotes(releaseNotes, isDebug = BuildConfig.DEBUG)
                }

                val buildCode = extractBuildNumber(tagName)
                AppRelease(
                    version = tagName.removePrefix("v"),
                    versionCode = buildCode,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl,
                    releasePageUrl = htmlUrl,
                    apkSha256 = apkSha256
                )
            }
        }.getOrNull()
    }

    private fun extractSha256FromReleaseNotes(notes: String, isDebug: Boolean): String {
        val linePrefix = if (isDebug) "debug" else "release"
        val targeted = Regex("(?im)^.*$linePrefix.*sha-?256[^a-f0-9]*([a-f0-9]{64}).*$")
            .find(notes)?.groupValues?.getOrNull(1)
        if (!targeted.isNullOrBlank()) return targeted.lowercase()
        return Regex("(?im)^.*sha-?256[^a-f0-9]*([a-f0-9]{64}).*$")
            .find(notes)?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
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
                val downloadUrl = if (BuildConfig.DEBUG) {
                    manifest.debugApkUrl.takeIf { it.isNotBlank() } ?: manifest.apkUrl
                } else {
                    manifest.apkUrl
                }
                val expectedSha = if (BuildConfig.DEBUG && manifest.debugApkUrl.isNotBlank()) {
                    manifest.debugApkSha256.takeIf { it.isNotBlank() } ?: manifest.apkSha256
                } else {
                    manifest.apkSha256
                }
                AppRelease(
                    version = manifest.version.removePrefix("v"),
                    versionCode = manifest.versionCode,
                    releaseNotes = manifest.releaseNotes,
                    apkDownloadUrl = downloadUrl,
                    releasePageUrl = "https://github.com/$githubRepo/releases",
                    apkSha256 = expectedSha
                )
            }
        }.getOrNull()
    }

    /**
     * Compares remote version with local installed version:
     * - First checks build numbers if in "1.0.0-bX" format
     * - Then compares versionCode if provided
     * - Then parses standard semver major.minor.patch
     */
    private fun isNewerVersion(
        remoteVersion: String,
        remoteCode: Long,
        currentVersion: String,
        currentCode: Long
    ): Boolean {
        val remoteBuild = extractBuildNumber(remoteVersion)
        val localBuild = extractBuildNumber(currentVersion)

        if (remoteBuild > 0 && localBuild > 0) {
            return remoteBuild > localBuild
        }

        if (remoteCode > 0 && currentCode > 0 && remoteCode != currentCode) {
            return remoteCode > currentCode
        }

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
