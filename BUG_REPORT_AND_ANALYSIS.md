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
   In `DetailViewModel.kt` (`maybeEnrich`), `onClearartResolved` cached and saved TVDB URLs into Room (`item.clearartUrl`) without filtering for Preferred Title Language, and `needsEnrichment` skipped scrubbing already-saved TVDB URLs when no replacement clearlogo existed (`EnrichmentRepository.enrich` left `clearart = item.clearartUrl` intact if no TMDb/Fanart logo existed).

3. **Un-reset Title & Auxiliary State on Sibling Switch**:
   `DetailViewModel.navigateToPrevious()` / `navigateToNext()` did not reset `_englishTitle`, `_romajiTitle`, `_synonyms`, `_tags`, `_heroClearLogoUrl`, etc., causing the previous entry's localized title (e.g., `"DAN DA DAN"`) and clearlogo to bleed onto the newly selected sibling. Furthermore, `DetailAuxMemoryCache.getCacheKey` previously collided on `"id_0"` for ephemeral items.

4. **In-Flight Job Race Condition**:
   When switching entries via gesture, previous asynchronous enrichment jobs were continuing in the background and overwriting the active entry.

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
In `DetailScreen.kt` (`detectHorizontalDragGestures`):
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
1. `animatableOffsetX.animateTo(screenWidthPx)` smoothly slides Entry A (Dandadan) completely off the right edge of the display while `SiblingDetailPreview` holds Entry B at center.
2. `viewModel.navigateToPrevious()` triggers asynchronous Room DB queries (`repository.getMediaById` and `getEpisodeProgress`) before emitting the new `DetailUiState`.
3. Because `animatableOffsetX.snapTo(0f)` executed **immediately** after calling `navigateToPrevious()` (before `uiState.item` changed), Compose drew Entry A back at `x = 0` for 1–3 frames until `LaunchedEffect(item?.id)` fired. Additionally, waiting on uncached Room DB flows delayed `uiState.item` updates.

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
1. **Shared `aniListSemaphore` & Duplicate Requests**:
   `searchAniList` shared `NetworkPacer.withAniListPacing` with background detail/discussion/season-poster requests (where `fetchAniListSeasonPoster` in `EnrichmentRepository.kt` also fired dozens of unpaced GraphQL requests on multi-season anime), saturating AniList's 90 req/min rate limit and triggering HTTP 429 delays.
2. **Lack of Search Caching, Filter-Chip Debounce Penalty & Unsafe Coalescing**:
   Typing `"bleach"` under `All Verticals` launched AniList Anime + Manga searches, then clicking the `"Anime"` filter chip waited another 400ms debounce and launched a 3rd redundant AniList Anime query without caching or safe coalescing (`NetworkPacer.coalesce` previously bound shared `Deferred`s to Caller A's `coroutineScope`, aborting when `LaunchedEffect` restarted).
3. **`awaitAll()` Barrier & Full-Screen Spinner**:
   `OmniSearchRepository.searchAll()` blocked on `deferreds.awaitAll()` before emitting fast Bangumi results, and `DiscoverView` hid the entire grid whenever `isSearching == true`.

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
1. **`CancellationException` Swallowed in `launchSeriesFetch()`**:
   Opening a search preview started `launchSeriesFetch` twice (`openPreviewInternal` / `loadRecommendationsAndReviews` and again when `maybeEnrich` finished). When job #1 was cancelled, catch blocks at `DetailViewModel` (`catch (_: Exception)`) swallowed `CancellationException` (`StandaloneCoroutine was cancelled`). The cancelled coroutine fell straight through to Step 4 (`// 4. Synthesize episodes from total progress` with limit `<= 500`), publishing **1 synthetic season** (hiding the Seasons rail since `seasons.size == 1`) and **366 blank episodes** (`stillUrl == null`, falling back to the detail page banner).
2. **Triple Duplicate AniZip & Double Cinemeta Downloads**:
   `fetchAniZipTmdbId`, `fetchAniZipClearlogo`, and `fetchAniZipEpisodes` separately fetched `https://api.ani.zip/mappings?anilist_id=269`, while `fetchTvSeasons` and `fetchTvSeasonEpisodes` concurrently fetched `fetchCinemetaVideos(imdbId)` without coalescing.
3. **135-Request Season Poster Storm on 16-Season Cinemeta Series (`EnrichmentRepository.fetchTvSeasons`)**:
   Because Bleach has 16 story-arc seasons in Cinemeta vs 1 season on TMDb (`hasLumpedSeason1 == true`), `fetchTvSeasons` launched `fetchAniListSeasonPoster` for all 15 missing seasons (up to 5 unpaced AniList queries each = 75 calls) followed by a synchronous sequential `forEach` loop calling `fetchKitsuSeasonData` (~60 sequential Kitsu HTTP calls) before returning the seasons list, causing the multi-second delay before the 16 seasons appeared.

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
1. **Ignored Return Value & Premature `_previewItem` Clear in `closePreview()`**:
   - Header Back button in `DetailScreen.kt` previously ignored the boolean return value of `viewModel.closePreview()`, failing to call `onBack()` when popping the root preview and leaving `item == null` (pitch-black Box).
   - Even after calling `onBack()`, root `closePreview()` set `_previewItem.value = null` immediately while `NavHost` played its 250ms `popExitTransition`, flashing black during the slide-out animation.
2. **Ephemeral `remember` State in `DiscoverView`**:
   `lastExecutedQuery`, `lastExecutedFilter`, and `lastExecutedAi` were stored in local `remember` (disposed upon navigating to `DetailScreen`) and initialized with untrimmed `queryFlow.value`. DataStore `aiSearchSetting` also re-emitted on back navigation, causing `LaunchedEffect` to re-run `searchRepository.searchAll(...)`.

---

### Bug 5 (Bonus): Android 14 Predictive Back Manifest Warning
In the logcat:
```text
W/WindowOnBackDispatcher(11650): OnBackInvokedCallback is not enabled for the application.
W/WindowOnBackDispatcher(11650): Set 'android:enableOnBackInvokedCallback="true"' in the application manifest.
```
`AndroidManifest.xml` lacked `android:enableOnBackInvokedCallback="true"`.

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
In `DiscoverView.kt` & `AiQueryEngine.kt`:
1. `useAiSearch` was stored only in local `remember` (not persisted to `PreferenceManager`), and toggling it re-triggered `LaunchedEffect`.
2. Without a Gemini or OpenRouter API key, `AiQueryEngine` fell back to `resolveWithLocalHeuristics`, which returned the exact same query string with `isAiResolved = false`, left `bannerFlow.value = null`, and unconditionally re-executed `searchRepository.searchAll(...)` with a loading spinner.
3. Furthermore, `AiQueryEngine.resolveWithGemini` targeted retired `gemini-1.5-flash` instead of `gemini-2.5-flash` / `gemini-2.0-flash`.

---

## 4. Implemented Fixes & Architectural Optimizations

### Fix for Bug 1 (Logo Bleed, Foreign TVDB Logos & Title Bleed)
1. **Guard `DetailAuxMemoryCache` & Clean Auxiliary Reset (`DetailViewModel.kt`)**:
   `DetailAuxMemoryCache.getCacheKey` returns `null` for non-positive/unidentified previews, and `resetAuxFields()` clears all auxiliary flows (`_englishTitle`, `_romajiTitle`, `_synonyms`, `_tags`, `_heroClearLogoUrl`, `_seasonMalIds`, etc.) before loading a sibling or preview.
2. **Strict TVDB Logo Filtering & Room Scrubbing (`DetailViewModel.kt`, `EnrichmentRepository.kt`, `DetailScreen.kt`)**:
   TVDB URLs (`thetvdb.com`) are rejected when preferred language is not `NATIVE`, never re-cached into `clearartMemoryCache`, and existing stale TVDB URLs in Room are scrubbed cleanly during `maybeEnrich()`.

### Fix for Bug 2 (Zero-Flash PowerAmp Sibling Gesture)
1. **Synchronized Offset Snap & Frozen Sibling Previews (`DetailScreen.kt`)**:
   Removed immediate `animatableOffsetX.snapTo(0f)` after `navigateToPrevious()` / `navigateToNext()`. Instead, `animatableOffsetX` only resets in `LaunchedEffect(item?.id)` once the new item renders, while `frozenPrevPreview` / `frozenNextPreview` hold the target sibling preview steady during the transition frame.
2. **0ms Room Item & Progress Caching (`DetailViewModel.kt`)**:
   Added `latestRoomItemCache` and `latestEpisodeProgressCache` with `.onStart { ... }.distinctUntilChanged()` on Room flows inside `uiState`, plus unified `navigateToSibling(...)` helper so switching siblings emits immediately in 0ms.

### Fix for Bug 3 (Fast, Coalesced, Progressive AniList Search)
1. **Dedicated Interactive Pacer & Safe `SupervisorJob` Coalescer (`NetworkPacer.kt`)**:
   Added `withAniListSearchPacing` (separate from background enrichment) and upgraded `NetworkPacer.coalesce` to use `CoroutineScope(SupervisorJob() + Dispatchers.IO)` so filter-chip switches never cancel shared in-flight requests.
2. **Search Result Cache, 429 Retries & Progressive UI (`OmniSearchRepository.kt`, `DiscoverView.kt`)**:
   Added `providerSearchCache` + `NetworkPacer.coalesce` around `searchAniList` (eliminating duplicate requests when toggling `All` $\leftrightarrow$ `Anime`), 0ms chip-switch skip on debounce, and thread-safe `onPartialResults` streaming so Bangumi shows immediately while AniList merges in with an inline spinner.

### Fix for Bug 4 (Bleach 366 Episodes & Fast Cinemeta Season Resolution)
1. **Proper `CancellationException` Propagation & Series Fetch Deduplication (`DetailViewModel.kt`, `EnrichmentRepository.kt`)**:
   Rethrows `CancellationException` in all catch blocks, calls `currentCoroutineContext().ensureActive()` before fallback synthesis, limits synthetic episode fallback to short series ($\le 26$ episodes), and deduplicates `launchSeriesFetch` calls across `openPreview`/`maybeEnrich`/`loadRecommendationsAndReviews` via `activeSeriesFetchKey`.
2. **Single-Shot AniZip & Cinemeta + Capped Poster Discovery (`EnrichmentRepository.kt`)**:
   Coalesces `fetchAniZipRoot` and `fetchCinemetaVideos`, paces `fetchAniListSeasonPoster` via `NetworkPacer.withAniListPacing`, and caps automatic extra per-season poster lookups in `fetchTvSeasons` at `<= 5` missing seasons so 16-season series like Bleach load without triggering a 135-request storm.

### Fix for Bug 5 (Back Navigation & Zero-Reload Discover State)
1. **Root Preview Preservation on Pop Exit & Back Handler (`DetailViewModel.kt`, `DetailScreen.kt`, `AndroidManifest.xml`)**:
   Header Back button and `BackHandler` honor `closePreview()`'s boolean return value; root `closePreview()` preserves `_previewItem` in memory during `NavHost.popExitTransition` so the screen never flashes black; `android:enableOnBackInvokedCallback="true"` is enabled.
2. **ViewModel-Backed Discover Search Execution State (`HomeViewModel.kt`, `DiscoverView.kt`)**:
   Moved `discoverLastExecutedQuery`, `discoverLastExecutedFilter`, `discoverLastExecutedAi`, `discoverLastEffectiveQuery`, `discoverLastEffectiveFilter`, and `discoverUseAiSearch` into `HomeViewModel` so returning from `DetailScreen` never reloads or loses scroll position.

### Fix for Bug 6 (AI Search Feedback, No-Reload Toggle & Gemini 2.5 Flash)
1. **Model Update & No-Op Reload Elimination (`AiQueryEngine.kt`, `DiscoverView.kt`)**:
   Upgraded `resolveWithGemini` from retired `gemini-1.5-flash` to `gemini-2.5-flash` (with `gemini-2.0-flash` fallback), persisted AI toggle state in `PreferenceManager`, shows clear setup/heuristic banners when no API key is configured, and skips redundant `searchRepository.searchAll(...)` reloads when `queryToRun` and `filterToRun` have not changed.

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
