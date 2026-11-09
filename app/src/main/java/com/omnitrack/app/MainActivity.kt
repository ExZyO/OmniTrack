package com.omnitrack.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.omnitrack.app.data.sync.TrackerAccountVerifier
import com.omnitrack.app.ui.navigation.OmniTrackNavigation
import com.omnitrack.app.ui.theme.OmniTrackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as OmniTrackApplication
        handleAuthDeepLink(intent?.data, app)

        setContent {
            OmniTrackTheme {
                val navController = rememberNavController()

                OmniTrackNavigation(
                    navController = navController,
                    repository = app.repository
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val app = application as OmniTrackApplication
        handleAuthDeepLink(intent.data, app)
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
        val fragment = data.fragment
        if (!fragment.isNullOrBlank()) {
            val params = fragment.split("&").associate {
                val parts = it.split("=")
                if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
            }
            token = params["access_token"]
        }

        val code = data.getQueryParameter("code")
        if (token == null && code == null) {
            token = data.getQueryParameter("access_token")
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            var finalAccessToken: String? = token

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
                                    finalAccessToken = parsed["access_token"]?.jsonPrimitive?.content
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
                                    finalAccessToken = parsed["access_token"]?.jsonPrimitive?.content
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
                                    finalAccessToken = parsed["access_token"]?.jsonPrimitive?.content
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
                            
                            android.util.Log.d("OmniTrack-MAL", "Starting MAL token exchange: clientId=$effectiveClientId, verifierLen=${verifier.length}, redirect=$malRedirectUri, codeLen=${code.length}")
                            
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
                                android.util.Log.d("OmniTrack-MAL", "MAL token response code=${resp.code}, body=$body")
                                if (resp.isSuccessful) {
                                    val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                    finalAccessToken = parsed["access_token"]?.jsonPrimitive?.content
                                } else {
                                    android.util.Log.e("OmniTrack-MAL", "MAL token exchange failed with code ${resp.code}: $body")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("OmniTrack-MAL", "Exception during MAL token exchange", e)
                        }
                    }
                    "trakt" -> {
                        try {
                            val pref = (application as OmniTrackApplication).preferenceManager
                            val customTrakt = pref.customTraktClientId.firstOrNull()?.takeIf { it.isNotBlank() }
                            val customSecret = pref.customTraktClientSecret.firstOrNull()?.takeIf { it.isNotBlank() }
                            val effectiveClientId = customTrakt ?: TrackerAccountVerifier.TRAKT_CLIENT_ID
                            val effectiveSecret = customSecret ?: TrackerAccountVerifier.TRAKT_CLIENT_SECRET
                            val secretLine = if (effectiveSecret.isNotBlank()) "\"client_secret\": \"$effectiveSecret\"," else ""
                            val payload = """
                                {
                                    "code": "$code",
                                    "client_id": "$effectiveClientId",
                                    $secretLine
                                    "redirect_uri": "$effectiveRedirectUri",
                                    "grant_type": "authorization_code"
                                }
                            """.trimIndent()
                            val req = Request.Builder()
                                .url("https://api.trakt.tv/oauth/token")
                                .post(payload.toRequestBody("application/json".toMediaType()))
                                .build()
                            client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val body = resp.body?.string().orEmpty()
                                    val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                    finalAccessToken = parsed["access_token"]?.jsonPrimitive?.content
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            if (!finalAccessToken.isNullOrBlank()) {
                val tokenToSave = finalAccessToken!!
                when (service) {
                    "anilist" -> tokenManager.saveAnilistToken(tokenToSave)
                    "mal", "myanimelist" -> tokenManager.saveMalToken(tokenToSave)
                    "trakt" -> tokenManager.saveTraktToken(tokenToSave)
                    "simkl" -> tokenManager.saveSimklToken(tokenToSave)
                    "comick" -> tokenManager.saveComickToken(tokenToSave)
                    "vndb" -> tokenManager.saveVndbToken(tokenToSave)
                    "bangumi", "bgm" -> tokenManager.saveBangumiToken(tokenToSave)
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
