# Project: BitChord Kotlin Multiplatform Setup (Android + Desktop)

## Architecture
BitChord is a Kotlin Multiplatform (KMP) music streaming and playback application.
- **`:shared`**: Core multiplatform business logic and models.
  - `commonMain`: 100% pure Kotlin standard library and DSP math (Models, EqualizerCurve, PeakLimiter, TransitionPlanner). Targets all platforms.
  - `jvmSharedMain`: Shared JVM code for Desktop and Android (YouTube parsing, InnerTubeX, NewPipeExtractor, lyrics, OkHttp, Jsoup).
  - Target platforms: Desktop JVM (`jvm`) and Android (`androidTarget()`). The Apple targets were cut — see the iOS row in the README.
- **`:sharedUi`**: Mobile/multiplatform Compose UI library used by `:app` (Android) and future mobile clients.
- **`:desktopUi`**: Desktop-isolated Compose UI library for Windows, Linux, and macOS.
- **`:desktopApp`**: Desktop application entry point, native packaging (MSI, DEB, RPM, DMG).
- **`:app`**: Android application module (MainActivity, PlaybackService, Media3, Android widgets, DSD native decoder).
- **CI Workflows**: GitHub Actions workflows building Windows, Linux, macOS, and Android (APK).

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Multiplatform `:shared` Targets | Configure `androidTarget()` and iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`) with `BitChordShared` XCFramework export in `shared/build.gradle.kts`. **iOS later cut** — the targets and the XCFramework export are gone; `:shared` is Android + JVM. | M1 | Survey (Explorer 2) |
| 2 | Ktor Multiplatform Client Setup | Keep pure common networking in `commonMain` with `ktor-client-core` and platform engines (`okhttp` for JVM/Android, `darwin` for iOS, `cio` for desktop). | M1 | Survey (Explorer 2) |
| 3 | Root Build & Settings Setup | Add R8 classpath (`8.13.23`), declare AGP 8.10.1 plugins, add version extra properties, update `gradle.properties`, and configure SDK detection in `settings.gradle.kts`. | M1 | Survey (Explorer 1) |
| 4 | Restore Historical `:app` Module | Restore `:app` from commit `8b1a112a` without overwriting existing working tree changes. | M1 | Survey (Explorer 1) |
| 5 | Restore Historical `:sharedUi` Module | Restore `:sharedUi` from commit `8b1a112a` to satisfy `:app` UI dependencies while preserving `desktopUi`. | M1 | Survey (Explorer 1) |
| 6 | Dead Dependency & Native Cleanup | Clean `CMakeLists.txt` (remove deleted `analyzer`), relocate `WearProtocol.kt` into `app/`, preserve `stripNewPipeExtractorUtils`, and align versions with `gradle.properties`. | M1 | Survey (Explorer 1) |
| 7 | CI Android Workflow Integration | Add Android build jobs to `.github/workflows/check.yml` (assembleDebug) and `.github/workflows/release.yml` (assembleRelease, APK artifact, SHA256 hashing). | M2 | Survey (Explorer 3) |
| 8 | CI iOS Workflow Integration | Add iOS build jobs to `.github/workflows/check.yml` (simulator framework link) and `.github/workflows/release.yml` (release XCFramework zip, SHA256 hashing, release aggregation). **Both jobs removed** when iOS was cut. | M2 | Survey (Explorer 3) |
| 9 | Working Tree & Desktop Preservation | Ensure all 43 uncommitted UI polish modifications are preserved intact and `:desktopApp:compileKotlin` succeeds cleanly. | M3 | Survey (Explorer 3) |
| 10 | Verification & Forensic Integrity Audit | Complete validation across all targets, CI schema checks, and forensic integrity audit. | M3 | System / Policy |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Multiplatform Targets & Android Restoration | Configure `:shared` for Android and iOS targets with XCFramework (the iOS half since cut); restore `:app` and `:sharedUi` from `8b1a112a`; clean dead deps in CMakeLists and WearProtocol; configure root `build.gradle.kts`, `settings.gradle.kts`, and `gradle.properties`. | none | DONE |
| 2 | CI Workflows Integration (Android + iOS) | Update `.github/workflows/check.yml` and `.github/workflows/release.yml` to build and package Android APK and iOS XCFramework (the iOS job since cut). | M1 | DONE |
| 3 | Verification, Testing & Forensic Integrity Audit | Execute full Gradle task graph verification, desktop compilation, CI schema verification, Challenger empirical testing, and Forensic Integrity Audit. | M1, M2 | DONE |

## Interface Contracts
### `:app` ↔ `:shared`
- `implementation(project(":shared"))`
- Package: `com.music.bitchord.data.model.*` (Song, HomeShelf, SearchResult, UserPlaylist, QueueTier, etc.)
- Package: `com.music.bitchord.data.*` (InnertubeParser, YtMusicRepository, SpotifyClient, etc.)
- Engine: Android runtime uses `androidTarget()` which resolves `jvmSharedMain`.

### `:app` ↔ `:sharedUi`
- `implementation(project(":sharedUi"))`
- Package: `com.music.bitchord.ui.player.NowPlayingScreen`
- Package: `com.music.bitchord.ui.components.SearchField`
- Package: `com.music.bitchord.ui.icons.BitChordIcons`
- Package: `com.music.bitchord.sharedui.resources.Res`

### `:desktopApp` ↔ `:shared`
- `implementation(project(":shared"))`
- JVM variant resolution: `jvm` target variant (`JVM_17`) unchanged.

## Code Layout
- `shared/` - KMP shared library
  - `shared/build.gradle.kts`
  - `shared/src/commonMain/kotlin/`
  - `shared/src/jvmSharedMain/kotlin/`
  - `shared/src/commonTest/kotlin/`
  - `shared/src/jvmTest/kotlin/`
- `sharedUi/` - Mobile / Multiplatform UI library
  - `sharedUi/build.gradle.kts`
  - `sharedUi/src/commonMain/kotlin/`
  - `sharedUi/src/androidMain/kotlin/`
- `app/` - Android Application
  - `app/build.gradle.kts`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/`
  - `app/src/main/cpp/`
  - `app/src/main/res/`
- `desktopApp/` - Desktop Application (Windows, Linux, macOS)
- `desktopUi/` - Desktop UI Library
- `.github/workflows/` - CI workflows (`check.yml`, `release.yml`)
- `gradle.properties` - Centralized version properties and flags
- `settings.gradle.kts` - Gradle project configuration
- `build.gradle.kts` - Root Gradle buildscript
