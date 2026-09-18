# Latest category browsing

Validated on 2026-09-18.

Latest, currently under Explore, now shows the full built-in category catalog grouped by subject. Search matches category codes, names, and subjects. Selecting a category opens a dedicated latest-preprints page sorted by submission date descending. Category browsing does not change followed categories or the advanced-search draft.

Each category page owns its request and pagination state. Refresh replaces the first page and ignores late pagination responses; pagination advances by the API result count while deduplicating displayed papers. Previously cached primary and cross-listed category papers are available when offline. Cache queries match complete category codes and do not modify the shared daily feed.

Validation:

- `testDebugUnitTest`: 41 passed, no failures or skips.
- `lintDebug`: no errors; 21 warnings in existing code/tooling.
- `assembleDebug` and `assembleDebugAndroidTest`: successful.
- Targeted instrumentation on an API 36 emulator: 9 passed, no failures or skips, covering `LatestCategoriesTest`, `LatestNavigationTest`, `LatestStateTest`, `ArxivDatabaseTest`, and `SearchStateTest`.
- `git diff --check`: passed.

The navigation test verifies category selection outside the followed categories, activity recreation, toolbar/system back behavior, and preservation of the directory search. State and database tests cover exact category matching, cross-listings, newest-first cache order, pagination offsets with duplicates, stale responses, cached fallback, retry, and independent category state.

Visual inspection on the emulator confirmed that the [category directory](screenshots/latest/categories.png) and category page header/loading layout render without clipped text or overlapping controls.

Live retrieval could not be verified on this emulator: OkHttp reported `UnknownHostException` resolving `export.arxiv.org` for both the existing Home sync and the new category request. The category request used `(cat:cs.AI)`, offset 0, and submission-date descending order; its failure displayed the expected error and Retry control. Automated data-loading checks use deterministic fixtures.

The checks used the existing local Android SDK, JDK, and populated Gradle cache. No app version, signing configuration, or release was changed.
