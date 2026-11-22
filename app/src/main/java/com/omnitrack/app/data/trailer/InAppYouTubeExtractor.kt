package com.omnitrack.app.data.trailer

import android.net.Uri
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class TrailerPlaybackSource(
    val videoUrl: String,
    val audioUrl: String? = null,
    val isHls: Boolean = false
)

/**
 * Ported from NuvioMobile-Official: resolves direct YouTube Innertube streams
 * (`visionos`, `android`, `ios`) so trailers play natively inside Media3 ExoPlayer
 * without hitting Error 152-4 iframe embed restrictions.
 */
class InAppYouTubeExtractor {

    companion object {
        private const val TAG = "InAppYouTubeExtractor"
        private const val REQUEST_TIMEOUT_MS = 20_000L
        private const val EXTRACTOR_TIMEOUT_MS = 28_000L
        private const val PREFERRED_SEPARATE_CLIENT = "visionos"

        private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
        private val API_KEY_REGEX = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
        private val VISITOR_DATA_REGEX = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")
        private val QUALITY_LABEL_REGEX = Regex("(\\d{2,4})p")
        private val JSON = Json { ignoreUnknownKeys = true }

        private val DEFAULT_HEADERS: Map<String, String> = mapOf(
            "accept-language" to "en-US,en;q=0.9",
            "user-agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        private val probeClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        private val CLIENTS = listOf(
            YouTubeClient(
                key = "visionos",
                id = "101",
                version = "1.02",
                userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                context = jsonObjectOf(
                    "clientName" to "VISIONOS",
                    "clientVersion" to "1.02",
                    "deviceMake" to "Apple",
                    "deviceModel" to "RealityDevice17,1",
                    "osName" to "visionOS",
                    "osVersion" to "26.5.23O471",
                    "hl" to "en",
                    "gl" to "US"
                ),
                priority = 0
            ),
            YouTubeClient(
                key = "android",
                id = "3",
                version = "20.10.35",
                userAgent = "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip",
                context = jsonObjectOf(
                    "clientName" to "ANDROID",
                    "clientVersion" to "20.10.35",
                    "osName" to "Android",
                    "osVersion" to "14",
                    "platform" to "MOBILE",
                    "androidSdkVersion" to 34,
                    "hl" to "en",
                    "gl" to "US"
                ),
                priority = 1
            ),
            YouTubeClient(
                key = "ios",
                id = "5",
                version = "20.10.1",
                userAgent = "com.google.ios.youtube/20.10.1 (iPhone16,2; U; CPU iOS 17_4 like Mac OS X)",
                context = jsonObjectOf(
                    "clientName" to "IOS",
                    "clientVersion" to "20.10.1",
                    "deviceModel" to "iPhone16,2",
                    "osName" to "iPhone",
                    "osVersion" to "17.4.0.21E219",
                    "platform" to "MOBILE",
                    "hl" to "en",
                    "gl" to "US"
                ),
                priority = 2
            )
        )
    }

    private data class YouTubeClient(
        val key: String,
        val id: String,
        val version: String,
        val userAgent: String,
        val context: JsonObject,
        val priority: Int
    )

    private data class WatchConfig(
        val apiKey: String?,
        val visitorData: String?
    )

    private data class StreamCandidate(
        val client: String,
        val priority: Int,
        val url: String,
        val score: Double,
        val hasN: Boolean,
        val height: Int,
        val fps: Int,
        val ext: String,
        val isDefaultAudioTrack: Boolean = true
    )

    private data class ManifestBestVariant(
        val url: String,
        val width: Int,
        val height: Int,
        val bandwidth: Long
    )

    private data class ManifestCandidate(
        val client: String,
        val priority: Int,
        val manifestUrl: String,
        val selectedVariantUrl: String,
        val height: Int,
        val bandwidth: Long
    )

    private data class HttpResp(
        val ok: Boolean,
        val status: Int,
        val body: String
    )

    suspend fun extractPlaybackSource(youtubeKeyOrUrl: String): TrailerPlaybackSource? = withContext(Dispatchers.IO) {
        if (youtubeKeyOrUrl.isBlank()) return@withContext null
        try {
            withTimeout(EXTRACTOR_TIMEOUT_MS) {
                extractInternal(youtubeKeyOrUrl)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w(TAG, "YouTube extraction failed: ${e.message}")
            null
        }
    }

    private suspend fun extractInternal(youtubeKeyOrUrl: String): TrailerPlaybackSource? {
        val videoId = extractVideoId(youtubeKeyOrUrl) ?: return null
        val watchUrl = "https://www.youtube.com/watch?v=$videoId&hl=en"
        val watchResp = performRequest(watchUrl, "GET", DEFAULT_HEADERS, null)
        if (!watchResp.ok) return null

        val watchConfig = WatchConfig(
            apiKey = API_KEY_REGEX.find(watchResp.body)?.groupValues?.getOrNull(1),
            visitorData = VISITOR_DATA_REGEX.find(watchResp.body)?.groupValues?.getOrNull(1)
        )
        val apiKey = watchConfig.apiKey ?: return null

        val progressive = mutableListOf<StreamCandidate>()
        val adaptiveVideo = mutableListOf<StreamCandidate>()
        val adaptiveAudio = mutableListOf<StreamCandidate>()
        val manifestUrls = mutableListOf<Triple<String, Int, String>>()

        for (client in CLIENTS) {
            runCatching {
                val playerResponse = fetchPlayerResponse(apiKey, videoId, client, watchConfig.visitorData)
                val streamingData = playerResponse.objectValue("streamingData") ?: return@runCatching
                val hlsManifestUrl = streamingData.stringValue("hlsManifestUrl")
                if (!hlsManifestUrl.isNullOrBlank()) {
                    manifestUrls += Triple(client.key, client.priority, hlsManifestUrl)
                }
                for (format in streamingData.listObjectValue("formats")) {
                    val url = format.stringValue("url") ?: continue
                    val mimeType = format.stringValue("mimeType").orEmpty()
                    if (!mimeType.contains("video/") && mimeType.isNotBlank()) continue
                    val height = (format.numberValue("height")
                        ?: parseQualityLabel(format.stringValue("qualityLabel"))?.toDouble()
                        ?: 0.0).toInt()
                    val fps = (format.numberValue("fps") ?: 0.0).toInt()
                    val bitrate = format.numberValue("bitrate") ?: format.numberValue("averageBitrate") ?: 0.0
                    progressive += StreamCandidate(
                        client = client.key,
                        priority = client.priority,
                        url = url,
                        score = videoScore(height, fps, bitrate),
                        hasN = hasNParam(url),
                        height = height,
                        fps = fps,
                        ext = if (mimeType.contains("webm")) "webm" else "mp4"
                    )
                }
                for (format in streamingData.listObjectValue("adaptiveFormats")) {
                    val url = format.stringValue("url") ?: continue
                    val mimeType = format.stringValue("mimeType").orEmpty()
                    val hasVideo = mimeType.contains("video/")
                    val hasAudio = mimeType.contains("audio/") || mimeType.startsWith("audio/")
                    if (hasVideo) {
                        val height = (format.numberValue("height")
                            ?: parseQualityLabel(format.stringValue("qualityLabel"))?.toDouble()
                            ?: 0.0).toInt()
                        val fps = (format.numberValue("fps") ?: 0.0).toInt()
                        val bitrate = format.numberValue("bitrate") ?: format.numberValue("averageBitrate") ?: 0.0
                        adaptiveVideo += StreamCandidate(
                            client = client.key,
                            priority = client.priority,
                            url = url,
                            score = videoScore(height, fps, bitrate),
                            hasN = hasNParam(url),
                            height = height,
                            fps = fps,
                            ext = if (mimeType.contains("webm")) "webm" else "mp4"
                        )
                    } else if (hasAudio) {
                        val bitrate = format.numberValue("bitrate") ?: format.numberValue("averageBitrate") ?: 0.0
                        val sampleRate = format.numberValue("audioSampleRate") ?: 0.0
                        val isDefaultAudio = format.objectValue("audioTrack")?.booleanValue("audioIsDefault") ?: true
                        adaptiveAudio += StreamCandidate(
                            client = client.key,
                            priority = client.priority,
                            url = url,
                            score = audioScore(bitrate, sampleRate),
                            hasN = hasNParam(url),
                            height = 0,
                            fps = 0,
                            ext = if (mimeType.contains("webm")) "webm" else "m4a",
                            isDefaultAudioTrack = isDefaultAudio
                        )
                    }
                }
            }
        }

        if (manifestUrls.isEmpty() && progressive.isEmpty() && adaptiveVideo.isEmpty()) return null

        var bestManifest: ManifestCandidate? = null
        for ((clientKey, priority, manifestUrl) in manifestUrls) {
            runCatching {
                val variant = parseHlsManifest(manifestUrl) ?: return@runCatching
                val candidate = ManifestCandidate(
                    client = clientKey,
                    priority = priority,
                    manifestUrl = manifestUrl,
                    selectedVariantUrl = variant.url,
                    height = variant.height,
                    bandwidth = variant.bandwidth
                )
                if (bestManifest == null || candidate.height > bestManifest.height ||
                    (candidate.height == bestManifest.height && candidate.bandwidth > bestManifest.bandwidth)
                ) {
                    bestManifest = candidate
                }
            }
        }

        val bestProgressive = sortCandidates(progressive).firstOrNull()
        val bestVideo = pickBestForClient(adaptiveVideo, PREFERRED_SEPARATE_CLIENT)
        val bestAudio = pickBestForClient(adaptiveAudio, PREFERRED_SEPARATE_CLIENT)

        val separatedVideoUrl = bestVideo?.url?.let { resolveReachableUrlOrNull(it) }
        val separatedAudioUrl = if (!separatedVideoUrl.isNullOrBlank()) {
            bestAudio?.url?.let { resolveReachableUrlOrNull(it) }
        } else null

        if (!separatedVideoUrl.isNullOrBlank() && !separatedAudioUrl.isNullOrBlank()) {
            return TrailerPlaybackSource(
                videoUrl = separatedVideoUrl,
                audioUrl = separatedAudioUrl,
                isHls = false
            )
        }

        val bestCombinedIsManifest = bestManifest != null &&
            (bestProgressive == null || bestManifest.height >= bestProgressive.height)
        if (bestCombinedIsManifest && bestManifest != null) {
            return TrailerPlaybackSource(
                videoUrl = bestManifest.manifestUrl,
                audioUrl = null,
                isHls = true
            )
        }

        val combinedUrl = bestProgressive?.url?.let { resolveReachableUrlOrNull(it) }
            ?: bestManifest?.selectedVariantUrl
            ?: return null

        return TrailerPlaybackSource(
            videoUrl = combinedUrl,
            audioUrl = null,
            isHls = combinedUrl.contains(".m3u8", ignoreCase = true)
        )
    }

    private suspend fun fetchPlayerResponse(
        apiKey: String,
        videoId: String,
        client: YouTubeClient,
        visitorData: String?
    ): JsonObject {
        val endpoint = "https://www.youtube.com/youtubei/v1/player?key=${encodeUrlComponent(apiKey)}"
        val headers = buildMap {
            putAll(DEFAULT_HEADERS)
            put("content-type", "application/json")
            put("origin", "https://www.youtube.com")
            put("x-youtube-client-name", client.id)
            put("x-youtube-client-version", client.version)
            put("user-agent", client.userAgent)
            if (!visitorData.isNullOrBlank()) put("x-goog-visitor-id", visitorData)
        }
        val payload = jsonObjectOf(
            "videoId" to videoId,
            "contentCheckOk" to true,
            "racyCheckOk" to true,
            "context" to jsonObjectOf("client" to client.context),
            "playbackContext" to jsonObjectOf(
                "contentPlaybackContext" to jsonObjectOf("html5Preference" to "HTML5_PREF_WANTS")
            )
        )
        val resp = performRequest(endpoint, "POST", headers, payload.toString())
        if (!resp.ok) throw IllegalStateException("player API ${client.key} failed (${resp.status})")
        return JSON.parseToJsonElement(resp.body) as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun performRequest(url: String, method: String, headers: Map<String, String>, body: String?): HttpResp {
        val reqBuilder = Request.Builder().url(url).headers(buildHeaders(headers))
        when (method.uppercase()) {
            "POST" -> reqBuilder.post((body ?: "").toRequestBody())
            else -> reqBuilder.get()
        }
        return httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            HttpResp(ok = resp.isSuccessful, status = resp.code, body = resp.body?.string().orEmpty())
        }
    }

    private suspend fun resolveReachableUrlOrNull(url: String): String? {
        if (!url.contains("googlevideo.com")) return url
        val uri = Uri.parse(url)
        val mnParam = uri.getQueryParameter("mn") ?: return url
        val servers = mnParam.split(',').map { it.trim() }.filter { it.isNotBlank() }
        if (servers.size < 2) return if (isUrlReachable(url)) url else null

        val host = uri.host ?: return if (isUrlReachable(url)) url else null
        val candidates = mutableListOf(url)
        servers.forEachIndexed { index, server ->
            val altHost = host
                .replaceFirst(Regex("^rr\\d+---"), "rr${index + 1}---")
                .replaceFirst(Regex("sn-[a-z0-9]+-[a-z0-9]+"), server)
            if (altHost != host) candidates += url.replace(host, altHost)
        }
        if (candidates.size == 1) return if (isUrlReachable(candidates[0])) candidates[0] else null

        val result = CompletableDeferred<String>()
        val probeScope = CoroutineScope(Dispatchers.IO)
        candidates.forEach { candidate ->
            probeScope.launch {
                if (isUrlReachable(candidate)) result.complete(candidate)
            }
        }
        return try {
            withTimeoutOrNull(2_000L) { result.await() }
        } finally {
            probeScope.cancel()
        }
    }

    private fun isUrlReachable(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=0-0")
            .headers(buildHeaders(DEFAULT_HEADERS))
            .build()
        probeClient.newCall(request).execute().use { it.code in 200..299 }
    }.getOrDefault(false)

    private fun parseHlsManifest(manifestUrl: String): ManifestBestVariant? {
        val resp = performRequest(manifestUrl, "GET", DEFAULT_HEADERS, null)
        if (!resp.ok) return null
        val lines = resp.body.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        var bestVariant: ManifestBestVariant? = null
        for (i in lines.indices) {
            val line = lines[i]
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue
            val attrs = parseHlsAttributeList(line)
            val nextLine = lines.getOrNull(i + 1) ?: continue
            if (nextLine.startsWith("#")) continue
            val parts = attrs["RESOLUTION"].orEmpty().split('x')
            val width = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val height = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L
            val candidate = ManifestBestVariant(absolutizeUrl(manifestUrl, nextLine), width, height, bandwidth)
            if (bestVariant == null || candidate.height > bestVariant.height ||
                (candidate.height == bestVariant.height && candidate.bandwidth > bestVariant.bandwidth)
            ) {
                bestVariant = candidate
            }
        }
        return bestVariant
    }

    private fun buildHeaders(source: Map<String, String>): Headers {
        val headers = Headers.Builder()
        source.forEach { (name, value) ->
            if (!name.equals("Accept-Encoding", ignoreCase = true)) headers.add(name, value)
        }
        if (source.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            headers.add("User-Agent", DEFAULT_HEADERS.getValue("user-agent"))
        }
        return headers.build()
    }

    private fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (VIDEO_ID_REGEX.matches(trimmed)) return trimmed
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        if (host.endsWith("youtu.be")) {
            val id = uri.pathSegments.firstOrNull()
            if (!id.isNullOrBlank() && VIDEO_ID_REGEX.matches(id)) return id
        }
        val queryId = uri.getQueryParameter("v")
        if (!queryId.isNullOrBlank() && VIDEO_ID_REGEX.matches(queryId)) return queryId
        val segs = uri.pathSegments
        if (segs.size >= 2 && (segs[0] == "embed" || segs[0] == "shorts" || segs[0] == "live") && VIDEO_ID_REGEX.matches(segs[1])) {
            return segs[1]
        }
        return null
    }

    private fun parseHlsAttributeList(line: String): Map<String, String> {
        val index = line.indexOf(':')
        if (index == -1) return emptyMap()
        val raw = line.substring(index + 1)
        val out = LinkedHashMap<String, String>()
        val key = StringBuilder()
        val value = StringBuilder()
        var inKey = true
        var inQuote = false
        for (ch in raw) {
            when {
                inKey && ch == '=' -> inKey = false
                inKey -> key.append(ch)
                ch == '"' -> inQuote = !inQuote
                ch == ',' && !inQuote -> {
                    val k = key.toString().trim()
                    if (k.isNotEmpty()) out[k] = value.toString().trim()
                    key.clear(); value.clear(); inKey = true
                }
                else -> value.append(ch)
            }
        }
        val last = key.toString().trim()
        if (last.isNotEmpty()) out[last] = value.toString().trim()
        return out
    }

    private fun parseQualityLabel(label: String?): Int? =
        label?.takeIf { it.isNotBlank() }?.let { QUALITY_LABEL_REGEX.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

    private fun hasNParam(url: String): Boolean =
        runCatching { Uri.parse(url).getQueryParameter("n")?.isNotBlank() == true }.getOrDefault(false)

    private fun videoScore(height: Int, fps: Int, bitrate: Double): Double =
        height * 1_000_000_000.0 + fps * 1_000_000.0 + bitrate

    private fun audioScore(bitrate: Double, audioSampleRate: Double): Double =
        bitrate * 1_000_000.0 + audioSampleRate

    private fun sortCandidates(items: List<StreamCandidate>): List<StreamCandidate> =
        items.sortedWith(
            compareBy<StreamCandidate> { if (it.isDefaultAudioTrack) 0 else 1 }
                .thenByDescending { it.score }
                .thenBy { if (it.hasN) 1 else 0 }
                .thenBy {
                    when (it.ext.lowercase()) {
                        "mp4", "m4a" -> 0
                        "webm" -> 1
                        else -> 2
                    }
                }
                .thenBy { it.priority }
        )

    private fun pickBestForClient(items: List<StreamCandidate>, clientKey: String): StreamCandidate? {
        val same = items.filter { it.client == clientKey }
        return if (same.isNotEmpty()) sortCandidates(same).firstOrNull() else sortCandidates(items).firstOrNull()
    }

    private fun absolutizeUrl(baseUrl: String, maybeRelative: String): String {
        if (maybeRelative.startsWith("http://") || maybeRelative.startsWith("https://")) return maybeRelative
        if (maybeRelative.startsWith('/')) {
            val scheme = baseUrl.substringBefore("://", "https")
            val host = baseUrl.substringAfter("://", "").substringBefore('/')
            return if (host.isNotBlank()) "$scheme://$host$maybeRelative" else maybeRelative
        }
        val baseDir = baseUrl.substringBeforeLast('/', missingDelimiterValue = baseUrl)
        return "$baseDir/$maybeRelative"
    }

    private fun encodeUrlComponent(value: String): String =
        value.replace("%", "%25").replace("+", "%2B").replace(" ", "%20").replace("&", "%26").replace("=", "%3D")
}

private fun JsonObject.objectValue(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.listObjectValue(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

private fun JsonObject.stringValue(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else primitive.toString().trim('"')
}

private fun JsonObject.numberValue(key: String): Double? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.toString().trim('"').toDoubleOrNull()
}

private fun JsonObject.booleanValue(key: String): Boolean? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.content.toBooleanStrictOrNull()
}

private fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JsonObject {
    val mapped = LinkedHashMap<String, JsonElement>()
    pairs.forEach { (k, v) -> v?.let { mapped[k] = toJsonElement(it) } }
    return JsonObject(mapped)
}

private fun toJsonElement(value: Any): JsonElement = when (value) {
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value.toDouble())
    is Map<*, *> -> JsonObject(
        value.entries.mapNotNull { (k, v) ->
            val ks = k?.toString() ?: return@mapNotNull null
            v?.let { ks to toJsonElement(it) }
        }.toMap()
    )
    is List<*> -> JsonArray(value.mapNotNull { it?.let(::toJsonElement) })
    else -> JsonPrimitive(value.toString())
}
