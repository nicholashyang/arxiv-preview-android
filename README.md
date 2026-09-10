# arXiv Preview

A local-first Android app for discovering, searching, saving, and reading arXiv papers.

## Features

- Follow any active arXiv category and browse a daily feed.
- Search titles, abstracts, authors, and arXiv IDs.
- Save favorites locally with Room.
- Read PDFs in-app with Jetpack PDF, or fall back to a browser.
- Manually download PDFs for offline reading.
- Optional daily notifications for new submissions.
- Current app version in Settings, manual update checks, and APK download/install from GitHub Releases.
- Optional automatic app updates: check daily on an unmetered network, download, and notify when ready.

The UI is English-only in version 1. Paper metadata is shown as published by arXiv.

## Build

Requirements:

- JDK 17
- Android Studio with Android SDK 37 and Build Tools 36.0.0

Create `local.properties` with your Android SDK location if Android Studio does not create it,
then run:

```shell
./gradlew testDebugUnitTest assembleDebug
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

Settings displays the installed `BuildConfig.VERSION_NAME` and `VERSION_CODE`. **Check for updates**
reads the latest stable release from
[`nicholashyang/arxiv-preview-android`](https://github.com/nicholashyang/arxiv-preview-android/releases).
Choose **Download**, then **Install**. Android may first ask you to allow installs from this app;
returning from that permission screen continues to the system installer. Cancelling installation
leaves the download available for another attempt.

**Automatic app updates** is off by default. Enabling it schedules unique daily WorkManager work
on Wi-Fi or another unmetered network, with sufficient free storage. Checks and downloads run in
the background; timing is approximate because Android schedules the work. A ready-update
notification opens Settings. Downloads still work if notification permission is declined, and
the ready APK remains in Settings. Disabling the switch cancels automatic work; manual updates
remain available. Android requires confirmation to install an APK; this is not silent installation.

Publishing compatible updates:

1. Increase both `versionCode` and the numeric `versionName` in `app/build.gradle.kts`.
2. Tag a stable GitHub release `v<versionName>` (for example, `v1.1.0`). Numeric versions are
   compared by component, so `1.10.0` is newer than `1.9.0`, and `1.0` equals `1.0.0`.
3. Upload a signed universal APK named `arxiv-preview-v<versionName>.apk` or
   `arxiv-preview-v<versionName>-universal.apk`. A single `arxiv-preview-*.apk` is also supported,
   including the existing `-preview.apk` naming. Unsigned and ambiguous sets of APKs are rejected.
4. Use the same signing certificate as the installed app. The first public preview was debug-signed;
   an update to that preview must use that same debug key. A new signing key cannot replace it.
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
