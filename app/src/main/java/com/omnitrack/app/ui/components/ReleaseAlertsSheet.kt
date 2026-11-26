package com.omnitrack.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.alerts.ReleaseAlertDispatcher
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.preferences.ReleaseAlertSettings
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseAlertsSheet(
    preferenceManager: PreferenceManager,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val saved by preferenceManager.releaseAlertSettings.collectAsState(initial = ReleaseAlertSettings())

    var enabled by remember(saved.enabled) { mutableStateOf(saved.enabled) }
    var discordUrl by remember(saved.discordWebhookUrl) { mutableStateOf(saved.discordWebhookUrl) }
    var telegramToken by remember(saved.telegramBotToken) { mutableStateOf(saved.telegramBotToken) }
    var telegramChatId by remember(saved.telegramChatId) { mutableStateOf(saved.telegramChatId) }
    var isTesting by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RELEASE ALERTS",
                        color = AccentGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Discord & Telegram Notifications",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Enable Background Alerts",
                        color = TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Automatically checks every 6 hours for upcoming episodes, chapters & releases in your library",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AmoledBlack,
                        checkedTrackColor = AccentGold
                    )
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

            Text(
                text = "DISCORD WEBHOOK",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            OutlinedTextField(
                value = discordUrl,
                onValueChange = { discordUrl = it },
                placeholder = { Text("https://discord.com/api/webhooks/...", color = TextMuted, fontSize = 12.5.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGold,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "TELEGRAM BOT",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            OutlinedTextField(
                value = telegramToken,
                onValueChange = { telegramToken = it },
                label = { Text("Bot Token", fontSize = 12.sp) },
                placeholder = { Text("123456:ABC-DEF...", color = TextMuted, fontSize = 12.5.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGold,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = telegramChatId,
                onValueChange = { telegramChatId = it },
                label = { Text("Chat ID", fontSize = 12.sp) },
                placeholder = { Text("Your user or channel chat ID", color = TextMuted, fontSize = 12.5.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGold,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            testStatus?.let { (ok, msg) ->
                Text(
                    text = msg,
                    color = if (ok) AccentJade else Color(0xFFEF4444),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (!isTesting) {
                            isTesting = true
                            testStatus = null
                            scope.launch {
                                val res = ReleaseAlertDispatcher.sendTestAlert(
                                    saved.copy(
                                        enabled = enabled,
                                        discordWebhookUrl = discordUrl.trim(),
                                        telegramBotToken = telegramToken.trim(),
                                        telegramChatId = telegramChatId.trim()
                                    )
                                )
                                testStatus = res.fold(
                                    onSuccess = { true to it },
                                    onFailure = { false to (it.message ?: "Test ping failed") }
                                )
                                isTesting = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, GlassBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(color = AccentGold, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Test Ping", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            preferenceManager.updateReleaseAlertSettings(
                                saved.copy(
                                    enabled = enabled,
                                    discordWebhookUrl = discordUrl.trim(),
                                    telegramBotToken = telegramToken.trim(),
                                    telegramChatId = telegramChatId.trim()
                                )
                            )
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = Color.Black),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text("Save Alerts", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
