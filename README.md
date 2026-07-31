# arXiv Preview

A local-first Android app for discovering, searching, saving, and reading arXiv papers.

## Features

- Follow any active arXiv category and browse a daily feed.
- Search titles, abstracts, authors, and arXiv IDs.
- Save favorites locally with Room.
- Read PDFs in-app with Jetpack PDF, or fall back to a browser.
- Manually download PDFs for offline reading.
- Optional daily notifications for new submissions.

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

arXiv API requests are serialized with a minimum three-second interval and use small pages, per
the [arXiv API manual](https://info.arxiv.org/help/api/user-manual.html).
