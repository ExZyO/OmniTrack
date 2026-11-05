# OmniTrack — Master Architecture, Progress & Roadmap Tracker

> **Maintainer:** Exile  
> **Target Platform:** Native Android (Kotlin + Jetpack Compose)  
> **Visual Style:** Deep AMOLED Dark Mode (#000000) + Modern Glassmorphism + Dynamic Cover Glow  
> **Cost Model:** $0 Forever (Local Room SQLite + Direct Free APIs + Google Drive Backup)  
> **CI/CD Build Rule:** 100% Remote Builds on GitHub Actions (Zero local Gradle builds on PC)

---

## 🧭 Project Status Dashboard

| Milestone | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| **Phase 1: Foundation & UI Shell** | v1.0.0-alpha.1 | ✅ **COMPLETED** | Initial shell, Room schema, AMOLED theme, sample mock data across all 8 media types. |
| **Release Pipeline Automation** | CI/CD | ✅ **COMPLETED** | Automated signed release APK generation and dual-tier GitHub Releases on push. |
| **Phase 2: Step 1 (Lossless & Glow)** | v1.0.0-b2 | ✅ **COMPLETED** | Coil 3 `LosslessCoverMapper` + AndroidX Palette `CoverGlowExtractor`. |
| **Phase 2: Step 2 (Artwork Discovery)** | v1.0.0-b2 | ✅ **COMPLETED** | `ArtworkWaterfallRepository` (MangaDex, AniList, Bangumi) + `ArtworkPickerBottomSheet` + Gallery Picker. |
| **Fluid Unboxed UI Overhaul** | v1.0.0-b2 | ✅ **COMPLETED** | Removed boxed card clutter; full-bleed parallax hero, edge-to-edge typography, ambient bloom. |
| **Poweramp Fluid Gesture Engine** | v1.1.0 | 📋 **PLANNED** | Smooth exit swipe-to-dismiss, folder pull-out physics, Predictive Back gestures. |
| **Phase 2: Step 3 (Shelves & Modes)** | v1.0.0-b3 | ⏳ **NEXT UP** | Custom shelves, category reordering, Jetpack DataStore display modes (Compact, Comfortable, List). |
| **Phase 2: Step 4 (JSON & Backup)** | v1.0.0-b4 | ⏳ **PENDING** | `BackupManager` SAF file export/import + WorkManager scheduled snapshots. |
| **Phase 3: Metadata Trackers** | v1.2.0 | ⏳ **PENDING** | AniList, MAL, Trakt, SIMKL, TMDB, Comick, Hardcover, Bangumi, IGDB, VNDB. |
| **Phase 4: Scrapers & Release Radar**| v1.3.0 | ⏳ **PENDING** | Native web novel scrapers, Airing Release Calendar, WorkManager notifications. |

---

## 🎨 Design & Interaction Mandates (The Nuvio & Poweramp Standard)

1. **Rule 2.10 — Fluid, Unboxed UI Architecture**:
   - **Zero Nested Boxes / No Cramped Shapes:** Content breathes openly on the pure `#000000` AMOLED canvas. Grouping is achieved through optical whitespace and typography hierarchy.
   - **Full-Bleed Parallax Backdrop:** Media banners flow edge-to-edge with multi-stop dark scrims.
   - **Seamless Metadata:** Tags, status pills, and star ratings use borderless translucent tints (`dominantAccent.copy(0.18f)`), never wrapped in artificial card decks.

2. **Poweramp Gesture Physics (Upcoming Integration)**:
   - **Edge Swipe-to-Dismiss / Exit Swipe:** Smooth right-swipe gesture to pop back out of detail screens or folders with interruptible momentum.
   - **Predictive Back Navigation:** Android 14/15 native predictive back integration with Compose transitions.
   - **Tactile Touch Feedback:** Bouncy spring physics (`DampingRatioMediumBouncy`, `StiffnessMedium`) on all interactive controls.

3. **Two-Tier Release Notes (Non-Technical & Technical)**:
   - Every GitHub Release and version update must provide both:
     1. **Everyday Readers (Non-Programmers):** Plain-English summary of what's new and how to use it.
     2. **Engineering & Under-the-Hood (Technical):** Architecture changes, API endpoints, Room migrations, and build metrics.

---

## 🗂️ Project File Structure

```text
OmniTrack/
├── .github/workflows/
│   └── build-apk.yml               # Automated Release APK builder & GitHub Release publisher
├── app/
│   ├── src/main/java/com/omnitrack/app/
│   │   ├── MainActivity.kt         # Edge-to-edge Activity with OmniTrackTheme
│   │   ├── OmniTrackApplication.kt # Database & Repository singleton holder
│   │   ├── data/
│   │   │   ├── artwork/
│   │   │   │   ├── ArtworkCandidate.kt          # Waterfall DTO
│   │   │   │   ├── ArtworkWaterfallRepository.kt# AniList + MangaDex + Bangumi multi-source
│   │   │   │   └── LosslessCoverMapper.kt       # Suffix stripper for 4K lossless covers
│   │   │   ├── db/
│   │   │   │   ├── Converters.kt                # Enum <-> String SQLite converters
│   │   │   │   ├── OmniTrackDatabase.kt         # Room Database v1
│   │   │   │   ├── dao/
│   │   │   │   │   ├── CategoryDao.kt           # Custom shelf & category queries
│   │   │   │   │   └── MediaDao.kt              # Media CRUD & progress updates
│   │   │   │   └── entity/
│   │   │   │       ├── CategoryEntity.kt        # Reorderable shelf entity
│   │   │   │       └── MediaItemEntity.kt       # Unified 8-media tracking entity
│   │   │   ├── model/
│   │   │   │   ├── MediaType.kt                 # Anime, Manga, Novels, Shows, Movies, VNs, Games
│   │   │   │   └── TrackingStatus.kt            # In Progress, Plan to Track, Completed, etc.
│   │   │   └── repository/
│   │   │       └── MediaRepository.kt           # Single source of truth data layer
│   │   ├── ui/
│   │   │   ├── components/
│   │   │   │   ├── ArtworkPickerBottomSheet.kt  # Waterfall search + URL + Gallery photo picker
│   │   │   │   ├── ChapterStepper.kt            # Tactile stepper + scrub slider + numeric keypad
│   │   │   │   ├── CoverGlowBox.kt              # Dynamic radial bloom on AMOLED canvas
│   │   │   │   ├── CoverGlowExtractor.kt        # AndroidX Palette dominant color extractor
│   │   │   │   ├── EpisodeAccordion.kt          # Collapsible seasons & watched checkboxes
│   │   │   │   ├── GameProgressCard.kt          # Hours stepper + platform chips
│   │   │   │   ├── GlassmorphicSurface.kt       # Frosted glass background helper
│   │   │   │   ├── MediaCard.kt                 # Borderless poster card + tactile +1 action
│   │   │   │   ├── MediaTypeTabRow.kt           # Top medium navigation tabs
│   │   │   │   └── StatusFilterChips.kt         # Library status filter chips
│   │   │   ├── navigation/
│   │   │   │   └── OmniTrackNavigation.kt       # Home <-> Detail Compose navigation transitions
│   │   │   ├── screens/
│   │   │   │   ├── detail/
│   │   │   │   │   ├── DetailScreen.kt          # Full-bleed parallax hero & adaptive detail
│   │   │   │   │   └── DetailViewModel.kt       # State holder for progress & artwork updates
│   │   │   │   └── home/
│   │   │   │       ├── HomeScreen.kt            # Staggered animated poster grid & search
│   │   │   │       └── HomeViewModel.kt         # Reactive library flow & seed logic
│   │   │   └── theme/
│   │   │       ├── Color.kt                     # AmoledBlack, AccentArctic, AccentJade, etc.
│   │   │       ├── Theme.kt                     # OmniTrackTheme with dark window insets
│   │   │       └── Type.kt                      # Typographic scale
│   │   └── util/
│   │       └── MockData.kt                      # 17 rich media items across all 8 media types
├── Other/                                       # LOCAL ONLY (ignored in .gitignore)
└── TRACKER.md                                   # This progress & roadmap document
```
