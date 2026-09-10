# Browsing, organization, search, and HTML reading validation

Validated on 2026-09-11. The complete suite was rerun for release version 1.3.0 / code 4.

## Automated verification

Command: `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest --offline`

- **35 JVM tests passed**, zero failures/errors.
- **35 Android instrumentation tests passed**, zero failures/errors/skips, on the API 35 ARM64 emulator (1080×2340, density 440).
- **Lint: 0 errors, 32 warnings**, consisting of existing dependency updates, KTX/style, and tooling notices.
- Debug APK and optimized unsigned Release APK both built successfully, including R8/resource shrinking.
- `git diff --check` passed.

Coverage includes:

- Room 2→3 migration and chained 1→2→3 migration preserving favorites, legacy keys, feed records, and downloaded PDF paths.
- Organization creation, case-insensitive duplicate rejection, whitespace validation, group/tag removal, favorite removal, full undo, and independent PDF records.
- Actual Compose swipe gestures below/above the threshold, both directions, configurable/disabled directions across recreation, vertical scrolling, action menu and organization save.
- Structured queries with all/any/phrase matching, escaping, combined fields/categories, UTC date boundaries, empty/invalid input, exact versioned arXiv identifiers and links.
- Search ViewModel late-response isolation, edited-versus-submitted conditions, server-offset pagination despite duplicate papers, and result deduplication.
- Remote request serialization/spacing, cancellation without retry, and the separate `id_list` parameter.
- Intercepted Android chooser intent containing the exact title/versioned HTTPS link; cancellation launches no external share recipient.
- HTML wide-table and long-MathML horizontal scrolling, no page-wide overflow, preserved images/math, 24px font changes without duplicate wrappers, table of contents navigation, find controls, saved position after activity recreation and a fresh reader opening, dark theme, anchor/PDF links, HTML 404 and retry.
- Existing native/basic PDF, math text, content preferences, settings navigation, update reminders, and update scheduler tests.

The tests exposed and drove fixes for duplicate names silently ignored by Room upsert, shifted Settings → About scrolling, and reading restoration/layout timing. The final complete suite passed after those fixes.

## Visual verification

Inspected the installed debug build on the emulator. The main search controls and advanced form fit the phone width; the form scrolls while Apply/Cancel stay accessible. Favorites displays its group, tag, local search, and management controls without overlap.

Screenshots:

- [Search](screenshots/features/search.png)
- [Advanced search](screenshots/features/advanced-search.png)
- [Favorites](screenshots/features/favorites.png)

## Artifacts and limits

- Installable development build: `app/build/outputs/apk/debug/app-debug.apk`.
- Optimized unsigned intermediate: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Signed release asset: `arxiv-preview-v1.3.0.apk`, built from the optimized release variant and verified non-debuggable.
- APK SHA-256: `94579de57d389eea9f8e37ddbc369d0fd7efc16dcdbd4a7c2e44d72ef91220e2`.
- Signing certificate SHA-256: `ffd792554e3fb6389ef1d943a25a51c0fbd6b66c61cbfd267656dc099f5fdac8`, identical to v1.2.0.
- Installed signed v1.2.0, completed onboarding, then installed signed v1.3.0 over it. Upgrade succeeded without uninstalling; saved onboarding preferences survived and the application opened its feed normally.
- HTML behavior was verified using deterministic intercepted article fixtures. Live arXiv availability and conversion quality are external dependencies; this change does not add offline HTML or source-LaTeX conversion.
- No physical device or second Android OS version was available for this validation. Existing PDF compatibility policies remain covered by tests.
- Release includes the completed settings and update-reminder improvements present in the workspace; their regression tests are part of the full suite.
