package com.omnitrack.app.data.alerts

import com.omnitrack.app.data.enrichment.CalendarReleaseItem
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.data.preferences.ReleaseAlertSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object ReleaseAlertDispatcher {

    suspend fun dispatchReleaseAlerts(
        settings: ReleaseAlertSettings,
        releases: List<CalendarReleaseItem>
    ): Int = withContext(Dispatchers.IO) {
        if (releases.isEmpty()) return@withContext 0
        var sentCount = 0
        for (rel in releases) {
            val timeStr = SimpleDateFormat("MMM d • h:mm a", Locale.US)
                .format(Date(rel.airingAtEpochSec * 1000L))
            val unitLabel = when {
                !rel.chapterLabel.isNullOrBlank() -> rel.chapterLabel
                rel.episodeNumber != null && rel.seasonNumber != null -> "S${rel.seasonNumber} · Ep ${rel.episodeNumber}"
                rel.episodeNumber != null -> "Episode ${rel.episodeNumber}"
                else -> rel.formatLabel
            }
            val discordOk = if (settings.discordWebhookUrl.isNotBlank()) {
                sendDiscordMessage(
                    webhookUrl = settings.discordWebhookUrl,
                    title = rel.title,
                    subtitle = "$unitLabel • ${rel.mediaType.displayName}",
                    footer = "Drops $timeStr",
                    thumbnailUrl = rel.coverUrl
                ).isSuccess
            } else false

            val telegramOk = if (settings.telegramBotToken.isNotBlank() && settings.telegramChatId.isNotBlank()) {
                val msg = buildString {
                    append("<b>New Release Drop</b>\n")
                    append("<b>${escapeHtml(rel.title)}</b>\n")
                    append("${escapeHtml(unitLabel)} · ${escapeHtml(rel.mediaType.displayName)}\n")
                    append("Time: $timeStr")
                }
                sendTelegramMessage(settings.telegramBotToken, settings.telegramChatId, msg).isSuccess
            } else false

            if (discordOk || telegramOk) sentCount++
        }
        sentCount
    }

    suspend fun sendTestAlert(settings: ReleaseAlertSettings): Result<String> = withContext(Dispatchers.IO) {
        val hasDiscord = settings.discordWebhookUrl.isNotBlank()
        val hasTelegram = settings.telegramBotToken.isNotBlank() && settings.telegramChatId.isNotBlank()
        if (!hasDiscord && !hasTelegram) {
            return@withContext Result.failure(IllegalArgumentException("Enter a Discord webhook URL or Telegram bot token & chat ID first."))
        }
        val notes = mutableListOf<String>()
        var anySuccess = false
        if (hasDiscord) {
            val res = sendDiscordMessage(
                webhookUrl = settings.discordWebhookUrl,
                title = "OmniTrack Release Alerts Connected",
                subtitle = "Test ping from OmniTrack background scheduler",
                footer = "Checks library releases every 6 hours",
                thumbnailUrl = null
            )
            if (res.isSuccess) {
                anySuccess = true
                notes += "Discord OK"
            } else {
                notes += "Discord failed: ${res.exceptionOrNull()?.message}"
            }
        }
        if (hasTelegram) {
            val res = sendTelegramMessage(
                botToken = settings.telegramBotToken,
                chatId = settings.telegramChatId,
                htmlText = "<b>OmniTrack Release Alerts Connected</b>\nYour library release alerts are active and working."
            )
            if (res.isSuccess) {
                anySuccess = true
                notes += "Telegram OK"
            } else {
                notes += "Telegram failed: ${res.exceptionOrNull()?.message}"
            }
        }
        if (anySuccess) Result.success(notes.joinToString(" • "))
        else Result.failure(IllegalStateException(notes.joinToString(" • ")))
    }

    private fun sendDiscordMessage(
        webhookUrl: String,
        title: String,
        subtitle: String,
        footer: String,
        thumbnailUrl: String?
    ): Result<Unit> = runCatching {
        val embed = JSONObject().apply {
            put("title", title)
            put("description", subtitle)
            put("color", 14262887) // OmniTrack AccentGold tint
            put("footer", JSONObject().put("text", footer))
            if (!thumbnailUrl.isNullOrBlank()) {
                put("thumbnail", JSONObject().put("url", thumbnailUrl))
            }
        }
        val payload = JSONObject().apply {
            put("username", "OmniTrack Releases")
            put("embeds", JSONArray().put(embed))
        }
        val req = Request.Builder()
            .url(webhookUrl.trim())
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        NetworkClient.sharedClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}")
            }
        }
    }

    private fun sendTelegramMessage(
        botToken: String,
        chatId: String,
        htmlText: String
    ): Result<Unit> = runCatching {
        val cleanToken = botToken.trim()
        val cleanChat = chatId.trim()
        val payload = JSONObject().apply {
            put("chat_id", cleanChat)
            put("text", htmlText)
            put("parse_mode", "HTML")
            put("disable_web_page_preview", false)
        }
        val req = Request.Builder()
            .url("https://api.telegram.org/bot$cleanToken/sendMessage")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        NetworkClient.sharedClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}")
            }
        }
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
