package com.omnitrack.app.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object AppUpdateInstaller {

    private val client = com.omnitrack.app.data.network.NetworkClient.sharedClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val allowedAssetHosts = setOf(
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )

    private fun validateAllowedDownloadUrl(url: HttpUrl) {
        if (!url.isHttps) {
            throw SecurityException("Refusing non-HTTPS update URL")
        }
        val host = url.host.lowercase()
        val isGithubReleasePath = host == "github.com" &&
            url.encodedPath.startsWith("/ExZyO/OmniTrack/releases/download/")
        val isAllowedCdn = host in allowedAssetHosts
        if (!isGithubReleasePath && !isAllowedCdn) {
            throw SecurityException("Untrusted update host: $host")
        }
    }

    /**
     * Downloads APK file and triggers native package installer.
     * @param onProgress Callback with progress from 0.0f to 1.0f
     */
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        expectedSha256: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val parsedUrl = apkUrl.toHttpUrlOrNull()
                ?: throw SecurityException("Invalid update URL")
            validateAllowedDownloadUrl(parsedUrl)

            AppLogger.i("UpdateInstaller", "Starting download from: $apkUrl")

            val request = Request.Builder()
                .url(parsedUrl)
                .build()

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "OmniTrack-update.apk")
            if (apkFile.exists()) apkFile.delete()

            val digest = MessageDigest.getInstance("SHA-256")
            client.newCall(request).execute().use { response ->
                validateAllowedDownloadUrl(response.request.url)
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed with HTTP ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("Empty response body")
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            digest.update(buffer, 0, bytesRead)
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                                onProgress(progress)
                                AppUpdateNotifier.showDownloadProgress(context, progress)
                            }
                        }
                        output.flush()
                    }
                }
            }

            val normalizedExpected = expectedSha256
                ?.trim()
                ?.removePrefix("sha256:")
                ?.lowercase()
                .orEmpty()
            if (normalizedExpected.isNotBlank()) {
                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (actualSha256 != normalizedExpected) {
                    apkFile.delete()
                    throw SecurityException("APK checksum verification failed")
                }
                AppLogger.i("UpdateInstaller", "APK SHA-256 verified")
            } else {
                AppLogger.w("UpdateInstaller", "No SHA-256 checksum supplied for release asset")
            }

            AppLogger.i("UpdateInstaller", "APK downloaded successfully (${apkFile.length()} bytes)")
            onProgress(1.0f)
            AppUpdateNotifier.showInstallPrompt(context, apkFile)

            // Trigger system installer on main thread
            withContext(Dispatchers.Main) {
                launchPackageInstaller(context, apkFile)
            }
        }
    }

    private fun launchPackageInstaller(context: Context, apkFile: File) {
        val authority = "${context.packageName}.provider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

        AppLogger.i("UpdateInstaller", "Launching package installer with URI: $apkUri")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
