# Discovery, authors, and recommendations validation

Validated on 2026-09-11. This is an unreleased workspace update; no release was published and the app version/signing configuration was not changed.

## Implementation

- Five-tab navigation: Home, Explore, Library, Inbox, Profile. Search, saved-paper organization, reader routes, swipe actions, and update-settings intents continue to use the existing components.
- Author identity selection, cached Semantic Scholar profiles, paper pagination, follow/unfollow, and non-arXiv external links. Name matches are never silently merged into a followed identity.
- Local recommendations with explainable category/topic/author/saved-paper signals, seven-day recent/earlier separation, diversity, exclusions and undo. Optional online results are keyed to the current saved/excluded seed set, so removing the seed does not leave stale personalization active.
- Per-source paginated sync with durable offsets, a silent initial baseline, overlap windows, pending author-paper hydration, merged Inbox reasons, and independent read/archive/feedback state.
- Room schema 4 migration; optional per-user API keys encrypted through Android Keystore under `noBackupFilesDir`.

## Automated checks

Command:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest --offline
```

The SDK and JDK were supplied by the existing local Android runtime. Instrumentation ran on the API 35 ARM64 emulator at 1080×2340, density 440.

Final results:

- **41 JVM tests passed**, zero failures/errors/skips.
- **46 Android instrumentation tests passed**, zero failures/errors/skips.
- **Lint: 0 errors, 34 warnings** (dependency/tooling, deprecation and style notices).
- Debug and optimized unsigned Release APKs built successfully, including R8/resource shrinking.
- `git diff --check` passed.

A full-suite regression exposed a startup race in the shared paper-actions host: the default swipe action could be active before persisted preferences arrived. The host now waits for preferences before rendering interactive cards. The final complete suite passed with this fix.

Coverage includes:

- Chained 1→2→3→4, 2→3→4, and direct 3→4 migrations. Favorites, groups, tags, offline file paths and HTML progress survive; new author/feedback/read/archive states survive database reopening.
- Deterministic local ranking: cold start, recency, topic/author identity signals, version deduplication, saved/dismissed exclusions and restoration, category diversity, empty metadata and finite scores.
- Online recommendation requests use resolved positive/negative paper IDs; non-arXiv results are ignored, arXiv results are hydrated, and removing the last saved seed invalidates online candidates and stops personalized requests.
- Pagination beyond one page, checkpoint retention on failed pages, resumption from the saved offset, initial sync without notifications, newly inserted item counts, duplicate suppression, merged reasons and preservation of read/archive flags.
- Semantic Scholar caching/coalescing, original cache timestamps on stale fallback, 401/403/404, 429 cooldown/retry, cancellation without retry, missing dates and legacy identifiers. URL tests verify legacy IDs remain one encoded path segment.
- Encrypted-key persistence and deletion without plaintext on disk.
- Five navigation destinations, explicit same-name author identity choice, follow state after recreation, Inbox read/archive/restore and notification navigation.
- Existing advanced search, favorite organization, actual swipe gestures/undo, sharing, settings/update reminders, native/basic PDF, math, mobile HTML, and reading-position regression tests.

## Visual checks

Fixture-based UI screenshots were captured in light and dark appearance and at 130% system font scale. Home cards, author links, Inbox actions and navigation labels remain readable; research preferences scroll without clipping their controls.

| Screen | Light | Dark | Large text |
|---|---|---|---|
| Home | [Screenshot](screenshots/discovery/home-light.png) | [Screenshot](screenshots/discovery/home-dark.png) | [Screenshot](screenshots/discovery/home-large.png) |
| Inbox | [Screenshot](screenshots/discovery/inbox-light.png) | [Screenshot](screenshots/discovery/inbox-dark.png) | [Screenshot](screenshots/discovery/inbox-large.png) |
| Research preferences | [Screenshot](screenshots/discovery/research-light.png) | [Screenshot](screenshots/discovery/research-dark.png) | [Screenshot](screenshots/discovery/research-large.png) |

## Live service check and limitations

A public request for author 1741101 returned HTTP 200 with the requested name, affiliations, paper count, citation count and h-index fields. Subsequent public paper requests returned HTTP 429. No personal API key was used, and no attempt was made to bypass the service limit. Deterministic fixtures cover recommendation and error behavior; this run does not establish uninterrupted live recommendation availability.

Author updates depend on Semantic Scholar indexing and valid arXiv mappings. A source processes at most eight pages per refresh and preserves its cursor for continuation; it does not claim complete real-time coverage. Category/topic overlap is two days, author overlap is 30 days. Daily work is scheduled approximately by Android. No backend, account synchronization, revision alerts, reading-duration collection, or physical-device validation was added.

Build outputs:

- Installable development APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Optimized unsigned release intermediate: `app/build/outputs/apk/release/app-release-unsigned.apk`.
