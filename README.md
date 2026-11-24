# OmniTrack

**OmniTrack** is an open-source, AMOLED-first universal media tracker for Android built with Jetpack Compose, Room SQLite, and Coil 3. Track everything you watch, read, and play in one fast, customizable library.

## Features

- **10 Media Verticals in One App**: Anime, Manga, Comics, Light & Web Novels, Fanfiction, Books, TV Shows, Movies, Games, and Visual Novels — hide or reorder any category globally to fit your library.
- **Multi-Tracker Two-Way Sync**: Connect **AniList**, **MyAnimeList**, **Bangumi**, **Comick**, **Trakt.tv**, **SIMKL**, **TMDb**, **Steam**, **VNDB**, and **Hardcover**, plus CSV/XML imports (Goodreads, Letterboxd, MAL XML).
- **Rich Vertical-Specific Tracking**:
  - **Visual Novels**: Character routes & endings with custom route photos (pinch-to-zoom & drag reframing), spoiler masking, playtime tracking, and VNDB metadata.
  - **Games**: Playtime steppers, completion tiers, platform tags, DLC/sub-entries, and Steam/IGDB metadata.
  - **Fanfiction & Web Novels**: Chapter checklists, update checks, cross-site mirror linking, and in-app WebView reading across **AO3**, **FanFiction.net**, **SpaceBattles**, **Sufficient Velocity**, **Questionable Questing**, and **Wattpad**.
  - **Books & Comics**: Format/publisher filters, reading quotes, and issue/volume tracking.
- **Custom Artwork & Framing**: Multi-source lossless cover & banner waterfall with interactive pinch-to-zoom banner reframing.
- **Performance Mode**: Pure AMOLED themes, hardware-accelerated image pipelines, and optional Performance Mode toggles for smooth scrolling on mid-range devices.
- **Encrypted Backups & In-App Updates**: Local backup/restore and automatic GitHub Releases update checks with SHA-256 verification.

## Installation

1. Open the [**GitHub Releases**](https://github.com/ExZyO/OmniTrack/releases/latest) page.
2. Download the latest `OmniTrack-v*.apk` release build (or `*-debug.apk` if you want ADB diagnostics).
3. Install the APK on Android 8.0+ (API 26+).

## Building & OAuth Configuration

OAuth client credentials are injected via `local.properties` or GitHub Actions Secrets (`TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET`, `MAL_CLIENT_ID`, `SIMKL_CLIENT_ID`, `SIMKL_CLIENT_SECRET`, `BANGUMI_CLIENT_ID`, `BANGUMI_CLIENT_SECRET`, `COMICK_CLIENT_ID`). All OAuth redirect callbacks use the `omnitrack://auth/<service>` deep-link scheme.

## Credits & Acknowledgments

OmniTrack draws inspiration from and adapts ideas and open-source mechanics from these great GPL-3.0 Android projects:

- [**NuvioMobile**](https://github.com/tapframe/NuvioStreaming) — hero carousel crossfade/parallax scroll motion and visual polish concepts.
- [**Chimanew**](https://github.com/chimanew) / Tachiyomi / Mihon ecosystem — library display/filter sheet organization and Compose/Coil performance optimizations.

## License

OmniTrack is free software licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE) for details.
