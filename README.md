# Raazi Music Player

<p align="center">
  <img src="Raazi.png" alt="Raazi Music Player Logo" width="200"/>
</p>

A modern, privacy-focused open-source Android music player built with **Jetpack Compose** and **Kotlin**. Raazi provides a seamless listening experience by extracting audio directly from YouTube Music, free from ads and tracking.

## Features

*   **Privacy-First**: No account required, no tracking, and no data collection.
*   **YouTube Music Integration**: Search and play any song from YouTube Music's vast library.
*   **Background Playback**: Keep the music playing while using other apps or with the screen off.
*   **Ad-Free**: Enjoy uninterrupted music without advertisements.
*   **Modern UI**: Beautiful, responsive user interface built with Material 3 and Jetpack Compose.
*   **Playlist Management**: Create and manage local playlists.
*   **Offline Support**: (Implementation in progress/supported)

## Tech Stack

Raazi is built using modern Android development tools and libraries:

*   **Language**: [Kotlin](https://kotlinlang.org/)
*   **UI Framework**: [Jetpack Compose](https://developer.android.com/jetbrains/compose)
*   **Architecture**: MVVM with Clean Architecture principles
*   **Concurrency**: [Coroutines](https://github.com/Kotlin/kotlinx.coroutines) & [Flow](https://kotlinlang.org/docs/flow.html)
*   **Dependency Injection**: (e.g., Hilt/Manual - *update if using Hilt*)
*   **Networking**: [Retrofit](https://squareup.com/retrofit) & [OkHttp](https://square.github.io/okhttp/)
*   **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
*   **Local Database**: [Room](https://developer.android.com/training/data-storage/room)
*   **HTML Parsing / Extraction**: [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) (Core component for YouTube Music interaction)
*   **Audio Playback**: [Media3 (ExoPlayer)](https://developer.android.com/media/media3)

## Installation

You can download the latest APK from the [Releases](https://github.com/israrxy/raazi/releases) page.

1.  Download the `Raazi-vX.X.apk` file.
2.  Open the file on your Android device.
3.  Allow installation from "Unknown Sources" if prompted.
4.  Install and enjoy!

## Development

To build the project locally:

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/israrxy/raazi.git
    cd raazi
    ```
2.  **Open in Android Studio**:
    Open the project directory in the latest version of Android Studio.
3.  **Sync Gradle**:
    Let Gradle download all dependencies.
4.  **Run**:
    Connect an Android device or start an emulator and run the `app` configuration.

## License

Raazi is Free Software: You can use, study share and improve it at your will. Specifically you can redistribute and/or modify it under the terms of the **GNU General Public License v3.0** as published by the Free Software Foundation.

This project utilizes the [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) library, which is also licensed under GPL-3.0.

See the [LICENSE](LICENSE) file for more details.

## Disclaimer

This app is for educational and personal use only. It interacts with YouTube Music services. Please respect the terms of service of the content providers. The developers of Raazi are not responsible for any misuse of this application.

## Acknowledgements

*   **NewPipe Team**: For their incredible work on the NewPipe Extractor.
*   **Android Open Source Community**: For the amazing tools and libraries.