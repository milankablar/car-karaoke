Car Karaoke 0.1.1 improves setup and media refresh reliability.

- The app’s update setting now opens the preconfigured Obtainium import directly, with a browser fallback when Obtainium is not installed.
- Bursts of music-app callbacks retain the earliest pending refresh deadline. Continuous notifications can no longer postpone song-state reads indefinitely.
- Manually selected lyrics can display when a player provides a song title but omits artist metadata; automatic matching remains conservative.
- The same package and signing certificate allow updates from 0.1.0 while preserving settings, cached lyrics and manual corrections.

The first release introduced shared phone/car karaoke, recording-aware matching, LRC import and the redesigned Android interface. Physical Pixel 8 Pro/Android 17 and Android Auto/DHU acceptance remain pending. See the README and test matrix for verified coverage and setup instructions.
