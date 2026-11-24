package com.omnitrack.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.omnitrack.app.data.model.extractFirstFanficUrl
import com.omnitrack.app.data.preferences.PerformanceSettings
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.sync.TrackerAccountVerifier
import com.omnitrack.app.util.AppLogger
import com.omnitrack.app.ui.components.LocalPerformanceSettings
import com.omnitrack.app.ui.navigation.OmniTrackNavigation
import com.omnitrack.app.ui.theme.OmniThemeSettings
import com.omnitrack.app.ui.theme.OmniThemeState
import com.omnitrack.app.ui.theme.OmniTrackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object SharedFanficUrlBus {
    private val _pendingUrl = MutableStateFlow<String?>(null)
    val pendingUrl: StateFlow<String?> = _pendingUrl.asStateFlow()

    fun emit(url: String) {
        if (url.isNotBlank()) {
            _pendingUrl.value = url
        }
    }

    fun consume() {
        _pendingUrl.value = null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as OmniTrackApplication
        handleAuthDeepLink(intent?.data, app)
        handleShareIntent(intent)

        setContent {
            val themeSettings by app.preferenceManager.themeSettings.collectAsState(initial = OmniThemeSettings())
            val perfSettings by app.preferenceManager.performanceSettings.collectAsState(initial = PerformanceSettings())
            LaunchedEffect(themeSettings) {
                OmniThemeState.apply(themeSettings)
            }
            OmniTrackTheme {
                CompositionLocalProvider(LocalPerformanceSettings provides perfSettings) {
                    val navController = rememberNavController()

                    OmniTrackNavigation(
                        navController = navController,
                        repository = app.repository
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as OmniTrackApplication
        handleAuthDeepLink(intent.data, app)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val mime = intent.type?.lowercase() ?: return
        if (!mime.startsWith("text/")) return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = extractFirstFanficUrl(sharedText) ?: return
        SharedFanficUrlBus.emit(url)
    }

    private fun handleAuthDeepLink(data: Uri?, app: OmniTrackApplication) {
        if (data == null) return
        val validSchemes = setOf("omnitrack", "aniyomi", "chimahon", "mihon", "app.mihon", "nuvio", "komikku")
        val scheme = data.scheme?.lowercase() ?: return
        if (scheme !in validSchemes) return

        val host = data.host?.lowercase() ?: ""
        val uriStr = data.toString().lowercase()
        val service: String = when {
            host.endsWith("-auth") -> host.removeSuffix("-auth")
            host == "auth" || host == "oauth" -> data.pathSegments.firstOrNull()?.lowercase() ?: ""
            uriStr.contains("anilist") -> "anilist"
            uriStr.contains("simkl") -> "simkl"
            uriStr.contains("bangumi") || uriStr.contains("bgm") -> "bangumi"
            uriStr.contains("comick") -> "comick"
            uriStr.contains("myanimelist") || uriStr.contains("mal") -> "mal"
            uriStr.contains("trakt") -> "trakt"
            uriStr.contains("vndb") -> "vndb"
            uriStr.contains("tmdb") -> "tmdb"
            else -> ""
        }

        if (service.isBlank()) return
        val tokenManager = app.trackerTokenManager
        val effectiveRedirectUri = when {
            scheme == "komikku" -> "komikku://bangumi-auth"
            scheme == "app.mihon" -> "app.mihon://comick-auth"
            scheme == "chimahon" || host == "myanimelist-auth" -> "chimahon://myanimelist-auth"
            scheme == "nuvio" -> "nuvio://auth/$service"
            else -> "omnitrack://auth/$service"
        }

        var token: String? = null
        var fragmentRefresh: String? = null
        val fragment = data.fragment
        if (!fragment.isNullOrBlank()) {
            val params = fragment.split("&").associate {
                val parts = it.split("=")
                if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
            }
            token = params["access_token"]
            fragmentRefresh = params["refresh_token"]
        }

        val code = data.getQueryParameter("code")
        val oauthState = data.getQueryParameter("state")
        val tmdbRequestToken = data.getQueryParameter("request_token")
        val tmdbApproved = data.getQueryParameter("approved")
        val queryRefresh = data.getQueryParameter("refresh_token")
        if (token == null && code == null) {
            token = data.getQueryParameter("access_token")
        }

        lifecycleScope.launch(Dispatchers.IO) {
            // TMDb v3 User Session callback (omnitrack://auth/tmdb?request_token=...&approved=true)
            if (service == "tmdb" && !tmdbRequestToken.isNullOrBlank()) {
                if (tmdbApproved == "false") {
                    tokenManager.savePendingTmdbRequestToken(null)
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "TMDb authorization declined", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val currentTokens = tokenManager.tokens.firstOrNull()
                val apiKeyOrBearer = currentTokens?.tmdbToken?.takeIf { it.isNotBlank() }
                    ?: PreferenceManager.cachedTmdbApiKey.takeIf { it.isNotBlank() }
                val effectiveReqTok = tmdbRequestToken.ifBlank { tokenManager.getPendingTmdbRequestToken().orEmpty() }
                if (!apiKeyOrBearer.isNullOrBlank() && effectiveReqTok.isNotBlank()) {
                    val sessionPair = TrackerAccountVerifier.exchangeTmdbSession(apiKeyOrBearer, effectiveReqTok)
                    tokenManager.savePendingTmdbRequestToken(null)
                    if (sessionPair != null) {
                        tokenManager.saveTmdbSession(sessionPair.first, sessionPair.second?.toString())
                        launch(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "TMDb write sync activated!", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                }
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to activate TMDb session", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // OAuth2 CSRF state verification for authorization-code redirects
            val authCodeServices = setOf("mal", "myanimelist", "trakt", "bangumi", "bgm", "comick")
            if (!code.isNullOrBlank() && service in authCodeServices) {
                val expectedState = TrackerAccountVerifier.pendingOAuthState?.takeIf { it.isNotBlank() }
                    ?: tokenManager.getOAuthState()
                TrackerAccountVerifier.pendingOAuthState = null
                tokenManager.saveOAuthState(null)
                if (!expectedState.isNullOrBlank() && oauthState != expectedState) {
                    AppLogger.w("OAuth", "Rejected callback for $service: state mismatch")
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "OAuth verification failed — please sign in again", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
            }

            val client = OkHttpClient()
            var finalAccessToken: String? = token
            var finalRefreshToken: String? = fragmentRefresh ?: queryRefresh

            if (finalAccessToken.isNullOrBlank() && !code.isNullOrBlank()) {
                when (service) {
                    "simkl" -> {
                        try {
                            val payload = """
                                {
                                    "code": "$code",
                                    "client_id": "${TrackerAccountVerifier.SIMKL_CLIENT_ID}",
                                    "client_secret": "${TrackerAccountVerifier.SIMKL_CLIENT_SECRET}",
                                    "redirect_uri": "$effectiveRedirectUri",
                                    "grant_type": "authorization_code"
                                }
                            """.trimIndent()
                            val req = Request.Builder()
                                .url("https://api.simkl.com/oauth/token")
                                .post(payload.toRequestBody("application/json".toMediaType()))
                                .build()
                            client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val body = resp.body?.string().orEmpty()
                                    val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                    finalAccessToken = parsed["access_token"]?.jsonPrimitive?.contentOrNull
                                    finalRefreshToken = parsed["refresh_token"]?.jsonPrimitive?.contentOrNull ?: finalRefreshToken
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    "bangumi", "bgm" -> {
                        try {
                            val form = FormBody.Builder()
                                .add("grant_type", "authorization_code")
                                .add("client_id", TrackerAccountVerifier.BANGUMI_CLIENT_ID)
                                .add("client_secret", TrackerAccountVerifier.BANGUMI_CLIENT_SECRET)
                                .add("code", code)
                                .add("redirect_uri", "komikku://bangumi-auth")
                                .build()
                            val req = Request.Builder()
                                .url("https://bgm.tv/oauth/access_token")
                                .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                                .post(form)
                                .build()
                            client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val body = resp.body?.string().orEmpty()
                                    val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                    finalAccessToken = parsed["access_token"]?.jsonPrimitive?.contentOrNull
                                    finalRefreshToken = parsed["refresh_token"]?.jsonPrimitive?.contentOrNull ?: finalRefreshToken
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    "comick" -> {
                        try {
                            val verifier = TrackerAccountVerifier.pendingComickVerifier?.takeIf { it.isNotBlank() }
                                ?: tokenManager.getComickVerifier().orEmpty()
                            val form = FormBody.Builder()
                                .add("grant_type", "authorization_code")
                                .add("client_id", TrackerAccountVerifier.COMICK_CLIENT_ID)
                                .add("code", code)
                                .add("redirect_uri", "app.mihon://comick-auth")
                                .add("code_verifier", verifier)
                                .add("resource", "https://api.comick.dev/integrations/v1")
                                .build()
                            val req = Request.Builder()
                                .url("https://comick.dev/api/auth/oauth2/token")
                                .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                                .post(form)
                                .build()
                            client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val body = resp.body?.string().orEmpty()
                                    val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                    finalAccessToken = parsed["access_token"]?.jsonPrimitive?.contentOrNull
                                    finalRefreshToken = parsed["refresh_token"]?.jsonPrimitive?.contentOrNull ?: finalRefreshToken
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    "mal", "myanimelist" -> {
                        try {
                            val pref = (application as OmniTrackApplication).preferenceManager
                            val customMal = pref.customMalClientId.firstOrNull()?.takeIf { it.isNotBlank() && it != "2db7880d919b9231be3031ec80e77f15" }
                            val effectiveClientId = customMal ?: TrackerAccountVerifier.MAL_CLIENT_ID
                            val verifier = TrackerAccountVerifier.pendingMalVerifier?.takeIf { it.isNotBlank() }
                                ?: tokenManager.getMalVerifier().orEmpty()
                            val malRedirectUri = if (customMal != null) effectiveRedirectUri else "chimahon://myanimelist-auth"

                            AppLogger.i("OAuth", "Exchanging MAL auth code (redirect=$malRedirectUri)")

                            val form = FormBody.Builder()
                                .add("grant_type", "authorization_code")
                                .add("client_id", effectiveClientId)
                                .add("code", code)
                                .add("code_verifier", verifier)
                                .add("redirect_uri", malRedirectUri)
                                .build()

                            val req = Request.Builder()
                                .url("https://myanimelist.net/v1/oauth2/token")
                                .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                                .post(form)
                                .build()

                            client.newCall(req).execute().use { resp ->
                                val body = resp.body?.string().orEmpty()
                                AppLogger.i("OAuth", "MAL token exchange HTTP ${resp.code}")
                                if (resp.isSuccessful) {
                                    val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                    finalAccessToken = parsed["access_token"]?.jsonPrimitive?.contentOrNull
                                    finalRefreshToken = parsed["refresh_token"]?.jsonPrimitive?.contentOrNull ?: finalRefreshToken
                                } else {
                                    AppLogger.w("OAuth", "MAL token exchange failed with HTTP ${resp.code}")
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.e("OAuth", "Exception during MAL token exchange", e)
                        }
                    }
                    "trakt" -> {
                        try {
                            val pref = (application as OmniTrackApplication).preferenceManager
                            val customTrakt = pref.customTraktClientId.firstOrNull()?.takeIf { it.isNotBlank() }
                            val customSecret = pref.customTraktClientSecret.firstOrNull()?.takeIf { it.isNotBlank() }
                            val effectiveClientId = customTrakt ?: TrackerAccountVerifier.TRAKT_CLIENT_ID
                            val effectiveSecret = customSecret ?: TrackerAccountVerifier.TRAKT_CLIENT_SECRET
                            val verifier = TrackerAccountVerifier.pendingTraktVerifier?.takeIf { it.isNotBlank() }
                                ?: tokenManager.getTraktVerifier().orEmpty()
                            val payload = buildJsonObject {
                                put("code", code)
                                put("client_id", effectiveClientId)
                                if (effectiveSecret.isNotBlank()) {
                                    put("client_secret", effectiveSecret)
                                }
                                if (verifier.isNotBlank()) {
                                    put("code_verifier", verifier)
                                }
                                put("redirect_uri", effectiveRedirectUri)
                                put("grant_type", "authorization_code")
                            }.toString()
                            val req = Request.Builder()
                                .url("https://api.trakt.tv/oauth/token")
                                .post(payload.toRequestBody("application/json".toMediaType()))
                                .build()
                            client.newCall(req).execute().use { resp ->
                                val body = resp.body?.string().orEmpty()
                                if (resp.isSuccessful) {
                                    val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(body) as? JsonObject
                                    finalAccessToken = (parsed?.get("access_token") as? JsonPrimitive)
                                        ?.takeIf { it !is JsonNull }
                                        ?.contentOrNull
                                    finalRefreshToken = (parsed?.get("refresh_token") as? JsonPrimitive)
                                        ?.takeIf { it !is JsonNull }
                                        ?.contentOrNull
                                        ?: finalRefreshToken
                                } else {
                                    AppLogger.w("OAuth", "Trakt token exchange failed: HTTP ${resp.code}")
                                }
                            }
                            TrackerAccountVerifier.pendingTraktVerifier = null
                            tokenManager.saveTraktVerifier(null)
                        } catch (_: Exception) {}
                    }
                }
            }

            if (!finalAccessToken.isNullOrBlank()) {
                val tokenToSave = finalAccessToken!!
                when (service) {
                    "anilist" -> tokenManager.saveAnilistToken(tokenToSave)
                    "mal", "myanimelist" -> tokenManager.saveMalTokens(tokenToSave, finalRefreshToken)
                    "trakt" -> tokenManager.saveTraktTokens(tokenToSave, finalRefreshToken)
                    "simkl" -> tokenManager.saveSimklTokens(tokenToSave, finalRefreshToken)
                    "comick" -> tokenManager.saveComickTokens(tokenToSave, finalRefreshToken)
                    "vndb" -> tokenManager.saveVndbToken(tokenToSave)
                    "bangumi", "bgm" -> tokenManager.saveBangumiTokens(tokenToSave, finalRefreshToken)
                    "tmdb" -> tokenManager.saveTmdbToken(tokenToSave)
                    "igdb" -> tokenManager.saveIgdbToken(tokenToSave)
                }

                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "${service.uppercase()} connected successfully!", Toast.LENGTH_SHORT).show()
                }
            } else {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to complete ${service.uppercase()} login", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
