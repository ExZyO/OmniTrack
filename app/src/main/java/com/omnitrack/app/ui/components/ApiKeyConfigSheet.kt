package com.omnitrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyConfigSheet(
    preferenceManager: PreferenceManager,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val currentGemini by preferenceManager.geminiApiKey.collectAsState(initial = "")
    val currentOpenRouter by preferenceManager.openRouterApiKey.collectAsState(initial = "")
    val currentTmdb by preferenceManager.tmdbApiKey.collectAsState(initial = "")
    val currentFanart by preferenceManager.fanartApiKey.collectAsState(initial = "")
    val currentMdblist by preferenceManager.mdblistApiKey.collectAsState(initial = "")
    val aiSearchEnabled by preferenceManager.aiSemanticSearchEnabled.collectAsState(initial = false)

    var geminiKey by remember(currentGemini) { mutableStateOf(currentGemini) }
    var openRouterKey by remember(currentOpenRouter) { mutableStateOf(currentOpenRouter) }
    var tmdbKey by remember(currentTmdb) { mutableStateOf(currentTmdb) }
    var fanartKey by remember(currentFanart) { mutableStateOf(currentFanart) }
    var mdblistKey by remember(currentMdblist) { mutableStateOf(currentMdblist) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "External APIs & AI Engine",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Keys are securely stored in private local DataStore",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            // AI Semantic Search Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentArctic,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "AI Natural Language Search",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Resolve conversational descriptions into canonical titles",
                            fontSize = 11.5.sp,
                            color = TextMuted
                        )
                    }
                }
                Switch(
                    checked = aiSearchEnabled,
                    onCheckedChange = { checked ->
                        scope.launch { preferenceManager.updateAiSemanticSearchEnabled(checked) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentArctic,
                        checkedTrackColor = AccentArctic.copy(alpha = 0.35f)
                    )
                )
            }

            // Gemini API Key
            ApiKeyField(
                label = "Google Gemini API Key",
                placeholder = "AIzaSy...",
                value = geminiKey,
                onValueChange = { geminiKey = it },
                accentColor = AccentArctic
            )

            // OpenRouter API Key
            ApiKeyField(
                label = "OpenRouter API Key (Alternative AI)",
                placeholder = "sk-or-v1-...",
                value = openRouterKey,
                onValueChange = { openRouterKey = it },
                accentColor = AccentArctic
            )

            // TMDb API Key
            ApiKeyField(
                label = "TMDb API Key (v3 auth)",
                placeholder = "32-character key for Movies & Shows",
                value = tmdbKey,
                onValueChange = { tmdbKey = it },
                accentColor = AccentGold
            )

            // Fanart.tv API Key
            ApiKeyField(
                label = "Fanart.tv Project API Key",
                placeholder = "Clearart transparent title logos",
                value = fanartKey,
                onValueChange = { fanartKey = it },
                accentColor = AccentJade
            )

            // MdbList API Key
            ApiKeyField(
                label = "MdbList API Key",
                placeholder = "IMDb, Rotten Tomatoes & Metacritic scores",
                value = mdblistKey,
                onValueChange = { mdblistKey = it },
                accentColor = Color(0xFFEF4444)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Save Button
            Button(
                onClick = {
                    scope.launch {
                        preferenceManager.updateGeminiApiKey(geminiKey.trim())
                        preferenceManager.updateOpenRouterApiKey(openRouterKey.trim())
                        preferenceManager.updateTmdbApiKey(tmdbKey.trim())
                        preferenceManager.updateFanartApiKey(fanartKey.trim())
                        preferenceManager.updateMdblistApiKey(mdblistKey.trim())
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentArctic)
            ) {
                Text(
                    text = "Save API Configuration",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmoledBlack
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ApiKeyField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceElevated)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(accentColor),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = TextMuted.copy(alpha = 0.6f), fontSize = 12.5.sp)
                    }
                    innerTextField()
                }
            )
        }
    }
}
