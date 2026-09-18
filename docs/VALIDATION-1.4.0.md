# arXiV 1.4.0 release validation

Validated on 2026-09-18. Version name **1.4.0**, version code **5**.

## Release scope

- Latest category directory and independent category-specific preprint pages.
- Home recommendations, author search/profiles/following, research preferences, and Inbox.
- Room schema 4 migration, preserving favorites, organization, downloaded PDFs, and reading progress.

## Automated checks

Using the existing local JDK 17, Android SDK, and Gradle dependency cache:

```sh
./gradlew testDebugUnitTest assembleDebug assembleRelease connectedDebugAndroidTest --offline
./gradlew lintDebug --offline
```

- 41 JVM unit tests passed; no failures, errors, or skips.
- 53 Android instrumentation tests passed on API 36; no failures, errors, or skips.
- Lint: no errors, 21 warnings in existing code/tooling.
- Optimized release build passed, including R8 and resource shrinking.
- `git diff --check` passed.

Lint was run separately after compilation: running debug lint concurrently with release annotation processing initially hit an Android lint generated-source race. The separate lint run completed successfully.

## Signed APK and upgrade

- Asset: `arxiv-preview-v1.4.0.apk`, 6,103,334 bytes.
- Package: `com.example.arxivpreview`; Android 12 / API 31 minimum.
- Version code/name and the non-debuggable release manifest verified from the final APK.
- APK signatures verified with `apksigner`.
- SHA-256: `9bd6696e156c69e62b6e9181a4c19466ff7113c10174b9af31c38ca536b1892e`.
- Signing certificate SHA-256: `ffd792554e3fb6389ef1d943a25a51c0fbd6b66c61cbfd267656dc099f5fdac8`, matching the downloaded public v1.3.0 APK and previous releases. The historical Android Debug certificate is retained for upgrade compatibility; this APK is an optimized, non-debuggable release build.
- On an API 33 emulator, installed the public signed v1.3.0 APK, selected `cs.AI`, and completed onboarding. Installing signed v1.4.0 with `adb install -r` succeeded without uninstalling. The upgraded app opened Home with the selected category retained and did not repeat onboarding.

## Scope and limitations of validation

The full suite covers database migrations, discovery and author fixtures, category/search state, navigation restoration, paper actions, saved-paper organization, readers, and update behavior. See [discovery validation](VALIDATION-discovery.md) and [category validation](VALIDATION-latest-categories.md) for coverage and screenshots.

The API 36 emulator could not resolve `export.arxiv.org`; live retrieval was therefore not verified there. The existing Home sync and the new category request both encountered the same DNS failure. Category-loading and pagination checks use deterministic fixtures, and the UI correctly exposes retry after a failed live request. Semantic Scholar availability and indexing remain external dependencies.
