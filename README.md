# arXiV

A local-first Android app for discovering, searching, saving, and reading arXiv papers.

## Version 1.3.0

- Configurable left/right swipe actions with favorite undo.
- Favorite groups, multiple tags, and local filtering.
- Title and versioned-link sharing from lists, details, HTML, and PDF.
- Structured advanced search with field matching, categories, dates, sorting, and exact ID lookup.
- Mobile HTML layout, reading preferences, contents, page search, and persistent reading position.
- Grouped settings and reliable system/in-app update reminders.

See [validation and screenshots](docs/VALIDATION-browsing-reading.md).

## Version 1.2.0

- A classic Apple-inspired interface with large titles, grouped surfaces, restrained separators, and arXiv red accents.
- The arXiv logomark as an adaptive launcher icon and the app name **arXiV**.
- **Settings → Appearance** offers Follow system, Light, and Dark; the choice persists across restarts.
- Offline formula typesetting in paper titles and abstracts throughout Latest, Search, Favorites, and details. KaTeX 0.18.7 and its fonts are bundled; unsupported expressions remain readable as source text.
- Equal **Read HTML** and **Read PDF** actions. HTML uses the official versioned arXiv page, retains images, math and in-page links, follows the app appearance, and restores reading position. HTML requires a connection and is not available for every paper; PDF remains available as an alternative.
- Fixed PDF crashes caused by assigning a document before fragment attachment, missing native view theme attributes, and running update scheduling in the PDF service process.
- The Jetpack PDF reader is used on compatible systems. An in-app basic reader provides offline pages, Previous/Next, pinch zoom, panning and Fit on other devices or if Jetpack loading fails. The basic reader does not offer text selection or full-text search. PDF page colors remain unchanged in dark mode.
- Legacy arXiv identifiers preserve category prefixes. A database migration preserves favorites, feed entries and offline file paths when correcting existing IDs.

## Features

- Follow any active arXiv category and browse a daily feed.
- Search titles, abstracts, authors, and arXiv IDs.
- Save favorites locally with Room.
- Manually download PDFs for offline reading.
- Optional daily notifications for new submissions.
- Current app version in Settings, manual update checks, and APK download/install from GitHub Releases.
- Automatic daily app update checks, system and in-app reminders, and downloads on request.

The interface is English. Paper metadata is shown as published by arXiv. This is an independent reader; third-party assets and their sources are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Browsing and reading improvements

- Swipe any paper in Latest, Search, or Favorites past 35% of its card width and release to execute an action. Settings → Swipe actions configures each direction: toggle favorite, groups/tags, share, download PDF, more actions, or off. Defaults are right = favorite and left = more. Each card also has a visible more button and accessibility actions. Removing a favorite offers Undo for five seconds, including its original group, tags, and saved date.
- Favorites support one group and any number of tags per paper. Manage creates, renames, and deletes groups/tags; deleting a group moves its papers to Ungrouped. Local search combines a group filter and all selected tags. Saving organization for a new paper also favorites it. Downloaded PDFs remain independent of favorites.
- Share opens the Android sharesheet with the title and a versioned official arXiv abstract link. Sharing is available from lists, details, HTML, and PDF; it does not download or attach files.
- Advanced search offers keyword/title/author/abstract fields, all-word/any-word/phrase matching, multiple categories, inclusive submission dates in UTC, and relevance/submission/update sorting. Different fields are ANDed; categories are ORed. Blank date endpoints are unbounded within the supported arXiv range. ID/link searches are exact, preserve versions, and bypass advanced filters. Editing conditions does not change an existing result set until Search/Apply. Pagination uses submitted conditions and protects against late responses.
- HTML defaults to a mobile single-column layout. Reader tools offer Contents, Find in page, and Reading settings (original/mobile layout, font sizes 16–24, and line spacing 1.4/1.6/1.8). Wide equations, tables, and code scroll separately. Reading settings and version-specific reading positions persist locally. Official HTML still requires a connection; unavailable pages retain PDF and retry options. This does not provide offline HTML or LaTeX source conversion.
- Room schema 3 migrates existing data without destructive resets. Previous favorites remain ungrouped; existing PDF paths and legacy identifiers are preserved.

## Build

Requirements:

- JDK 17
- Android Studio with Android SDK 37 and Build Tools 36.0.0

Create `local.properties` with your Android SDK location if Android Studio does not create it,
then run:

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Instrumentation tests require an Android 12+ emulator:

```shell
./gradlew connectedDebugAndroidTest
```

## Architecture

The app is a single-activity Compose project with MVVM-style screen state. `AppContainer` owns
the repositories and platform services. Metadata and user state remain on-device in Room and
DataStore. WorkManager runs durable PDF downloads and the approximate 09:00 daily refresh.

## App updates

Settings groups Appearance, Notifications, App permissions, Content preferences, and About arXiv
on one page. Content preferences groups followed categories by subject, with expandable lists,
search, selected counts, and an explicit Save button. App permissions shows notification and
unknown-app installation access and opens the corresponding Android settings.

About arXiv displays the installed `BuildConfig.VERSION_NAME` and `VERSION_CODE`.
**Check for updates** reads the latest stable release from
[`nicholashyang/arxiv-preview-android`](https://github.com/nicholashyang/arxiv-preview-android/releases).
Choose **Download**, then **Install**. Android may first ask you to allow installs from this app;
returning from that installation flow continues to the system installer. Opening installation
permissions from App permissions only manages access and does not launch the installer.
Cancelling installation leaves the download available for another attempt.

Automatic checks are always enabled, including for users who disabled the former automatic
update switch. Unique daily WorkManager work checks whenever a network is available; timing is
approximate because Android schedules the work. Checks never download an APK. A new-release
notification and an in-app prompt open Settings at About arXiv. Each reminder is remembered per
release and APK, so dismissing the prompt does not repeatedly interrupt reading. The available
update remains in About arXiv. System notification access and the daily-paper notification
preference do not affect checks or in-app prompts. Manual downloads also notify when ready.
Android requires confirmation to install an APK; this is not silent installation.

Publishing compatible updates:

1. Increase both `versionCode` and the numeric `versionName` in `app/build.gradle.kts`.
2. Tag a stable GitHub release `v<versionName>` (for example, `v1.1.0`). Numeric versions are
   compared by component, so `1.10.0` is newer than `1.9.0`, and `1.0` equals `1.0.0`.
3. Upload a signed universal APK named `arxiv-preview-v<versionName>.apk` or
   `arxiv-preview-v<versionName>-universal.apk`. A single `arxiv-preview-*.apk` is also supported,
   including the existing `-preview.apk` naming. Unsigned and ambiguous sets of APKs are rejected.
4. Use the same signing certificate as the installed app. The first public preview was debug-signed;
   an update to that preview must use that same debug key. A new signing key cannot replace it. The v1.2.0 release must keep the same certificate.
5. Publish the release with its uploaded asset and GitHub-generated SHA-256 digest. Drafts and
   prereleases are ignored. APK size, digest, package name, version code/name, Android minimum
   version, and signing certificate are checked before offering installation. The digest and APK
   metadata are checked again immediately before handing the file to the system installer.

Failed checks retain the previous available release and display an error. Failed or interrupted
downloads never expose a partial APK to the installer. Metadata and completed downloads survive
process restarts; obsolete files are removed after successful checks/downloads. No GitHub token
is embedded in the app, so releases must be publicly accessible.

arXiv API requests are serialized with a minimum three-second interval and use small pages, per
the [arXiv API manual](https://info.arxiv.org/help/api/user-manual.html).
