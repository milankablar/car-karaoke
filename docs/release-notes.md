Car Karaoke 0.2.0 redesigns the Android Auto Now Playing display and streamlines the app architecture.

- Android Auto Now Playing now displays three live lyric lines (current line in the large title, next two lines underneath) while placing a clean, palette-tinted Song Title + Artist card in the artwork slot instead of noisy album art.
- Added Android Auto display presets (`3-line lyrics`, `Current + Song`, `Song + Current`), an optional album artwork toggle, and configurable dashboard extra buttons (`Seek ±10s` or `Car sync ±250ms`).
- Tightened the Android Auto `Live lyrics` browse tab with a single top song header, tap-to-seek on lyric rows, brief-gap lyric holding, and instrumental countdown cues.
- Prevented sibling/upstream bridge apps from looping into each other when installed side by side.
- Unified presentation preferences into `SettingsManager` (automatically migrating existing 0.1.x settings on upgrade and including them in backups) and removed legacy dead code and unused dependencies.
- Uses the same package and signing certificate for seamless Obtainium updates from 0.1.x while preserving settings, cached lyrics, and manual corrections.
