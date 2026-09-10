# Verification and remaining device acceptance

## Current evidence

- 107 local unit tests pass; lint has no errors.
- Three focused Android 16 emulator integration/UI tests pass: shared phone/car lyrics and corrections, browser-service rows, and phone/car lifecycle ownership plus landscape.
- Signed release APK certificate/package/version checks pass; shrinking reduced the APK to roughly 5.5 MB.
- Obtainium 1.6.15 imported the generated configuration successfully in the emulator. Actual release installation/update status is recorded after publishing.

## Automated checks

Run with Java 17 and an Android SDK containing API 36 and build-tools 35.0.0:

```sh
bash gradlew testDebugUnitTest lintDebug assembleDebug
bash gradlew connectedDebugAndroidTest
python3 scripts/obtainium.py --check
```

The regression suite covers recording identity, incorrect/ambiguous matches, A/B/A out-of-order completion, pause/rate/seek math, LRC offsets and word timestamps, manual override protection, legacy-cache confirmation, offline fallback, provider JSON parsing, backup validation/recovery and inherited bridge behavior. UI instrumentation seeds original synthetic lyrics without a network or music subscription, checks the phone and car presenter agree, exercises settings/themes and per-song correction, and checks lifecycle ownership and landscape layout.

Screenshots in `docs/screenshots` are emulator captures of synthetic test lyrics, not evidence of real music-app or head-unit compatibility.

## Physical acceptance still required

First device: Pixel 8 Pro with Android 17. First car: Mazda CX-90. The app has no Mazda-specific logic.

1. Install the release through Obtainium; grant install-source permission and music notification access. Start your music app and verify the selected player.
2. Reproduce the originally reported wrong-song example. Capture song/artist/album/duration, selected provider, source app/version, and diagnostics. The original report has not yet been reproduced with a real player.
3. Play A → B → C rapidly; return to A; pause/resume; seek backward/forward; repeat the same track; try a live/remastered version and two active music apps. Verify corrections survive restart and offline playback.
4. Connect Android Auto, open Car Karaoke → Live lyrics, and compare the active line with the phone. Confirm browsing refreshes without losing focus. Test touch/rotary where available, split/full display, wired/wireless if available, and different DHU sizes.
5. Close the phone while the car remains connected, then disconnect the car while the phone remains visible. Test screen lock, permission revoke/regrant, reconnect, and a continuous long listening session.
6. Through Obtainium, install a later signed release with a higher versionCode. Verify settings and manual lyrics survive; test manual refresh and then scheduled updates. The OS controls whether background installation is silent.
7. Check large font, dark/black/light, landscape, TalkBack, reduced motion and Android 10 compatibility. Current emulator evidence is Android 16, not Android 17.

Do not treat the presence of a release APK or a browser-service unit test as proof of head-unit compatibility.
