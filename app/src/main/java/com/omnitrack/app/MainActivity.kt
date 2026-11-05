package com.omnitrack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.omnitrack.app.ui.navigation.OmniTrackNavigation
import com.omnitrack.app.ui.theme.OmniTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as OmniTrackApplication

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
}
