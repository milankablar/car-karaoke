Car Karaoke's first public release: a vehicle-independent fork of AAMediaMate with a new phone interface and shared phone/car lyric engine.

- Multiline, automatically scrolling phone lyrics; real word timestamps from enhanced LRC files.
- Android Auto Live lyrics destination with a rolling three-line window, shared song corrections, and an optional car-only timing offset.
- Recording-aware lyric matching and cache keys; ambiguous matches require a preview and selection. Late responses cannot replace the current song's lyrics.
- Clean Material 3 settings, light/dark/black themes, lyric size, reduced motion, source selection, LRC import, and saved corrections.
- Signed GitHub releases, one APK, checksums, and a preconfigured Obtainium import link.

This is an initial release. Automated tests and an Android 16 emulator were used; Pixel 8 Pro/Android 17, Mazda CX-90, Desktop Head Unit, real music-app coexistence, and screen-locked long-session behavior still need physical acceptance testing. Android Auto controls its own layout and refresh behavior. Online lyrics are line-timed when available; word highlighting requires real word timestamps. Plain lyrics remain untimed.

Install through Obtainium using the link in the README so Obtainium can manage subsequent updates. The APK here is the same signed universal build.
