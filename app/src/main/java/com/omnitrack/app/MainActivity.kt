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
import com.omnitrack.app.ui.navigation.OmniTrackNavigation
import com.omnitrack.app.ui.theme.OmniTrackTheme
import kotlinx.coroutines.launch

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
        val validSchemes = setOf("omnitrack", "aniyomi", "chimahon", "mihon", "app.mihon")
        val scheme = data.scheme?.lowercase() ?: return
        if (scheme !in validSchemes) return

        val host = data.host?.lowercase() ?: ""
        val service: String = when {
            host.endsWith("-auth") -> host.removeSuffix("-auth")
            host == "auth" || host == "oauth" -> data.pathSegments.firstOrNull()?.lowercase() ?: ""
            else -> ""
        }

        if (service.isBlank()) return
        val tokenManager = app.trackerTokenManager

        // Parse token from URL fragment (e.g. #access_token=... for AniList implicit grant)
        var token: String? = null
        val fragment = data.fragment
        if (!fragment.isNullOrBlank()) {
            val params = fragment.split("&").associate {
                val parts = it.split("=")
                if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
            }
            token = params["access_token"]
        }

        // If not in fragment, check query parameters (?code=... or ?access_token=...)
        if (token == null) {
            token = data.getQueryParameter("access_token") ?: data.getQueryParameter("code")
        }

        if (!token.isNullOrBlank()) {
            lifecycleScope.launch {
                when (service) {
                    "anilist" -> {
                        tokenManager.saveAnilistToken(token)
                        Toast.makeText(this@MainActivity, "AniList connected successfully!", Toast.LENGTH_SHORT).show()
                    }
                    "mal", "myanimelist" -> {
                        tokenManager.saveMalToken(token)
                        Toast.makeText(this@MainActivity, "MyAnimeList connected successfully!", Toast.LENGTH_SHORT).show()
                    }
                    "trakt" -> {
                        tokenManager.saveTraktToken(token)
                        Toast.makeText(this@MainActivity, "Trakt.tv connected successfully!", Toast.LENGTH_SHORT).show()
                    }
                    "simkl" -> {
                        tokenManager.saveSimklToken(token)
                        Toast.makeText(this@MainActivity, "SIMKL connected successfully!", Toast.LENGTH_SHORT).show()
                    }
                    "comick" -> {
                        tokenManager.saveComickToken(token)
                        Toast.makeText(this@MainActivity, "Comick connected successfully!", Toast.LENGTH_SHORT).show()
                    }
                    "vndb" -> {
                        tokenManager.saveVndbToken(token)
                        Toast.makeText(this@MainActivity, "VNDB connected successfully!", Toast.LENGTH_SHORT).show()
                    }
                    "bangumi", "bgm" -> {
                        tokenManager.saveBangumiToken(token)
                        Toast.makeText(this@MainActivity, "Bangumi connected successfully!", Toast.LENGTH_SHORT).show()
                    }
                    "tmdb" -> {
                        tokenManager.saveTmdbToken(token)
                        Toast.makeText(this@MainActivity, "TMDb connected successfully!", Toast.LENGTH_SHORT).show()
                    }
                    "igdb" -> {
                        tokenManager.saveIgdbToken(token)
                        Toast.makeText(this@MainActivity, "IGDB connected successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
