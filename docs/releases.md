# Releases and Obtainium

The GitHub Actions Release workflow runs for `car-karaoke-v*` tags on this fork. It verifies main-branch ancestry, a unique increasing semantic version/versionCode, the official Gradle wrapper checksum, release unit tests, lint and APK assembly. It checks the APK package/version, rejects debug builds, verifies the permanent signing certificate, writes checksums and release metadata, uploads a draft, then publishes it.

For a new release:

1. Update both fields in `version.properties`; never reuse or decrease versionCode.
2. Update `docs/release-notes.md`, run checks, commit and push `main`.
3. Run `python3 scripts/release.py tag` using an authenticated `gh` CLI.
4. Follow the Release workflow in GitHub Actions. A failed build never publishes a release. If publishing failed after a draft was created, inspect and finish that draft; the guard intentionally refuses overwriting an existing release.

One universal `car-karaoke-X.Y.Z.apk` is published with `SHA256SUMS` and `release-metadata.json`. The same package and certificate are used for every update. Upstream tags do not trigger releases. The development package cannot overwrite the release package.

GitHub signing secrets are `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`. The keystore is created outside the checkout and removed from the Actions runner after use. Only the public SHA-256 certificate fingerprint is committed. Keep an off-device recovery copy of the private key and its passwords; losing it prevents updates to existing installations. Do not rotate the key casually.

`python3 scripts/obtainium.py` regenerates the reviewed JSON and web-safe import link. The configuration selects this repository, stable Car Karaoke releases, one precisely named APK, and extracts the semantic version from the namespaced tag. `additionalSettings` is encoded as a JSON string as required by Obtainium's App import model. No GitHub token is included. The companion QR encodes the same link.

Install Obtainium from its official project, open our import link, confirm the configuration, and let Obtainium perform the first Car Karaoke installation. Grant Android's install-source permission when prompted. After installation enable Car Karaoke's music notification access. Some sideloading paths may require Android's Allow restricted settings action in app info before that permission can be granted.

For background updates, enable Obtainium's update checks and background updates. Android 12+ is one prerequisite for silent installs; installer ownership, target SDK, package selection and system scheduling also apply. Our automation prepares and publishes updates, but cannot bypass the phone's initial permission/import confirmations.

Official references: [Obtainium deep links](https://wiki.obtainium.imranr.dev/deep_links/), [source options](https://wiki.obtainium.imranr.dev/sources/), [update behavior](https://wiki.obtainium.imranr.dev/app_tracking/), [Android Auto testing](https://developer.android.com/training/cars/testing).
