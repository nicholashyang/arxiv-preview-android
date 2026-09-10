# arXiV 1.2.0 validation

Validated on 2026-09-11 with the existing Android SDK 37 build toolchain and an Android 15 / API 35 ARM64 emulator with SDK extension S = 13.

## Automated checks

`./gradlew testDebugUnitTest lintDebug assembleRelease connectedDebugAndroidTest`

- 28 JVM unit tests passed; 0 failures, errors or skips.
- 16 Android instrumentation tests passed; 0 failures, errors or skips.
- Android lint: 0 errors, 32 warnings (dependency/tooling update notices, KTX/style suggestions, and existing API/kapt notices).
- Optimized release build passed with R8 and resource shrinking enabled.
- New tests cover legacy identifier normalization, capability gating, theme persistence, data migration retaining favorites/downloads/feed entries, real native PDF document loading and fragment recreation, basic reader navigation/page restoration, offline KaTeX/fonts, untrusted markup and unsupported expressions, HTML math/images/theme, anchor/PDF navigation, scroll restoration, and 404/retry recovery.
- The migration test builds the old schema from its checked-in v1 export, then opens it with the current Room database to verify both schema compatibility and preserved relations.

## PDF crash reproduction

The v1.1.0 APK crashed opening a valid two-page offline PDF:

```
java.lang.IllegalStateException: Can't access ViewModels from detached fragment
  at androidx.pdf.viewer.fragment.PdfViewerFragment.setDocumentUri(...)
  at com.example.arxivpreview.ui.PdfScreenKt.EmbeddedPdfViewer(...)
```

After fixing attachment order, validation exposed missing native Material theme attributes and update scheduling incorrectly starting in the PDF service process. All three causes are addressed. Native PDF regression tests require a successful document-load callback with the correct page count, rather than merely an attached view.

## Signed release and upgrade

- APK: `arxiv-preview-v1.2.0.apk`
- Package: `com.example.arxivpreview`; version name `1.2.0`; version code `3`; minimum Android API `31`.
- Signing certificate SHA-256: `ffd792554e3fb6389ef1d943a25a51c0fbd6b66c61cbfd267656dc099f5fdac8` — matches the published v1.1.0 APK.
- APK SHA-256: `780d2a4ce4f58513fea76b6fbbd418676c825c75f7cba87626c72ea5d0c63b1a`.
- Installed the final optimized, signed APK over v1.1.0. The onboarding choice, three favorites (including a legacy ID), and the offline PDF survived. The final APK opened the same PDF that crashed the old app.
- The basic reader retained page 2 through landscape rotation and return to portrait.
- Verified Light, Dark, Follow system, cold restart, and enlarged system text. PDF page colors remain unchanged.
- Opened the real official HTML page `https://arxiv.org/html/2501.12948v1` in the signed release. Direct emulator networking failed; enabling the existing host proxy for this test allowed loading and scrolling. Network failure and retry UI worked. No proxy settings are embedded in the app.

## Visual checks and limits

Screenshots in [screenshots](screenshots) were captured from the final signed release. Reader/legacy demo entries are synthetic test data; the DeepSeek entry links to the official paper. Screenshots show light/dark favorites and settings, formula-rich details, native/basic PDF, and real HTML reading. The debug-only test activity is absent from the release APK.

Compatibility gating is unit-tested below SDK extension 13, and the basic reader was exercised on the emulator. A second physical or low-extension device was not available. HTML availability and source conversion errors depend on arXiv. This release does not add offline HTML, complete TeX compilation, or basic-reader text search/selection.
