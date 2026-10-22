# Apple-inspired interface redesign

Date: 2026-10-23. Calendar build ID: `20261023050200`. Android installation code: `8`.

The app remains a native Android / Jetpack Compose application. The interface follows Apple-style visual conventions using Android system typography and Compose icons.

## Scope

- Shared components in `AppComponents.kt` provide large titles, compact back toolbars, filled search/form fields, segmented controls, inset research groups, subject icons, and empty states.
- The semantic palette uses blue actions, light gray grouped backgrounds, white cards, black dark-mode backgrounds, and coordinated elevated surfaces. Typography, spacing, corner radii, and low-contrast separators are shared across destinations.
- Home presents a date, unique unread-paper count, followed-category count, and category cards with subject icons. Per-category unread counts remain independent.
- Explore and Inbox use segmented navigation. Search, Library, category browsing, and category preferences use clearable search fields. Advanced search and secure-key forms use filled fields.
- Paper cards place actions alongside category metadata without covering the date or title. Details put HTML and PDF actions before the abstract. Author profiles show individual paper/citation/h-index statistics.
- Settings uses grouped surfaces, appearance checkmarks, permission icons, and grouped research preferences. Its tab label is **Settings**.
- Compact screens use a tab bar, medium screens use an 80dp rail, and windows at least 1100dp wide use a 228dp sidebar. Readers keep the available reading area. Existing fold-aware panes, selection restoration, and reading progress remain in place.
- No database migration or change to stored papers, favorites, groups, tags, subscriptions, or downloads is required.

## Validation

- 49 JVM unit tests passed.
- The complete 63-test Android suite passed on the API 36 emulator, including two new tests for segmented-control selection and accessible search submission/clearing. The final suite also covers the independent paper-title click target. The category state-restoration test closes the keyboard before selecting/collapsing groups to avoid moving its pointer target during IME resizing. Rapid Inbox actions settle the initial navigation clock first, and notification routing waits for the destination controls to finish entering.
- The selected-paper/filter restoration test passed at 2400 × 1600 px / 320dpi (labeled sidebar) and at 1800 × 1400 px / 320dpi (icon rail). The emulator was restored to 1080 × 2400 px / 420dpi afterward.
- A wide-layout check exposed a paper-title hit-target ambiguity with the author link. Paper titles now expose their own clickable node. The wide navigation test passes in both layouts.
- Phone screenshots cover light, dark, and 1.3× system font scaling. Formula rendering, HTML/PDF readers, swipe actions, search, persistence, notification routing, and update navigation remain covered by the existing suite.
- Static analysis reports no errors. Remaining warnings include dependency updates, existing Kotlin/SDK suggestions, and a shared-component parameter-order convention.
- `git diff --check` passes.

## Screenshots

These screenshots use deterministic local fixtures or the emulator's test data.

- [Home, light appearance](screenshots/apple-home-light.png)
- [Home, dark appearance](screenshots/apple-home-dark.png)
- [Explore](screenshots/apple-explore.png)
- [Library after upgrading the signed release](screenshots/apple-library-light.png)
- [Inbox](screenshots/apple-inbox-light.png)
- [Settings](screenshots/apple-settings-light.png)
- [Research preferences](screenshots/apple-research-light.png)
- [Library, labeled sidebar and detail pane](screenshots/apple-sidebar-library.png)
- [Library, icon rail and detail pane](screenshots/apple-rail-library.png)

Physical foldable hardware was not available; existing injected-hinge tests cover pane placement and safe reading bounds.


## Release package

- APK: `arxiv-preview-universal.apk`, 6,168,870 bytes; optimized and non-debuggable.
- SHA-256: `a81a90f2784541bf990f4d11c3371a34524c5a7428df62a2ce90cf5357158660`.
- Signing certificate SHA-256: `ffd792554e3fb6389ef1d943a25a51c0fbd6b66c61cbfd267656dc099f5fdac8`, matching the previous public release.
- Signature verification and 16 KiB ZIP alignment checks passed. Package identity, minimum SDK, and internal build identifiers were inspected before publication.
- Installed the previous public APK and then upgraded to the new signed APK without clearing app data. The saved fixture paper and the `cs.AI` subscription remained present, and onboarding did not repeat. The release Library screenshot was captured after this upgrade.
