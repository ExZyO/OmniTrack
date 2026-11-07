package com.omnitrack.app.data.ai

import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.network.NetworkPacer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class AiQueryResult(
    val resolvedTitle: String,
    val detectedType: MediaType? = null,
    val isAiResolved: Boolean = false,
    val explanation: String? = null
)

/**
 * AI Natural Language Query Engine.
 * Resolves conversational, descriptive, or vague queries into exact media titles
 * using Google Gemini or OpenRouter semantic LLMs, with instant local heuristic fallback.
 */
class AiQueryEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun resolveQuery(
        rawQuery: String,
        geminiApiKey: String? = null,
        openRouterApiKey: String? = null
    ): AiQueryResult = withContext(Dispatchers.IO) {
        val trimmed = rawQuery.trim()
        if (trimmed.length < 3) return@withContext AiQueryResult(resolvedTitle = trimmed)

        // 1. Try Gemini API first if configured
        if (!geminiApiKey.isNullOrBlank()) {
            val geminiResult = resolveWithGemini(trimmed, geminiApiKey)
            if (geminiResult != null) return@withContext geminiResult
        }

        // 2. Try OpenRouter API if configured
        if (!openRouterApiKey.isNullOrBlank()) {
            val openRouterResult = resolveWithOpenRouter(trimmed, openRouterApiKey)
            if (openRouterResult != null) return@withContext openRouterResult
        }

        // 3. Smart Local Heuristic Fallback
        resolveWithLocalHeuristics(trimmed)
    }

    private suspend fun resolveWithGemini(query: String, apiKey: String): AiQueryResult? =
        NetworkPacer.withGeneralPacing {
            try {
                val prompt = """
                    You are a media identification expert for Anime, Manga, Light Novels, TV Shows, Movies, Games, and Visual Novels.
                    User query: "$query"
                    Identify the exact canonical title and medium the user is searching for.
                    Return ONLY a JSON object with keys:
                    "title": (string, official English or Romaji title),
                    "mediaType": (one of "ANIME", "MANGA", "NOVEL", "SHOW", "MOVIE", "GAME", "VISUAL_NOVEL"),
                    "explanation": (one short sentence explaining what was identified)
                """.trimIndent()

                val payload = buildJsonObject {
                    put("contents", buildJsonArray {
                        add(buildJsonObject {
                            put("parts", buildJsonArray {
                                add(buildJsonObject {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                }

                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withGeneralPacing null
                    val body = response.body?.string() ?: return@withGeneralPacing null
                    val root = json.parseToJsonElement(body).jsonObject
                    val candidates = root["candidates"]?.jsonArray ?: return@withGeneralPacing null
                    val candidate = candidates.firstOrNull()?.jsonObject ?: return@withGeneralPacing null
                    val text = candidate["content"]?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: return@withGeneralPacing null

                    parseAiResponseJson(text)
                }
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun resolveWithOpenRouter(query: String, apiKey: String): AiQueryResult? =
        NetworkPacer.withGeneralPacing {
            try {
                val prompt = """
                    User query: "$query".
                    Identify the canonical media title and category.
                    Return ONLY valid JSON:
                    {"title": "canonical title", "mediaType": "ANIME|MANGA|NOVEL|SHOW|MOVIE|GAME|VISUAL_NOVEL", "explanation": "short reason"}
                """.trimIndent()

                val payload = buildJsonObject {
                    put("model", "google/gemini-2.0-flash-001")
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }

                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withGeneralPacing null
                    val body = response.body?.string() ?: return@withGeneralPacing null
                    val root = json.parseToJsonElement(body).jsonObject
                    val choices = root["choices"]?.jsonArray ?: return@withGeneralPacing null
                    val content = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.content ?: return@withGeneralPacing null

                    parseAiResponseJson(content)
                }
            } catch (_: Exception) {
                null
            }
        }

    private fun parseAiResponseJson(rawText: String): AiQueryResult? {
        return try {
            // Extract json block if wrapped in markdown code fence
            val cleaned = rawText
                .replace(Regex("^```json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^```\\s*"), "")
                .replace(Regex("```$"), "")
                .trim()

            val obj = json.parseToJsonElement(cleaned).jsonObject
            val title = obj["title"]?.jsonPrimitive?.content ?: return null
            val typeStr = obj["mediaType"]?.jsonPrimitive?.content?.uppercase()
            val detectedType = typeStr?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
            val explanation = obj["explanation"]?.jsonPrimitive?.content

            AiQueryResult(
                resolvedTitle = title,
                detectedType = detectedType,
                isAiResolved = true,
                explanation = explanation
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveWithLocalHeuristics(query: String): AiQueryResult {
        var cleaned = query.trim()
        var detectedType: MediaType? = null

        val lower = query.lowercase()
        when {
            "anime" in lower -> detectedType = MediaType.ANIME
            "manga" in lower || "manhwa" in lower || "manhua" in lower -> detectedType = MediaType.MANGA
            "novel" in lower || "light novel" in lower || "web novel" in lower -> detectedType = MediaType.NOVEL
            "show" in lower || "series" in lower || "tv" in lower -> detectedType = MediaType.SHOW
            "movie" in lower || "film" in lower -> detectedType = MediaType.MOVIE
            "game" in lower || "rpg" in lower -> detectedType = MediaType.GAME
            "visual novel" in lower || "vn" in lower -> detectedType = MediaType.VISUAL_NOVEL
        }

        // Strip conversational prefix stop-phrases
        val prefixes = listOf(
            "that anime where", "the anime where", "anime with", "anime about",
            "that manga where", "the manga where", "manga about", "manga with",
            "that novel where", "the novel about", "novel with", "novel about",
            "that show where", "the show about", "show with", "tv show about",
            "that movie where", "the movie about", "movie with",
            "that game where", "the game where", "game about", "game with",
            "that visual novel where", "vn about", "visual novel with",
            "find me", "search for", "look up", "what is the name of", "what's the name of"
        )

        for (prefix in prefixes) {
            if (cleaned.lowercase().startsWith(prefix)) {
                cleaned = cleaned.substring(prefix.length).trim()
                break
            }
        }

        return AiQueryResult(
            resolvedTitle = if (cleaned.isNotBlank()) cleaned else query,
            detectedType = detectedType,
            isAiResolved = false
        )
    }
}
