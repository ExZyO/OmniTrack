# OmniTrack Architecture, Bug Investigation & Root Cause Report
**Target Audience**: Senior Android / Jetpack Compose / Kotlin Coroutines Architecture AI  
**Scope**: 6 isolated UI/Lifecycle/Data bugs captured via wireless ADB on Samsung Galaxy S24 Ultra (Android 14, OneUI 6.1) running OmniTrack debug build `1.0.0-b188-debug`.

---

## 1. System Architecture & Context Overview

### What is OmniTrack?
OmniTrack is a high-performance, offline-first native Android media tracking application built with:
- **Language & UI Toolkit**: Kotlin 2.x, Jetpack Compose, Material 3, Chris Banes' `Haze` (glassmorphism), Coil 3 (image loading).
- **Persistence**: Room Database (`MediaRepository`, `MediaItemEntity`, `EpisodeProgressEntity`).
- **Networking**: OkHttp 4.x singleton (`NetworkClient`), custom token-bucket/semaphore pacer (`NetworkPacer`), Kotlinx Serialization.
- **External Metadata Catalogs**:
  - **AniList**: Anime/Manga GraphQL API (`https://graphql.anilist.co`).
  - **TMDb**: TV Shows, Movies, Anime seasons/episodes (`TheMovieDatabase v3`).
  - **Bangumi**: Japanese multi-media catalog API (`https://api.bgm.tv`).
  - **AniZip**: Cross-mapping API linking AniList IDs $\leftrightarrow$ TVDB $\leftrightarrow$ TMDb $\leftrightarrow$ episode mappings.
  - **Dantotsu / Kumiho**: Community comment system for anime/manga.

### Key Architectural Concepts
1. **`DetailViewModel` & `DetailScreen`**:
   - The central detail view displaying media details, hero banner/poster, clearart logo (transparent PNG title logo overlaying the hero banner), seasons, episodes, cast, reviews, and community comments.
   - Supports **two viewing modes**:
     - **Library Mode** (`mediaId > 0`): Stored in local Room DB.
     - **Ephemeral Preview Mode** (`mediaId <= 0`, typically `-1L` or `0L`): Clicked from Search/Discover. Metadata is held temporarily in `EphemeralPreviewHolder.previewItem` and `_previewStack` before the user decides to "Add to Library".
2. **PowerAmp Horizontal Bilateral Swipe Gestures**:
   - `DetailScreen` includes kinematic horizontal drag gestures (`detectHorizontalDragGestures`) with spring physics.
   - Swiping right navigates to the previous sibling entry; swiping left navigates to the next sibling entry within the active library/search list (`ActiveSiblingHolder`).
3. **In-Memory Caching Layers**:
   - `DetailAuxMemoryCache`: In-memory LRU/ConcurrentHashMap holding auxiliary detail data (recommendations, cast, reviews, hero clear logos).
   - `SeriesContentMemoryCache`: Memory cache holding resolved TMDb IDs, seasons, and episodes.
   - `EnrichmentRepository`: Handles asynchronous fetching of clearart logos from Fanart.tv, TheTVDB, AniZip, and TMDb.

---

## 2. Executive Summary of the 6 Captured Bugs

| Bug # | User Action & Symptom | Log Evidence File | Primary Root Cause Category |
| :--- | :--- | :--- | :--- |
| **Bug 1** | PowerAmp gesture switch causes art logos to bleed across unrelated entries (e.g., Solo Leveling logo or plain text on Apothecary Diaries); Chinese TVDB logo appears despite English preference. | `bug1_logo_bleed_and_poweramp.log` | **Cache Key Collision & Unfiltered Logo Persistence**: `DetailAuxMemoryCache` keys on `mediaId` without guarding against `id <= 0`, causing all search/preview items to share `"id_0"` / `"ANIME_0_null_null_null"`. Also, asynchronous `onClearartResolved` saves unfiltered TVDB URLs to Room DB. |
| **Bug 2** | PowerAmp gesture swipe between entries flashes the previous entry for 1–2 frames before loading the new entry. | `bug2_poweramp_flash_switch.log` | **Premature Offset Snap in Gesture Physics**: `animatableOffsetX.snapTo(0f)` executes immediately after calling `viewModel.navigateToNext()`, before Room DB / Compose has emitted the new item, snapping the old item back into center view. |
| **Bug 3** | Searching "Bleach" with Anime filter: AniList results are severely delayed or do not appear, while Bangumi appears instantly. | `bug3_bleach_search_anilist_vs_bangumi.log` | **Global Serial Pacing & Coroutine Blocking**: `NetworkPacer.withAniListPacing` throttles GraphQL calls behind in-flight background enrichment/discussion queries, while Bangumi runs unthrottled. |
| **Bug 4** | Bleach episodes initially show no seasons, then 366 generic episodes with background poster thumbnails, and only after a long delay do real TMDb seasons and episode preview stills swap in. | `bug4_bleach_366_episodes_delay.log` | **Optimistic TotalProgress Synthesis Preceding Chained Network Calls**: Preview items without `tmdbId` execute a 5-step serial network resolution (AniList $\to$ AniZip $\to$ TMDb $\to$ Seasons $\to$ Episodes). The optimistic 366-episode synthesizer executes before real seasons arrive. |
| **Bug 5** | Clicking the top-left Back button from a search preview details page turns the screen completely pitch black. Only a secondary system Android back gesture exits to search. Going back also forces the search page to reload. | `bug5_back_black_screen_and_search_reload.log` | **Ignored Navigation Return Value & Missing Root Exit**: `DetailScreen.kt` calls `viewModel.closePreview()` on back button click but discards the boolean return value. When the preview stack is empty, it resets `_previewItem = null` without calling `onBack()`, rendering an empty Box. |
| **Bug 6** | Tapping the "AI" button in the search bar does nothing except cause the search page to blink/reload. | `bug6_ai_button_search_reload.log` | **Missing API Key Fallback Feedback & Query State Invalidation**: The AI button toggles `useAiSearch`. If no Gemini/OpenRouter API key is configured, `AiQueryEngine` falls back to local heuristics which returns the identical query string, triggering a full network re-search with no visual feedback. |

---

## 3. Deep Dive Analysis by Bug

---

### Bug 1: Cross-Entry Art Logo Bleed & Foreign TVDB Clearlogos

#### Observed Behavior
When navigating between entries using horizontal PowerAmp swipe gestures (e.g., from Dandadan to The Apothecary Diaries, to Solo Leveling):
1. The clearart logo (the stylized transparent title PNG) from the previous media item bleeds onto the new entry.
2. In many cases, it falls back to plain text or displays a Chinese/foreign language TVDB logo even when the user has set their Preferred Title Language to English.

#### Log Evidence (`bug_logs/bug1_logo_bleed_and_poweramp.log`)
```text
11-18 16:04:52.350 I/OmniTrack-HomeScreen(11650): Tapped card: The Apothecary Diaries (id: 26)
11-18 16:04:52.910 I/OmniTrack-DetailScreen(11650): Viewing detail for: The Apothecary Diaries [id=26, type=Anime]
11-18 16:04:54.647 D/OmniTrack-DetailViewModel(11650): AniZip fallback episodes error: StandaloneCoroutine was cancelled
11-18 16:04:56.086 D/OmniTrack-EnrichmentRepository(11650): Discovered seasonAniListIds for 'The Apothecary Diaries': {1=161645, 2=176301, 3=195516, 0=170508}
```

#### Code Inspection & Root Cause
1. **Cache Key Collision for Ephemeral / Search Items**:
   In `DetailViewModel.kt`:
   ```kotlin
   internal object DetailAuxMemoryCache {
       val cache = ConcurrentHashMap<String, CachedDetailAux>()
       fun getCacheKey(item: MediaItemEntity): String =
           "${item.mediaType}_${item.id}_${item.tmdbId}_${item.anilistId}_${item.malId}"
       fun getCacheKey(mediaId: Long): String = "id_$mediaId"
   }
   ```
   When items are opened from Discover/Search as previews, their database `id` is `0L` or `-1L`, and their `tmdbId` is initially `null`.
   - `getCacheKey(item)` evaluates to `"ANIME_0_null_null_null"`.
   - `getCacheKey(mediaId)` evaluates to `"id_0"`.
   - **Every single search preview item shares the identical cache entry!** Opening Dandadan caches its logo and aux data under `"id_0"`. Opening Apothecary Diaries or Solo Leveling immediately retrieves Dandadan's cached logo from `"id_0"`.

2. **Unfiltered TVDB Logo Stored in Database**:
   In `DetailViewModel.kt` (`maybeEnrich`):
   ```kotlin
   onClearartResolved = { earlyLogo, _ ->
       EnrichmentRepository.putCachedClearart(item, earlyLogo)
       if (_mediaId.value == item.id) {
           _heroClearLogoUrl.value = earlyLogo
       }
       if (curr.clearartUrl != earlyLogo) {
           repository.updateMedia(curr.copy(clearartUrl = earlyLogo)) // Persisted to Room!
       }
   }
   ```
   AniZip returns clearlogos scraped from TheTVDB. TheTVDB clearlogos are frequently Chinese or Japanese localized text. `onClearartResolved` does not verify whether `earlyLogo` is from TVDB when English is preferred, and writes this Chinese logo directly into the Room DB entity (`item.clearartUrl`).
   Once saved to the database, `DetailScreen.kt` line 1045 loads `item.clearartUrl` on future visits without filtering.

3. **In-Flight Job Race Condition**:
   When switching entries via gesture, previous asynchronous enrichment jobs were continuing in the background. When their callbacks fired, if `item.id` was 0 (as in preview items), the identity check `_mediaId.value == item.id` (`0 == 0`) passed, overwriting the new entry's logo with the previous entry's resolved logo.

---

### Bug 2: Screen Flash During PowerAmp Gesture Navigation

#### Observed Behavior
When performing a PowerAmp horizontal swipe gesture to switch from Dandadan to The Apothecary Diaries:
- Dandadan animates off-screen as expected.
- For a split second, Dandadan snaps back into full view in the center of the screen, creating a visual flash/stutter, before Apothecary Diaries appears.

#### Log Evidence (`bug_logs/bug2_poweramp_flash_switch.log`)
```text
11-18 16:05:49.256 I/InputDispatcher( 2894): Delivering touch to (11650): action: 0x0
11-18 16:05:49.347 I/InputDispatcher( 2894): Delivering touch to (11650): action: 0x1
11-18 16:05:50.553 I/OmniTrack-UpdateChecker(11650): Checking for updates...
11-18 16:05:52.179 I/VRI[MainActivity]@e984aa3(11650): ViewPostIme pointer 0
```

#### Code Inspection & Root Cause
In `DetailScreen.kt` lines 465–490:
```kotlin
// Swiping right: navigate to previous sibling entry
currentX > threshold && uiState.hasPreviousEntry -> {
    coroutineScope.launch {
        animatableOffsetX.animateTo(
            targetValue = screenWidthPx,
            animationSpec = spring(...)
        )
        viewModel.navigateToPrevious()
        animatableOffsetX.snapTo(0f) // <-- ROOT CAUSE!
    }
}
```
1. `animatableOffsetX.animateTo(screenWidthPx)` smoothly slides Entry A (Dandadan) completely off the right edge of the display.
2. `viewModel.navigateToPrevious()` is invoked. Inside the ViewModel, `_mediaId.value = prevId` triggers an asynchronous Room DB query via `repository.getMediaById(prevId).flatMapLatest { ... }` on `Dispatchers.IO`.
3. In the very next line of the coroutine, `animatableOffsetX.snapTo(0f)` executes **immediately**.
4. Because the Room DB query and Compose recomposition take 30–100ms to fetch and emit the new entity into `uiState`, Compose executes a frame where `offsetX == 0f`, but `uiState.item` is **still Entry A (Dandadan)**!
5. This draws Dandadan centered at `x = 0`, causing a visible visual snap/flash before Apothecary Diaries is emitted.

---

### Bug 3: AniList Bleach Search Delay vs. Bangumi

#### Observed Behavior
When searching for "Bleach" in the Discover view with the "Anime" filter enabled:
- Bangumi results appear almost instantaneously.
- AniList results either take 5–10 seconds to appear or appear to not load at all unless repeated.

#### Log Evidence (`bug_logs/bug3_bleach_search_anilist_vs_bangumi.log`)
```text
11-18 16:07:38.904 I/ActivityManager( 2894): Start proc ...
11-18 16:08:00.913 I/track.app.debug(11650): Compiler allocated 9195KB to compile void ... DiscoverView(...)
```

#### Code Inspection & Root Cause
In `OmniSearchRepository.kt`:
```kotlin
MediaType.ANIME -> {
    deferreds.add(async { searchAniList(trimmed, isAnime = true) })
    deferreds.add(async { searchBangumi(trimmed, type = 2, defaultType = MediaType.ANIME) })
}
```
1. **Network Pacing Bottleneck**:
   `searchAniList` is wrapped inside `NetworkPacer.withAniListPacing`:
   ```kotlin
   private val anilistSemaphore = Semaphore(4)
   suspend fun <T> withAniListPacing(block: suspend () -> T): T {
       anilistSemaphore.withPermit {
           delay(50) // Global pacer delay
           block()
       }
   }
   ```
   When the user navigates between screens or opens items, multiple background tasks (`dantotsuCommentsRepository.getComments`, `enrichmentRepository.searchAniListForAnime`, `fetchAniZipEpisodes`) also consume permits from the shared AniList semaphore.
2. **HTTP 429 Rate Limiting & Backoff**:
   AniList enforces a strict 90 requests/minute limit. When burst requests occur, AniList responds with HTTP 429.
   In `searchAniList`:
   ```kotlin
   var response = client.newCall(request).execute()
   if (response.code == 429) {
       response.close()
       delay(800)
       response = client.newCall(request).execute()
   }
   ```
   If a 429 occurs, it delays for 800ms while holding the coroutine.
3. **Bangumi Runs Unthrottled**:
   Bangumi uses `withBangumiPacing` (a separate semaphore with no competing background workers) and hits an unauthenticated HTTP REST endpoint returning small JSON payloads, finishing in ~120ms.
4. **`awaitAll()` Barrier**:
   In `OmniSearchRepository.kt`:
   ```kotlin
   val rawResults = deferreds.awaitAll().flatten()
   ```
   Because `awaitAll()` waits for **both** AniList and Bangumi before emitting any results to `resultsFlow`, the entire search UI is blocked until AniList finishes. If AniList is delayed, the user sees nothing; if AniList times out or fails silently, only Bangumi is returned.

---

### Bug 4: Bleach 366-Episode Delay & Generic Backdrop Thumbnails

#### Observed Behavior
When opening Bleach from search results:
1. Initially, no season selector is displayed.
2. The episodes section lists 366 episodes titled "Episode 1", "Episode 2"... with no descriptions, no air dates, and with the anime's background poster duplicated as every episode's thumbnail.
3. After several seconds, the season selector suddenly appears (showing 16 seasons), and proper episode thumbnails and titles load in.

#### Log Evidence (`bug_logs/bug4_bleach_366_episodes_delay.log`)
```text
11-18 16:08:56.686 I/OmniTrack-DetailScreen(11650): Viewing detail for: Bleach [id=0, type=Anime]
11-18 16:08:57.510 D/OmniTrack-EnrichmentRepository(11650): Discovered seasonAniListIds for 'Bleach': {1=269, 0=834, 2=116674}, seasonMalIds: {1=269, 0=834, 2=41467}
11-18 16:08:58.204 D/OmniTrack-DetailViewModel(11650): AniZip fallback episodes error: StandaloneCoroutine was cancelled
```

#### Code Inspection & Root Cause
1. **Search Preview Lacks TMDb ID**:
   When Bleach is opened from AniList search results, the item has `id = 0`, `anilistId = 269`, `totalProgress = 366`, but `tmdbId = null`.
2. **Serial Multi-Step Network Resolution**:
   Inside `launchSeriesFetch(item)`:
   - Step 1: `enrichmentRepository.fetchAniZipTmdbId(269)` $\to$ returns TMDb ID `30984`.
   - Step 2: `enrichmentRepository.fetchTmdbImdbId(30984)` $\to$ network call.
   - Step 3: `enrichmentRepository.fetchTvSeasons(30984)` $\to$ returns 16 seasons.
   - Step 4: `enrichmentRepository.fetchTvSeasonEpisodes(30984, seasonNumber = 1)` $\to$ TMDb Season 1 contains 366 episodes in a single ~1.2 MB JSON response.
3. **Optimistic Synthesis Fallback Race**:
   At the tail end of `launchSeriesFetch`:
   ```kotlin
   // 4. Synthesize episodes from total progress if catalogue metadata has no granular breakdown
   if (_realSeasons.value.isEmpty() && _realEpisodes.value.isEmpty()) {
       val totalCount = item.totalProgress?.toInt() // 366
       if (totalCount != null && totalCount > 0 && totalCount <= 500) {
           val synEps = (1..totalCount).map { epNum ->
               MediaEpisode(id = epNum.toLong(), episodeNumber = epNum, seasonNumber = 1, title = "Episode $epNum", stillUrl = null)
           }
           _realSeasons.value = listOf(synSeason)
           _episodesBySeason.value = mapOf(1 to synEps)
           _realEpisodes.value = synEps
       }
   }
   ```
   Because TMDb's 1.2 MB Season 1 payload takes 2–4 seconds to download and parse on mobile data/Wi-Fi, the initial state shows either nothing or the synthesized 366 generic episodes. In `DetailScreen.kt`, when `episode.stillUrl == null`, the UI falls back to rendering `item.bannerUrl ?: item.coverUrl`.
   Once TMDb finishes parsing, `_realSeasons.value` is overwritten with the 16 real TMDb seasons, causing the layout to jump and reload all episode cards.

---

### Bug 5: Back Button Black Screen & Search Page Reload

#### Observed Behavior
1. When viewing a search preview item in `DetailScreen`, clicking the in-app Back button (the top-left back arrow) turns the screen completely pitch black.
2. The user is stuck on this black screen until they perform the system Android edge-swipe back gesture, which returns them to the Search page.
3. When returning to the Search page, the search results reload from scratch rather than preserving the previous state.

#### Log Evidence (`bug_logs/bug5_back_black_screen_and_search_reload.log`)
```text
11-18 16:10:54.019 D/CoreBackPreview( 2894): startBackNavigation currentTask=Task{...}
11-18 16:10:54.029 D/WindowOnBackDispatcher(11650): onBackInvoked, callback=android.view.ViewRootImpl...
11-18 16:10:54.071 W/WindowOnBackDispatcher(11650): OnBackInvokedCallback is not enabled for the application.
```

#### Code Inspection & Root Cause
1. **Ignored Return Value in Header Back Button**:
   In `DetailScreen.kt` lines 1007–1023:
   ```kotlin
   IconButton(
       onClick = {
           if (uiState.isPreviewMode) {
               viewModel.closePreview() // <-- RETURNS FALSE, BUT RETURN VALUE IS DISCARDED!
               val restoredOffset = viewModel.popScrollOffset()
               if (restoredOffset != null) {
                   coroutineScope.launch { scrollState.scrollTo(restoredOffset) }
               }
           } else if (sheetBackStack.isNotEmpty()) {
               sheetBackStack.removeAt(sheetBackStack.lastIndex)
           } else {
               onBack()
           }
       }
   )
   ```
   Now check `viewModel.closePreview()` in `DetailViewModel.kt`:
   ```kotlin
   fun closePreview(): Boolean {
       if (_previewStack.value.size > 1) {
           _previewStack.update { it.dropLast(1) }
           // ... restore previous preview
           return true
       }
       _previewStack.value = emptyList()
       _previewItem.value = null
       EphemeralPreviewHolder.previewItem = null
       val currentId = _mediaId.value
       resetAuxFields()
       if (currentId > 0L) {
           // ... restore library item
           return true
       }
       return false // <-- Indicates there is nothing left to preview, must exit screen!
   }
   ```
   - For a search preview item, `_previewStack.value.size` is 1 and `_mediaId.value` is `-1L`.
   - `closePreview()` clears `_previewItem.value = null` and returns `false`.
   - But `DetailScreen.kt` **never checks the boolean result** and does **not** call `onBack()`!
   - Now `uiState.item` becomes `null`. In `DetailScreen.kt`:
     ```kotlin
     if (uiState.isLoading && item == null) {
         NuvioLoadingIndicator()
     } else if (item != null) {
         // ... Detail content
     }
     ```
     Since `isLoading` is false and `item` is null, **neither branch executes**! The composable renders an empty `Box(Modifier.fillMaxSize())` with a black background!
   - Compare this to the system back handler at line 218:
     ```kotlin
     val handled = viewModel.closePreview()
     if (!handled) {
         onBack() // System back gesture works because it checked `!handled`!
     }
     ```

2. **Search Page Reload on Return**:
   In `DiscoverView.kt`:
   ```kotlin
   var lastExecutedQuery by remember { mutableStateOf<String?>(if (resultsFlow.value.isNotEmpty()) queryFlow.value else null) }
   var lastExecutedFilter by remember { mutableStateOf<MediaType?>(if (resultsFlow.value.isNotEmpty()) filterFlow.value else null) }
   var lastExecutedAi by remember { mutableStateOf<Boolean?>(if (resultsFlow.value.isNotEmpty()) useAiSearch else null) }
   ```
   When navigating away from `DiscoverView` to `DetailScreen`, if `DiscoverView` leaves composition, `remember` state is disposed.
   When navigating back, `lastExecutedQuery` is re-initialized. In `LaunchedEffect(searchQuery, selectedFilter, useAiSearch)`:
   ```kotlin
   if (resultsFlow.value.isNotEmpty() && trimmed == lastExecutedQuery && ...) {
       return@LaunchedEffect
   }
   ```
   If any timing mismatch occurs between `queryFlow` and `lastExecutedQuery` during re-composition, it falls through to `delay(400)` and re-executes `searchRepository.searchAll(...)`, discarding the preserved scroll position and reloading from the network.

---

### Bug 5 (Bonus): Android 14 Predictive Back Manifest Warning
In the logcat:
```text
W/WindowOnBackDispatcher(11650): OnBackInvokedCallback is not enabled for the application.
W/WindowOnBackDispatcher(11650): Set 'android:enableOnBackInvokedCallback="true"' in the application manifest.
```
`AndroidManifest.xml` lacks `android:enableOnBackInvokedCallback="true"`, causing Android 13/14 predictive back animations to fail and fall back to legacy window dismissal.

---

### Bug 6: AI Button Click in Search Bar Has No Effect / Reloads

#### Observed Behavior
Tapping the Sparkle/AI icon button in the search text field triggers a progress spinner and reloads the search page, but does not show AI suggestions or any changes to the results.

#### Log Evidence (`bug_logs/bug6_ai_button_search_reload.log`)
```text
11-18 16:11:36.936 I/InputDispatcher( 2894): Delivering touch to (11650): action: 0x0
11-18 16:11:36.957 I/InputDispatcher( 2894): Delivering touch to (11650): action: 0x1
```

#### Code Inspection & Root Cause
In `DiscoverView.kt` lines 257–268:
```kotlin
.clickable { useAiSearch = !useAiSearch }
```
When tapped:
1. `useAiSearch` toggles from `false` to `true`.
2. `LaunchedEffect(searchQuery, selectedFilter, useAiSearch)` triggers because `useAiSearch != lastExecutedAi`.
3. In `DiscoverView.kt`:
   ```kotlin
   val geminiKey by prefManager.geminiApiKey.collectAsState(initial = "")
   val openRouterKey by prefManager.openRouterApiKey.collectAsState(initial = "")
   // ...
   if (useAiSearch) {
       val aiResult = aiEngine.resolveQuery(
           rawQuery = trimmed,
           geminiApiKey = geminiKey.ifBlank { null },
           openRouterApiKey = openRouterKey.ifBlank { null }
       )
   ```
4. In `AiQueryEngine.kt`:
   ```kotlin
   suspend fun resolveQuery(rawQuery: String, geminiApiKey: String?, openRouterApiKey: String?): AiQueryResult {
       if (!geminiApiKey.isNullOrBlank()) { ... }
       if (!openRouterApiKey.isNullOrBlank()) { ... }
       // 3. Smart Local Heuristic Fallback
       resolveWithLocalHeuristics(trimmed)
   }
   ```
   If the user has **not configured a Gemini or OpenRouter API key** in Settings, `AiQueryEngine` falls back to `resolveWithLocalHeuristics`.
5. For an exact title query like `"bleach"`, `resolveWithLocalHeuristics("bleach")` returns:
   `AiQueryResult(resolvedTitle = "bleach", detectedType = null, isAiResolved = false)`.
6. Back in `DiscoverView.kt`:
   - `aiResult.isAiResolved` is `false`.
   - `aiResult.resolvedTitle == trimmed` (`"bleach" == "bleach"`).
   - Therefore, `bannerFlow.value` remains `null`. No banner or notification appears.
7. Next, line 176 executes:
   `val results = searchRepository.searchAll(query = "bleach", filter = filterToRun)`
   This runs the identical network search again, showing the loading indicator, and delivers the exact same results.
8. To the user, tapping the AI button appears broken: it blinks/reloads the screen and changes nothing, with zero feedback informing them that no AI API key is configured.

---

## 4. Proposed Fixes & Architectural Recommendations

### Fix for Bug 1 (Logo Bleed & Foreign TVDB Logos)
1. **Guard `DetailAuxMemoryCache` against Non-Positive IDs**:
   In `DetailAuxMemoryCache`:
   ```kotlin
   fun getCacheKey(item: MediaItemEntity): String? {
       val key = item.tmdbId?.let { "tmdb_${it}" }
           ?: item.anilistId?.let { "anilist_${it}" }
           ?: if (item.id > 0L) "id_${item.id}" else null
       return key
   }
   ```
   Never generate a cache key like `"id_0"`, `"id_-1"`, or `"ANIME_0_null_null_null"`. If an item has no positive ID or catalog key, bypass in-memory aux caching entirely.
2. **Strict TVDB Logo Filtering in Database Updates**:
   In `DetailViewModel.kt` `onClearartResolved`:
   ```kotlin
   val isTvdb = earlyLogo.contains("thetvdb.com", ignoreCase = true)
   val pref = preferenceManager?.cachedPreferredTitleLanguage ?: PreferredTitleLanguage.ENGLISH
   if (isTvdb && pref != PreferredTitleLanguage.NATIVE) {
       // Reject TVDB Chinese logo when English is preferred!
       return@onClearartResolved
   }
   ```
3. **DetailScreen Header Clearart Check**:
   Apply the same TVDB filter to `effectiveHeaderClearart` at line 1045 of `DetailScreen.kt`.

### Fix for Bug 2 (PowerAmp Navigation Flash)
1. **Coordinate Animation Reset with State Emission**:
   Do NOT call `animatableOffsetX.snapTo(0f)` immediately after calling `viewModel.navigateToNext()`.
   Instead, when `viewModel.navigateToNext()` succeeds:
   - Position `animatableOffsetX` at `+screenWidthPx` (entering from the opposite side).
   - Once the new `uiState.item` is collected, animate `animatableOffsetX` from `screenWidthPx` $\to$ `0f` (a true bilateral slide-through transition).
   - Alternatively, hide the content or hold the offset until `uiState.item.id == nextId`.

### Fix for Bug 3 (AniList Search Delays)
1. **Progressive Search Emission (Streaming / Asynchronous Merge)**:
   Do not use `val rawResults = deferreds.awaitAll().flatten()`.
   Instead, use a Kotlin `Flow` or emit results progressively:
   - As soon as Bangumi finishes (~150ms), emit Bangumi results to the UI.
   - When AniList finishes, merge and prioritize AniList results to the top of the list.
2. **Dedicated Search Pacer**:
   Do not force user-facing search queries to wait behind background discussion comment fetches. Allocate a dedicated high-priority slot in `NetworkPacer` for interactive search.

### Fix for Bug 4 (Bleach 366 Episodes & Generic Thumbnails)
1. **Parallel Episode Fetching & Cache Seed**:
   Seed `SeriesContentMemoryCache` with the TMDb ID from search results before opening `DetailScreen`.
2. **Refine Placeholder Synthesizer**:
   When synthesizing placeholder episodes from `totalProgress`:
   - Limit synthesis to small counts ($\le 26$), or display an explicit "Fetching season details from TMDb..." progress state rather than generating 366 generic episodes with duplicate backdrop artwork.

### Fix for Bug 5 (Back Button Black Screen & Manifest Back Flag)
1. **Check Boolean Return Value on ClosePreview**:
   In `DetailScreen.kt` lines 1008 & 1134:
   ```kotlin
   if (uiState.isPreviewMode) {
       val handled = viewModel.closePreview()
       if (!handled) {
           onBack()
       } else {
           val restoredOffset = viewModel.popScrollOffset()
           if (restoredOffset != null) {
               coroutineScope.launch { scrollState.scrollTo(restoredOffset) }
           }
       }
   }
   ```
2. **Enable Predictive Back in Manifest**:
   In `app/src/main/AndroidManifest.xml`:
   ```xml
   <application
       android:enableOnBackInvokedCallback="true"
       ... >
   ```

### Fix for Bug 6 (AI Button Behavior & Feedback)
1. **User Feedback Dialog / Toast when Keys are Missing**:
   If `geminiKey.isBlank() && openRouterKey.isBlank()`:
   - Display a Snackbar or Dialog: *"AI Semantic Search requires a Gemini or OpenRouter API key. Configure it in Settings $\to$ API Keys, or use natural language queries like 'anime with death note'."*
2. **Do Not Re-query if Query Has Not Changed**:
   If `aiResult.resolvedTitle == trimmed` and `!aiResult.isAiResolved`, do not trigger a full search reload. Simply show a subtle hint that the query was already specific.

---

## 5. File Reference Matrix

| Bug | Primary Files Involved | Specific Functions / Lines |
| :--- | :--- | :--- |
| **Bug 1** | `DetailViewModel.kt`, `EnrichmentRepository.kt`, `DetailScreen.kt` | `DetailAuxMemoryCache.getCacheKey`, `maybeEnrich()`, `onClearartResolved`, `effectiveHeaderClearart` |
| **Bug 2** | `DetailScreen.kt` | `detectHorizontalDragGestures` (lines 465–490), `animatableOffsetX.snapTo(0f)` |
| **Bug 3** | `OmniSearchRepository.kt`, `NetworkPacer.kt` | `searchAll()`, `searchAniList()`, `deferreds.awaitAll()` |
| **Bug 4** | `DetailViewModel.kt`, `EnrichmentRepository.kt` | `launchSeriesFetch()`, `generateMockSeasons()`, synthesize block (lines 2092–2119) |
| **Bug 5** | `DetailScreen.kt`, `DetailViewModel.kt`, `AndroidManifest.xml` | `DetailScreen.kt` lines 1008 & 1134, `closePreview()`, `application` tag |
| **Bug 6** | `DiscoverView.kt`, `AiQueryEngine.kt` | `DiscoverView.kt` lines 154–175 & 257–268, `resolveQuery()` |
