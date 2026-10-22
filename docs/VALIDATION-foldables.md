# Foldable and large-screen validation

Validated on 2026-10-23. Release date **2026-10-23**. Public version labels are replaced by dates; the APK filename is fixed as `arxiv-preview-universal.apk`.

## Layout behavior

- Compact windows retain bottom navigation. Windows at least 600dp wide and 480dp high use an 80dp navigation rail; HTML/PDF readers use the full available content area.
- Explore, category Latest, Library, Inbox, author profiles, and author identity results share a list/detail browser. With no separating fold, it displays two panes from 760dp of content width (840dp including the rail) and 480dp height. The list is between 320dp and 440dp wide. The current paper is highlighted.
- `WindowInfoTracker` supplies separating folds and occluding hinges. Feature coordinates are translated from window coordinates into each layout's coordinates, including the rail offset. Each side leaves 12dp of clearance. Book posture requires at least 320dp on each side; tabletop posture requires 600dp content width and at least 240dp per pane. Otherwise, content uses the larger safe region.
- Home, onboarding, settings, research preferences, and standalone details limit content width to 840dp. Readers avoid a separating/occluding hinge without imposing that width limit.
- Selection is saved per navigation entry. List UI is saved when a compact detail replaces it. Back closes the selection first. A retained detail model store holds only the current paper rather than accumulating a model for every selection.
- Release titles and the About screen use dates; update prompts and notifications omit version numbers. A calendar build ID keeps the existing update comparison and APK validation compatible with older installations, while the APK uses the fixed filename `arxiv-preview-universal.apk`.

## Verification

- 49 JVM tests passed, including six layout-policy tests for compact/expanded windows, density, width boundaries, book/tabletop posture, small safe regions, zero-width creases, and out-of-window folds. Calendar release tests cover upgrades from legacy versions, same-day update ordering, fixed APK filenames, and strict date formatting.
- All 61 Android instrumentation tests passed on an API 36 emulator. The five new tests cover compact/expanded transitions, saved filter input and detail scroll position, book/tabletop hinge bounds, safe reader bounds, and selected-paper/filter restoration after activity recreation and Back. The existing suite covers discovery, Home/category navigation, search, readers, actions, persistence, and app updates.
- The navigation test also passed at an expanded 1800 × 1400 px / 320dpi window. Both the list and details were displayed alongside the navigation rail. Compact validation used 1080 × 2400 px / 420dpi. The emulator's previous size and density were restored after testing.
- Debug and optimized release builds passed. Debug lint reported zero errors and 35 existing warnings. The About screen was checked visually: it displays **2026-10-23** and omits the internal build identifiers. `git diff --check` passed.

## Screenshots

- [Expanded Library with detail pane](screenshots/foldable-library-expanded.png)
- [Compact selected-paper view](screenshots/foldable-library-compact.png)
- [About screen with build date](screenshots/build-date-settings.png)

Screenshots use deterministic local paper fixtures. Hinge tests inject separating-fold coordinates into the production layout code. Physical foldable hardware and OEM WindowManager extension delivery have not been tested.

## Release APK

- Fixed asset name: `arxiv-preview-universal.apk`; 6,185,254 bytes.
- Optimized, non-debuggable release APK. Signature verification and 16 KiB ZIP alignment checks passed.
- SHA-256: `7d7970e3fcc3a96cf292ce5d42ede84e1b65573cb5f27c6d74178fd8be89684b`.
- Signing certificate SHA-256: `ffd792554e3fb6389ef1d943a25a51c0fbd6b66c61cbfd267656dc099f5fdac8`, matching the previously published APK.
- Installed the previous public APK without clearing app data, then upgraded to the signed date release. Cold launch succeeded, the `cs.AI` category was preserved, and onboarding did not repeat.
