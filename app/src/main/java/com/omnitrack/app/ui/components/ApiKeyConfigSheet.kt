package com.omnitrack.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.network.ApiKeyValidationState
import com.omnitrack.app.data.network.ApiKeyValidator
import com.omnitrack.app.data.preferences.DEFAULT_GEMINI_MODELS
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val currentGeminiModel by preferenceManager.geminiModel.collectAsState(initial = "gemini-2.5-flash")
    val currentCustomGeminiModel by preferenceManager.customGeminiModel.collectAsState(initial = "")
    val currentUseCustomGeminiModel by preferenceManager.useCustomGeminiModel.collectAsState(initial = false)
    val currentOpenRouter by preferenceManager.openRouterApiKey.collectAsState(initial = "")
    val currentTmdb by preferenceManager.tmdbApiKey.collectAsState(initial = "")
    val currentFanart by preferenceManager.fanartApiKey.collectAsState(initial = "")
    val currentSteamGridDb by preferenceManager.steamGridDbApiKey.collectAsState(initial = "")
    val currentIgdbId by preferenceManager.igdbClientId.collectAsState(initial = "")
    val currentIgdbSecret by preferenceManager.igdbClientSecret.collectAsState(initial = "")
    val currentMdblist by preferenceManager.mdblistApiKey.collectAsState(initial = "")
    val currentOmdb by preferenceManager.omdbApiKey.collectAsState(initial = "")
    val currentComicVine by preferenceManager.comicVineApiKey.collectAsState(initial = "")
    val currentMetronUser by preferenceManager.metronUsername.collectAsState(initial = "")
    val currentMetronPass by preferenceManager.metronPassword.collectAsState(initial = "")
    val aiSearchEnabled by preferenceManager.aiSemanticSearchEnabled.collectAsState(initial = false)

    var geminiKey by remember(currentGemini) { mutableStateOf(currentGemini) }
    var openRouterKey by remember(currentOpenRouter) { mutableStateOf(currentOpenRouter) }
    var tmdbKey by remember(currentTmdb) { mutableStateOf(currentTmdb) }
    var fanartKey by remember(currentFanart) { mutableStateOf(currentFanart) }
    var steamGridDbKey by remember(currentSteamGridDb) { mutableStateOf(currentSteamGridDb) }
    var igdbClientId by remember(currentIgdbId) { mutableStateOf(currentIgdbId) }
    var igdbClientSecret by remember(currentIgdbSecret) { mutableStateOf(currentIgdbSecret) }
    var mdblistKey by remember(currentMdblist) { mutableStateOf(currentMdblist) }
    var omdbKey by remember(currentOmdb) { mutableStateOf(currentOmdb) }
    var comicVineKey by remember(currentComicVine) { mutableStateOf(currentComicVine) }
    var metronUsername by remember(currentMetronUser) { mutableStateOf(currentMetronUser) }
    var metronPassword by remember(currentMetronPass) { mutableStateOf(currentMetronPass) }

    val validationStates = remember { mutableStateMapOf<String, ApiKeyValidationState>() }
    var isTestingAll by remember { mutableStateOf(false) }

    fun testSingleKey(id: String, validateBlock: suspend () -> ApiKeyValidationState) {
        scope.launch {
            validationStates[id] = ApiKeyValidationState.Testing
            validationStates[id] = validateBlock()
        }
    }

    fun testAllConfiguredKeys() {
        if (isTestingAll) return
        scope.launch {
            isTestingAll = true
            try {
                coroutineScope {
                    val tasks = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()
                    if (geminiKey.isNotBlank()) tasks += async {
                        validationStates["gemini"] = ApiKeyValidationState.Testing
                        validationStates["gemini"] = ApiKeyValidator.validateGemini(geminiKey)
                    }
                    if (openRouterKey.isNotBlank()) tasks += async {
                        validationStates["openrouter"] = ApiKeyValidationState.Testing
                        validationStates["openrouter"] = ApiKeyValidator.validateOpenRouter(openRouterKey)
                    }
                    if (tmdbKey.isNotBlank()) tasks += async {
                        validationStates["tmdb"] = ApiKeyValidationState.Testing
                        validationStates["tmdb"] = ApiKeyValidator.validateTmdb(tmdbKey)
                    }
                    if (fanartKey.isNotBlank()) tasks += async {
                        validationStates["fanart"] = ApiKeyValidationState.Testing
                        validationStates["fanart"] = ApiKeyValidator.validateFanart(fanartKey)
                    }
                    if (steamGridDbKey.isNotBlank()) tasks += async {
                        validationStates["sgdb"] = ApiKeyValidationState.Testing
                        validationStates["sgdb"] = ApiKeyValidator.validateSteamGridDb(steamGridDbKey)
                    }
                    if (igdbClientId.isNotBlank() || igdbClientSecret.isNotBlank()) tasks += async {
                        validationStates["igdb"] = ApiKeyValidationState.Testing
                        validationStates["igdb"] = ApiKeyValidator.validateIgdb(igdbClientId, igdbClientSecret)
                    }
                    if (mdblistKey.isNotBlank()) tasks += async {
                        validationStates["mdblist"] = ApiKeyValidationState.Testing
                        validationStates["mdblist"] = ApiKeyValidator.validateMdblist(mdblistKey)
                    }
                    if (omdbKey.isNotBlank()) tasks += async {
                        validationStates["omdb"] = ApiKeyValidationState.Testing
                        validationStates["omdb"] = ApiKeyValidator.validateOmdb(omdbKey)
                    }
                    if (comicVineKey.isNotBlank()) tasks += async {
                        validationStates["comicvine"] = ApiKeyValidationState.Testing
                        validationStates["comicvine"] = ApiKeyValidator.validateComicVine(comicVineKey)
                    }
                    if (metronUsername.isNotBlank() || metronPassword.isNotBlank()) tasks += async {
                        validationStates["metron"] = ApiKeyValidationState.Testing
                        validationStates["metron"] = ApiKeyValidator.validateMetron(metronUsername, metronPassword)
                    }
                    tasks.awaitAll()
                }
            } finally {
                isTestingAll = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI & Artwork Boosters",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tap Test to verify keys live against their servers — sign-in happens in Tracker Accounts",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentGold.copy(alpha = 0.16f))
                        .border(0.8.dp, AccentGold.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .clickable(enabled = !isTestingAll) { testAllConfiguredKeys() }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isTestingAll) {
                        CircularProgressIndicator(
                            color = AccentGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = if (isTestingAll) "Testing" else "Test All",
                        color = AccentGold,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Section 1: AI Search
            Text(
                text = "AI SEARCH",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = AccentGold
            )

            // AI Semantic Search Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
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
                        tint = AccentGold,
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
                            text = "Turn vibe descriptions into matching titles in Discover",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }
                Switch(
                    checked = aiSearchEnabled,
                    onCheckedChange = { checked ->
                        scope.launch { preferenceManager.updateAiSemanticSearchEnabled(checked) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AmoledBlack,
                        checkedTrackColor = AccentGold,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurfaceCard
                    )
                )
            }

            ApiKeyField(
                label = "Google Gemini API Key",
                unlockNote = "Unlocks AI search queries and smart natural-language discovery.",
                placeholder = "AIzaSy...",
                value = geminiKey,
                onValueChange = {
                    geminiKey = it
                    validationStates["gemini"] = ApiKeyValidationState.Idle
                },
                validationState = validationStates["gemini"] ?: ApiKeyValidationState.Idle,
                onTest = { testSingleKey("gemini") { ApiKeyValidator.validateGemini(geminiKey) } }
            )

            GeminiModelSelectorSection(
                geminiModel = currentGeminiModel,
                customGeminiModel = currentCustomGeminiModel,
                useCustomGeminiModel = currentUseCustomGeminiModel,
                onSelectPreset = { modelId ->
                    scope.launch {
                        preferenceManager.updateUseCustomGeminiModel(false)
                        preferenceManager.updateGeminiModel(modelId)
                    }
                },
                onSelectCustomMode = {
                    scope.launch {
                        preferenceManager.updateUseCustomGeminiModel(true)
                    }
                },
                onCustomModelChange = { valTrim ->
                    scope.launch {
                        preferenceManager.updateCustomGeminiModel(valTrim)
                    }
                }
            )

            ApiKeyField(
                label = "OpenRouter API Key (Optional fallback)",
                unlockNote = "Fallback AI router when you prefer non-Gemini models.",
                placeholder = "sk-or-v1-...",
                value = openRouterKey,
                onValueChange = {
                    openRouterKey = it
                    validationStates["openrouter"] = ApiKeyValidationState.Idle
                },
                validationState = validationStates["openrouter"] ?: ApiKeyValidationState.Idle,
                onTest = { testSingleKey("openrouter") { ApiKeyValidator.validateOpenRouter(openRouterKey) } }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Section 2: Artwork & Ratings Boosters
            Text(
                text = "ARTWORK & RATINGS BOOSTERS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = AccentGold
            )

            ApiKeyField(
                label = "TMDb API Key (v3 auth or v4 Read Token)",
                unlockNote = "Unlocks Movie/TV metadata, posters, backdrops & screen adaptations.",
                placeholder = "32-character v3 key or Bearer token",
                value = tmdbKey,
                onValueChange = {
                    tmdbKey = it
                    validationStates["tmdb"] = ApiKeyValidationState.Idle
                },
                validationState = validationStates["tmdb"] ?: ApiKeyValidationState.Idle,
                onTest = { testSingleKey("tmdb") { ApiKeyValidator.validateTmdb(tmdbKey) } }
            )

            ApiKeyField(
                label = "Fanart.tv Project API Key",
                unlockNote = "Unlocks clean textless logos, clearart & wide hero banners.",
                placeholder = "Fanart.tv API key",
                value = fanartKey,
                onValueChange = {
                    fanartKey = it
                    validationStates["fanart"] = ApiKeyValidationState.Idle
                },
                validationState = validationStates["fanart"] ?: ApiKeyValidationState.Idle,
                onTest = { testSingleKey("fanart") { ApiKeyValidator.validateFanart(fanartKey) } }
            )

            ApiKeyField(
                label = "SteamGridDB API Key",
                unlockNote = "Unlocks textless covers, heroes & logos for Games and Visual Novels.",
                placeholder = "SteamGridDB key",
                value = steamGridDbKey,
                onValueChange = {
                    steamGridDbKey = it
                    validationStates["sgdb"] = ApiKeyValidationState.Idle
                },
                validationState = validationStates["sgdb"] ?: ApiKeyValidationState.Idle,
                onTest = { testSingleKey("sgdb") { ApiKeyValidator.validateSteamGridDb(steamGridDbKey) } }
            )

            ApiKeyField(
                label = "IGDB / Twitch Client ID",
                unlockNote = "Pairs with the secret below to unlock rich game trailers, genres & screenshots.",
                placeholder = "Twitch Developer App Client ID",
                value = igdbClientId,
                onValueChange = {
                    igdbClientId = it
                    validationStates["igdb"] = ApiKeyValidationState.Idle
                }
            )

            ApiKeyField(
                label = "IGDB / Twitch Client Secret",
                unlockNote = "Required alongside IGDB Client ID to authenticate with Twitch.",
                placeholder = "Twitch Developer App Client Secret",
                value = igdbClientSecret,
                onValueChange = {
                    igdbClientSecret = it
                    validationStates["igdb"] = ApiKeyValidationState.Idle
                },
                validationState = validationStates["igdb"] ?: ApiKeyValidationState.Idle,
                testLabel = "Test Pair",
                showTestWhenAnyNonBlank = igdbClientId.isNotBlank() || igdbClientSecret.isNotBlank(),
                onTest = { testSingleKey("igdb") { ApiKeyValidator.validateIgdb(igdbClientId, igdbClientSecret) } }
            )

            ApiKeyField(
                label = "MdbList API Key",
                unlockNote = "Unlocks IMDb, Rotten Tomatoes, Trakt & Metacritic critic score rows.",
                placeholder = "MdbList API key",
                value = mdblistKey,
                onValueChange = {
                    mdblistKey = it
                    validationStates["mdblist"] = ApiKeyValidationState.Idle
                },
                validationState = validationStates["mdblist"] ?: ApiKeyValidationState.Idle,
                onTest = { testSingleKey("mdblist") { ApiKeyValidator.validateMdblist(mdblistKey) } }
            )

            ApiKeyField(
                label = "OMDb API Key (Free · Optional)",
                unlockNote = "Free backup for IMDb, Rotten Tomatoes & Metascore. Get one free at omdbapi.com/apikey.aspx",
                placeholder = "OMDb 8-character key",
                value = omdbKey,
                onValueChange = {
                    omdbKey = it
                    validationStates["omdb"] = ApiKeyValidationState.Idle
                },
                validationState = validationStates["omdb"] ?: ApiKeyValidationState.Idle,
                onTest = { testSingleKey("omdb") { ApiKeyValidator.validateOmdb(omdbKey) } }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Section 3: Comics Metadata & Pull List
            Text(
                text = "COMICS METADATA & PULL LIST",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = AccentGold
            )

            Text(
                text = "Comics search & shelves already work without keys via Apple Books & Open Library. Add Comic Vine or Metron to unlock full issue checklists, creator credits, variant covers & live Wednesday pull lists.",
                fontSize = 11.5.sp,
                color = TextSecondary
            )

            ApiKeyField(
                label = "Comic Vine API Key (Optional)",
                unlockNote = "Unlocks full volume issues, creative teams, characters & variant art.",
                placeholder = "Comic Vine 40-char API key",
                value = comicVineKey,
                onValueChange = {
                    comicVineKey = it
                    validationStates["comicvine"] = ApiKeyValidationState.Idle
                },
                validationState = validationStates["comicvine"] ?: ApiKeyValidationState.Idle,
                onTest = { testSingleKey("comicvine") { ApiKeyValidator.validateComicVine(comicVineKey) } }
            )

            ApiKeyField(
                label = "Metron Username (Optional)",
                unlockNote = "Free Metron.cloud account username for live Wednesday pull lists.",
                placeholder = "Metron username",
                value = metronUsername,
                onValueChange = {
                    metronUsername = it
                    validationStates["metron"] = ApiKeyValidationState.Idle
                }
            )

            ApiKeyField(
                label = "Metron Password (Optional)",
                unlockNote = "Pairs with your Metron username via Basic Auth.",
                placeholder = "Metron password",
                value = metronPassword,
                onValueChange = {
                    metronPassword = it
                    validationStates["metron"] = ApiKeyValidationState.Idle
                },
                validationState = validationStates["metron"] ?: ApiKeyValidationState.Idle,
                testLabel = "Test Login",
                showTestWhenAnyNonBlank = metronUsername.isNotBlank() || metronPassword.isNotBlank(),
                onTest = { testSingleKey("metron") { ApiKeyValidator.validateMetron(metronUsername, metronPassword) } }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    scope.launch {
                        preferenceManager.updateGeminiApiKey(geminiKey.trim())
                        preferenceManager.updateOpenRouterApiKey(openRouterKey.trim())
                        preferenceManager.updateTmdbApiKey(tmdbKey.trim())
                        preferenceManager.updateFanartApiKey(fanartKey.trim())
                        preferenceManager.updateSteamGridDbApiKey(steamGridDbKey.trim())
                        preferenceManager.updateIgdbCredentials(igdbClientId.trim(), igdbClientSecret.trim())
                        preferenceManager.updateMdblistApiKey(mdblistKey.trim())
                        preferenceManager.updateOmdbApiKey(omdbKey.trim())
                        preferenceManager.updateComicVineApiKey(comicVineKey.trim())
                        preferenceManager.updateMetronCredentials(metronUsername.trim(), metronPassword.trim())
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
            ) {
                Text(
                    text = "Save Configuration",
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
    unlockNote: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    validationState: ApiKeyValidationState = ApiKeyValidationState.Idle,
    testLabel: String = "Test",
    showTestWhenAnyNonBlank: Boolean = value.isNotBlank(),
    onTest: (() -> Unit)? = null
) {
    val validGreen = Color(0xFF22C55E)
    val invalidRed = Color(0xFFEF4444)
    val borderColor = when (validationState) {
        is ApiKeyValidationState.Valid -> validGreen.copy(alpha = 0.55f)
        is ApiKeyValidationState.Invalid -> invalidRed.copy(alpha = 0.55f)
        is ApiKeyValidationState.Testing -> AccentGold.copy(alpha = 0.55f)
        is ApiKeyValidationState.Idle -> GlassBorder
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            when (validationState) {
                is ApiKeyValidationState.Testing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AccentGold,
                            strokeWidth = 1.8.dp,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Testing...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentGold
                        )
                    }
                }
                is ApiKeyValidationState.Valid -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Verified",
                            tint = validGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = validationState.detail,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = validGreen
                        )
                    }
                }
                is ApiKeyValidationState.Invalid -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Invalid",
                            tint = invalidRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = validationState.reason,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = invalidRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                is ApiKeyValidationState.Idle -> {
                    if (onTest != null && showTestWhenAnyNonBlank) {
                        Text(
                            text = "Not tested",
                            fontSize = 10.5.sp,
                            color = TextSecondary.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }
        Text(
            text = unlockNote,
            fontSize = 11.sp,
            color = TextSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(AccentGold),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = TextSecondary.copy(alpha = 0.65f), fontSize = 12.5.sp)
                    }
                    innerTextField()
                }
            )
            if (onTest != null && showTestWhenAnyNonBlank) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = validationState !is ApiKeyValidationState.Testing) { onTest() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = testLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                }
            }
        }
    }
}

@Composable
fun GeminiModelSelectorSection(
    geminiModel: String,
    customGeminiModel: String,
    useCustomGeminiModel: Boolean,
    onSelectPreset: (String) -> Unit,
    onSelectCustomMode: () -> Unit,
    onCustomModelChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = remember(geminiModel) {
        DEFAULT_GEMINI_MODELS.firstOrNull { it.id == geminiModel }
    }
    val displayLabel = if (useCustomGeminiModel) {
        "Custom Model (Enter Manually…)"
    } else {
        selectedOption?.let { "${it.name} (${it.description})" } ?: geminiModel
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Gemini AI Model",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (expanded) AccentGold.copy(alpha = 0.7f) else GlassBorder,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = displayLabel,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Select Model",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
            ) {
                DEFAULT_GEMINI_MODELS.forEach { option ->
                    val isSelected = !useCustomGeminiModel && geminiModel == option.id
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = option.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) AccentGold else TextPrimary
                                )
                                Text(
                                    text = option.description,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = AccentGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            onSelectPreset(option.id)
                            expanded = false
                        }
                    )
                }

                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = "Custom Model (Enter Manually…)",
                                fontSize = 13.sp,
                                fontWeight = if (useCustomGeminiModel) FontWeight.Bold else FontWeight.Medium,
                                color = if (useCustomGeminiModel) AccentGold else TextPrimary
                            )
                            Text(
                                text = "Enter any custom or preview Gemini model ID",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    },
                    trailingIcon = if (useCustomGeminiModel) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = AccentGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    onClick = {
                        onSelectCustomMode()
                        expanded = false
                    }
                )
            }
        }

        AnimatedVisibility(visible = useCustomGeminiModel) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Custom Model ID",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = customGeminiModel,
                    onValueChange = { onCustomModelChange(it.trim()) },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "e.g. gemini-3.8-flash",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary.copy(alpha = 0.6f)
                        )
                    },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = DarkSurfaceElevated,
                        unfocusedContainerColor = DarkSurfaceElevated,
                        cursorColor = AccentGold
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
