# Lyrics Share Cleanup & Deep Linking (`raazi.pages.dev` + Web Bundle)

This plan removes the inline share icon button from active lyric lines while preserving long-press lyric quote sharing, configures Android App Links deep linking for `raazi.pages.dev` across songs, playlists, albums, and artists, and generates the ready-to-deploy Cloudflare Pages (`raazi.pages.dev`) deep-link web files directly in the project.

### User Review & Critical Decisions

> [!NOTE]
> **About Cloning External Repositories**: The AI Studio Android build container runs in an isolated sandbox without outbound `git clone` / `git push` access to external GitHub repositories. To make updating `raazi.pages.dev` effortless, we will generate the complete Cloudflare Pages deep-link bundle (`/.well-known/assetlinks.json`, `_redirects`, and the smart deep-link landing page) directly inside your project workspace so you can copy or push them straight into your `raazi.pages.dev` repo.

- **Confirmed Decision 1 (Song URL Formats)**: Support both `https://raazi.pages.dev/play?id=VIDEO_ID` and `https://raazi.pages.dev/watch?v=VIDEO_ID` (plus custom scheme `raazi://`). Outgoing song share links will generate `https://raazi.pages.dev/play?id=VIDEO_ID`.
- **Confirmed Decision 2 (Supported Entity Links)**: Support deep linking directly into **Songs**, **Playlists**, **Albums**, and **Artists**:
  - Songs: `/play?id=<videoId>`, `/watch?v=<videoId>`, `/song/<videoId>`
  - Playlists: `/playlist?list=<playlistId>`, `/playlist/<playlistId>`
  - Albums: `/album?id=<browseId>`, `/album/<browseId>`
  - Artists: `/artist?id=<channelId>&name=<artistName>`, `/artist/<channelId>`
- **Confirmed Decision 3 (Lyrics Line Sharing)**: Remove the inline share `IconButton` on active lyric lines so lyrics stay distraction-free, while keeping long-press copy & share behavior intact.
- **Confirmed Decision 4 (`raazi.pages.dev` Website Files Included)**: Generate the complete Cloudflare Pages deep-link assets (`.well-known/assetlinks.json` with SHA-256 fingerprint, `_redirects` rules for SPA/deep-link routing, and a branded Raazi deep-link redirect/fallback page that auto-triggers `raazi://` and `intent://` on Android or offers APK download if Raazi isn't installed).

---

### 1. Overview & Core Concept

- **What It Does**:
  1. Declutters the synced lyrics view by removing the trailing share icon from the active lyric line while keeping tap-to-seek and long-press quote sharing.
  2. Enables seamless deep linking between `https://raazi.pages.dev` and the Raazi Android app for songs, playlists, albums, and artists—handling both cold starts and warm intents (`singleTop` / `onNewIntent`).
  3. Provides the complete Cloudflare Pages web deep-link handler and Digital Asset Links configuration for `raazi.pages.dev`.
- **Target Audience / Persona**: Listeners sharing songs, playlists, albums, or artist profiles with friends who want links from `raazi.pages.dev` to open directly inside Raazi and start playing or navigate immediately.
- **Key Value**: Cleaner, uninterrupted lyrics reading experience and end-to-end link sharing across `raazi.pages.dev` and Android.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  - **Lyrics View**: While a song plays in synced lyrics mode, each lyric row displays only the vocal text (and optional translation or manual-scroll timestamp pill) without a trailing share icon. Long-pressing any line still copies the formatted lyric quote and opens the system share sheet.
  - **Sharing from Raazi**: Tapping Share in the player or options sheet generates a `https://raazi.pages.dev/play?id=<videoId>` link (and corresponding playlist/artist URLs when shared).
  - **Opening a Link (`raazi.pages.dev`)**:
    - If Raazi is installed and verified via App Links, Android opens Raazi directly without a browser hop.
    - If opened in a mobile browser, the `raazi.pages.dev` deep-link handler page displays a sleek dark-mode Raazi card, automatically triggers `intent://` / `raazi://` to launch the app at that exact song/playlist/album/artist, and provides a one-tap "Open in Raazi" button plus a "Download Raazi APK" fallback button pointing to your GitHub Releases.
- **Visual Identity & Theme**:
  - Preserves the app's luxury dark Material 3 aesthetic (`PureBlack` / `Zinc900` surfaces with `Emerald400` / `Emerald500` accents) across both the Android UI and the `raazi.pages.dev` web deep-link bridge page.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Warm + Cold Deep Link Handling (`onNewIntent` + Reactive Navigation Event)**
  - *Chosen Approach*: Configure the main activity with `singleTop` launch handling and `onNewIntent` forwarding into a reactive deep-link state flow in the ViewModel, consumed by the main navigation host once splash/onboarding finishes.
  - *Why*: Previously, deep links were only inspected once inside a `LaunchedEffect(Unit)` on initial composition, meaning clicking a link while Raazi was already open in the background did nothing, and playlist/artist navigation couldn't reach the `NavHostController`.
- **Decision 2: Dual Verification (`autoVerify="true"` HTTPS App Links + `intent://` / `raazi://` Web Bridge)**
  - *Chosen Approach*: Register verified HTTPS intent filters for `raazi.pages.dev` alongside the `raazi://` custom scheme, paired with a Cloudflare Pages web bridge (`play/index.html` + `_redirects` + `.well-known/assetlinks.json`).
  - *Why*: Ensures links open the app reliably both when Android App Links auto-verification is active and when opened inside in-app browsers (Instagram, Telegram, WhatsApp) that require an `intent://` or `raazi://` redirect.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                Cloudflare Pages (raazi.pages.dev)                       │
│   • /.well-known/assetlinks.json (Android App Links SHA-256)            │
│   • /_redirects (Routes /play, /watch, /song/*, /playlist/*, /artist/*) │
│   • Deep-Link Bridge Page (Auto-fires intent:// & raazi:// + APK CTA)   │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Activity Intent Receiver (Android)                   │
│   • singleTop launchMode + onCreate(intent) & onNewIntent(intent)       │
│   • Forwards Uri to ViewModel.handleDeepLink(uri)                       │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                  Deep Link Parser & Router (ViewModel)                  │
│   • Song (/play?id=, /watch?v=, /song/{id})                             │
│       ──► Fetches InnerTube metadata ──► playMusic() + Expand Player    │
│   • Playlist / Album (/playlist?list=, /playlist/{id}, /album/{id})     │
│       ──► Emits DeepLinkDestination.Playlist(id)                        │
│   • Artist (/artist?id=&name=, /artist/{id})                            │
│       ──► Emits DeepLinkDestination.Artist(id, name)                    │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                   Main Navigation & Player Sheet                        │
│   • Expands BottomSheetScaffold on Song deep link                       │
│   • Navigates NavHostController to playlist/{id} or artist/{id}         │
└─────────────────────────────────────────────────────────────────────────┘
```
