# arXiV 1.5.0 release validation

Validated on 2026-10-23. Version name **1.5.0**, version code **6**.

## Release scope

- Home shows only the categories selected under Setting → Content preferences → Followed categories.
- Category cards open the existing latest-paper pages and show unread counts in the upper-right corner, including zero.
- Counts use synced, unarchived Inbox papers, match complete category codes, and include cross-listings once per category. Opening a paper marks it read across the app.
- Saving categories starts discovery synchronization. Cancelled synchronization clears its progress message.
- Profile is renamed Setting, with a settings icon and updated navigation hints. The grid adapts to large system text sizes.

## Automated checks

- 41 JVM unit tests passed, with no failures, errors, or skips.
- All 56 Android instrumentation tests passed on an API 36 emulator.
- Debug lint: no errors or fatal findings; 35 warnings. Release lint: no errors or fatal findings; 11 warnings.
- Debug, instrumentation APK, and optimized release builds passed. Release includes R8 and resource shrinking.
- `git diff --check` passed.

The full instrumentation run covers persistence, migrations, read/archive/restore behavior, category selection and navigation, recreation, discovery fixtures, search, favorite organization, paper actions, sharing, readers, and app updates. Home was visually inspected in light and dark themes and at 130% font scale; large text uses a wider card layout.

## Signed APK and upgrade

- Asset: `arxiv-preview-v1.5.0.apk`, 6,136,102 bytes; universal APK with ARM and x86 variants.
- Package: `com.example.arxivpreview`; Android 12 / API 31 minimum; target API 37.
- Final APK version code/name verified. `apkanalyzer manifest debuggable` returned `false`.
- APK signature and ZIP alignment verified with `apksigner` and `zipalign`.
- SHA-256: `15f9562f54e2f575aa3fb75584569e3e7ca0185217c900608ed035452f176cc6`.
- Signing certificate SHA-256: `ffd792554e3fb6389ef1d943a25a51c0fbd6b66c61cbfd267656dc099f5fdac8`, verified against the downloaded public v1.4.0 APK. The historical Android Debug certificate is retained for upgrade compatibility; the artifact is an optimized, non-debuggable release build.
- Installed the public v1.4.0 APK on the emulator without clearing app data, then installed the signed v1.5.0 APK with `adb install -r`. Installation and cold launch succeeded. The previously persisted `cs.AI` category appeared on the new Home screen, and the Setting navigation item was present. Onboarding did not repeat; no AndroidRuntime error was reported.

Discovery and network edge cases use deterministic fixtures. This validation does not assert exhaustive live arXiv or Semantic Scholar availability.
