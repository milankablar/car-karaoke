# AGENTS.md

## Commands

- `./scripts/check.sh` — full local/CI verification gate (Gradle wrapper SHA-256, Kotlin formatting, unit tests, Android Lint, debug APK build, Obtainium config verification, and Python script syntax check)
- `./scripts/check.sh --format` — auto-format Kotlin sources with Spotless/ktlint
- `bash gradlew testDebugUnitTest` — run unit and invariant tests
- `bash gradlew connectedDebugAndroidTest` — run instrumentation tests (requires emulator or device)
- `python3 scripts/obtainium.py` — regenerate `distribution/obtainium.json`, `distribution/obtainium-link.txt`, and `app/src/main/assets/obtainium.json`
- `python3 scripts/release.py tag` — validate and preview a release tag (pass `--apply` to create and push the tag)

`scripts/check.sh` auto-detects Homebrew OpenJDK 17 and `~/Library/Android/sdk` on macOS when `JAVA_HOME` or `ANDROID_HOME` are unset. When invoking `gradlew` directly, ensure Java 17 and an Android SDK with API 36 and build-tools `35.0.0` are configured.

## Architecture

Single Android module (`app/`) in Kotlin + Jetpack Compose (Material 3). Release package is `io.github.milankablar.carkaraoke`; debug builds append `.dev`.

- **Android entrypoints & container:** `CarKaraokeApplication` (owns `AppContainer`), `MainActivity`, `MediaBridgeService` (Android Auto `MediaBrowserServiceCompat`), `MediaNotificationListener`.
- **Playback & media bridge:** `MediaBridgeSessionManager`, `MediaControllerManager`, `MediaInformationRetriever`, `MediaStateUpdater`, `MediaBridgeMediaCallback`, `MediaClientValidator`.
- **Karaoke core (`karaoke/`):** `KaraokeCoordinator` (pure Kotlin `StateFlow<KaraokeState>`), `TrackIdentity`, `PlaybackClock`, `LyricMatcher`, `EnhancedLrcParser`, `CarLyricsPresenter`, `RecordingLyricsRepository`, `CandidateProvider`, `RefreshGate`.
- **Phone UI (`ui/`):** `CarKaraokeApp` (sealed `AppRoute` shell), `KaraokeScreen`, `SettingsHomeScreen`, `CorrectionScreen`, `PlayerSelectionScreen`, `CarDisplayScreen`, plus library/provider/backup/diagnostics screens.
- **Storage, settings, backup & diagnostics:** `SettingsManager` (single typed preference store), `lyrics/` (`LyricsStorage`, `LyricsRepository`, `LrcFormat`, `LyricsCleanupManager`, `providers/`), `backup/`, `diagnostics/`.

See [docs/architecture.md](docs/architecture.md), [docs/testing.md](docs/testing.md), and [docs/releases.md](docs/releases.md) for current design, test status, and release workflow details.

## Rules

- **Verification gate:** Run `./scripts/check.sh` before finishing any change.
- **Pure domain isolation:** Pure domain files (`KaraokeCoordinator.kt`, `KaraokeModels.kt`, `EnhancedLrcParser.kt`, `CarLyricsPresenter.kt`, `RefreshGate.kt`) must never import `android.*` or `androidx.*` (enforced by `ArchitectureInvariantsTest`).
- **UI boundary:** `ui/` must never construct `OkHttpClient` or `ConfiguredCandidates`, or call `getSharedPreferences` directly—delegate to `AppContainer` and `SettingsManager` (enforced by `ArchitectureInvariantsTest`).
- **Fork hygiene:** Never reintroduce upstream `com.gululu.aamediamate` references (enforced by `ArchitectureInvariantsTest`). Keep `upstream` read-only for fetching; always pass `--repo milankablar/car-karaoke` on `gh` mutations.
- **Vehicle independence:** Never add vehicle-specific APIs, layouts, package checks, or timing assumptions.
- **Dependency hygiene:** Declare all versions in `gradle/libs.versions.toml`; do not leave unused libraries in `app/build.gradle.kts`.
- **Secrets & signing:** Never commit keystores or secrets. Release signing reads `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` from the environment. Never move or overwrite published release tags/APKs.
- **Commits:** Imperative subject (<= 50 chars) explaining what/why; no `Co-Authored-By` trailers.
