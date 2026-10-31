# Universal Multi-Source Lyrics Engine & Synced Karaoke UI Upgrade

This plan introduces a parallel, fault-tolerant lyrics extraction engine combining **Musixmatch** (word-by-word RichSync, line-synced subtitles, and plain lyrics), **LRCLIB**, **Genius** (search API + HTML lyrics container extractor), **Lyrist / Lyrica API**, and **YouTube Music InnerTube** fallback so lyrics resolve reliably across global and regional tracks. It pairs this engine with an upgraded **word-by-word and line-by-line synced lyrics UI** and interactive **provider badges & source switching** in the player and manual lyrics picker.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX choices were confirmed during clarification and govern this implementation:

- **Confirmed Decision 1 — Multi-Source Provider Suite**: Integrate **Musixmatch** (synced RichSync/LRC + plain lyrics), **Genius** (search + HTML lyrics scraper), **Lyrist / Lyrica API**, and **YouTube Music native browse lyrics**, alongside **LRCLIB** in a unified pipeline.
- **Confirmed Decision 2 — Parallel Execution & Unified Scoring**: Query all enabled lyrics providers concurrently with per-provider timeouts and rank all returned candidates using a unified match score (weighing title/artist similarity, duration proximity, script preference, and rich/line sync availability).
- **Confirmed Decision 3 — Provider Visibility & Manual Source Switcher**: Display a live **provider badge** (e.g., `MUSIXMATCH • SYNCED`, `LRCLIB • SYNCED`, `GENIUS • PLAIN`, `YT MUSIC`) in the lyrics header and add **provider filter chips** in the manual Lyrics Browser bottom sheet so users can filter, compare, and pin lyrics from any provider.
- **Confirmed Decision 4 — Word-by-Word & Line-by-Line Synced Lyrics UI**: Upgrade the lyrics viewport to parse both enhanced word-level timestamps (`<mm:ss.xx>word` / Musixmatch RichSync) and standard line-level LRC timestamps (`[mm:ss.xx]`), rendering smooth progressive word/syllable karaoke fill when word timings are available and fluid line-progress glow when line timings are used.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Near-100% Lyrics Coverage**: Eliminates "No lyrics found" dead ends by running a sanitized multi-candidate query across five complementary lyrics sources in parallel—covering mainstream global hits, South Asian / Bangla / Hindi / indie releases, acoustic covers, and YouTube-exclusive uploads.
  2. **Karaoke-Grade Synced Lyrics Experience**: Supports word-by-word progressive fill (Apple Music / Musixmatch style) when rich word-level timestamps exist, plus enhanced line-by-line smooth highlight progression, instrumental interlude indicators, and instant tap-to-seek.
  3. **Full Transparency & Control**: Shows which source supplied the active lyrics and lets users filter by provider (`All`, `Musixmatch`, `LRCLIB`, `Genius`, `Lyrica`, `YT Music`) inside the Browse Lyrics sheet.
- **Target Audience / Persona**: Music listeners who stream diverse catalogs (including regional tracks, remixes, and covers) and want accurate, synchronized lyrics every time without manual trial-and-error.
- **Key Value**: Combines the rich synchronization of Musixmatch and LRCLIB with the massive community catalog of Genius, Lyrica/Lyrist, and YouTube Music in one fast, non-blocking pipeline.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Automatic Parallel Fetch**: When a track starts playing, the player checks local pinned lyrics and cache first; on a miss, it dispatches parallel requests to Musixmatch, LRCLIB, Genius, Lyrist/Lyrica, and YouTube Music, automatically selecting the highest-scoring candidate.
  2. **Live Provider & Sync Mode Badge**: The Now Playing strip inside the Lyrics view displays a compact pill badge showing both sync granularity and provider (e.g., `MUSIXMATCH • WORD SYNC`, `LRCLIB • SYNCED`, `GENIUS • PLAIN`), tappable to open the provider switcher.
  3. **Word-by-Word & Line-by-Line Karaoke Viewport**:
     - **Word-Sync Mode**: When a track has enhanced LRC or Musixmatch RichSync word timestamps, each word in the active line illuminates progressively based on the exact playback position, with subtle scale lift on the currently sung word.
     - **Line-Sync Mode**: When a track has standard line timestamps, the active line displays a smooth progress sweep and glowing Emerald accent bar while upcoming and passed lines recede with depth-graded opacity.
  4. **Provider Switcher in Browse Lyrics Sheet**: Tapping the search/switch button opens the Browse Lyrics bottom sheet featuring horizontal **Provider Filter Chips** (`All`, `Musixmatch`, `LRCLIB`, `Genius`, `Lyrica`, `YT Music`), match confidence percentages, sync type badges (`Word Sync`, `Synced`, `Plain`), and a 2-line lyric preview before pinning.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Atmospheric luxury dark glassmorphism over a dynamically blurred album-art backdrop.
  - *Color Palette & Mood*: Deep obsidian surface overlays (`#121820` at 95% alpha), crisp white active typography (`#FFFFFF` at 100% alpha), muted upcoming lyric lines (`#FFFFFF` at 30%–64% alpha), and signature **Emerald** accents (`#34D399` / `#10B981`) for active sync indicators and provider badges.
  - *Typography & Hierarchy*: Bold expressive display lines (`26sp` ExtraBold for the active line, `22sp` Bold for inactive lines) with generous `34sp` line height and clean `10sp–12sp` uppercase pill badges.
  - *Component Styling & Layout*: Material 3 `ModalBottomSheet`, rounded `18dp` active line highlight cards, `999dp` pill filter chips, and smooth spring-physics auto-scrolling anchored at 28% of the viewport height.
- **Interactive Feedback & Motion**:
  - 60fps word/line highlight interpolation tied to playback progress.
  - Spring-animated scale transition (`dampingRatio = LowBouncy`) when a new lyric line becomes active.
  - Haptic clock-tick feedback when tapping any lyric line to seek or switching providers.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Parallel Multi-Provider Execution with Unified Candidate Ranking**
  - *Chosen Approach*: Fan out requests to all providers concurrently using `supervisorScope` + `async` with an individual per-provider timeout (4.5 seconds), then score and rank all returned `Lyrics` candidates together.
  - *Why*: Sequential waterfall chains feel sluggish when earlier providers time out or miss a regional song, and a single provider might return plain lyrics when another has synced LRC. Parallel execution with unified scoring guarantees both fast response times and the highest-quality lyrics (Word-Synced > Line-Synced > Plain, weighted by title/artist/duration match).
  - *Alternatives Considered*: Strict sequential waterfall (rejected because a slow first provider delays fallback providers and cannot compare match quality across sources).
- **Decision 2: Self-Contained Musixmatch & Genius Extractors (Zero Mandatory User API Keys)**
  - *Chosen Approach*:
    - **Musixmatch Extractor**: Uses Musixmatch's desktop/mobile web API flow (`matcher.track.get`, `track.Search`, `track.subtitle.get`, `track.richsync.get`, `track.lyrics.get`) with automatic anonymous user-token acquisition and caching, plus web page fallback.
    - **Genius Extractor**: Queries Genius's public search endpoint (`/api/search/multi` & `/api/search`) to find the best-matching song page, then extracts and cleans all `<div data-lyrics-container="true">` blocks while stripping contributor headers and editorial annotations.
    - **Lyrist / Lyrica & YT Music Fallback**: Queries Lyrist (`lyrist.vercel.app/api`) / `lyrics.ovh` and YouTube Music's native `next` -> `browse(lyricsEndpoint)` API for tracks uploaded directly to YouTube.
  - *Why*: Works immediately out-of-the-box for every user without requiring manual API key configuration.
- **Decision 3: Enhanced Word-Level & Line-Level LRC Data Model**
  - *Chosen Approach*: Extend the lyrics parser to support both standard `[mm:ss.xx]` line tags and word-timed `<mm:ss.xx>word` / RichSync JSON segments, serializing word timings into enhanced LRC format so existing local caching (`LyricsCache`) and saved lyrics (`SavedLyricsStore`) persist word-by-word sync transparently.
  - *Why*: Avoids destructive database migrations while enabling word-by-word karaoke playback from cache and pinned selections.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        PlayerScreen & LyricsScreen                          │
│  ┌───────────────────────────────┐   ┌───────────────────────────────────┐  │
│  │     LyricsNowPlayingStrip     │   │      LyricsBrowserBottomSheet     │  │
│  │ • Provider + Sync Badge Pill  │   │ • Provider Filter Chips           │  │
│  │ • Romanized / Script Switcher │   │   (All/Musixmatch/LRCLIB/Genius…) │  │
│  └───────────────┬───────────────┘   └─────────────────┬─────────────────┘  │
│                  ▼                                     ▼                    │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                       LyricsMainViewportContent                       │  │
│  │  • Word-by-Word Karaoke Fill (Enhanced LRC / Musixmatch RichSync)     │  │
│  │  • Line-by-Line Sweep & Spring Lift (Standard LRC)                    │  │
│  │  • Musical Interlude Dots & Tap-to-Seek                               │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
└──────────────────────────────────────┼──────────────────────────────────────┘
                                       │ StateFlow<Lyrics?> / List<LyricsSearchResult>
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                   MusicPlayerViewModel & MusicRepository                    │
│  ┌─────────────────────────┐               ┌─────────────────────────────┐  │
│  │    SavedLyricsStore     │               │         LyricsCache         │  │
│  │ (User-Pinned Track ID)  │               │ (Disk Cache w/ Word Timings)│  │
│  └───────────┬─────────────┘               └──────────────┬──────────────┘  │
│              └─────────────────────┬──────────────────────┘                 │
│                                    ▼                                        │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                  MultiSourceLyricsAggregator (Parallel)               │  │
│  │  • LyricsMetadataSanitizer (Title/Artist Cleanup & Romanization)      │  │
│  │  • Unified Scorer (Title 55% + Artist 25% + Sync Bonus + Duration)    │  │
│  └───┬─────────────────┬─────────────────┬─────────────────┬─────────────┘  │
└──────┼─────────────────┼─────────────────┼─────────────────┼────────────────┘
       ▼                 ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐
│ Musixmatch   │  │  LrcLibApi   │  │    Genius    │  │  Lyrica / Lyrist API  │
│  Extractor   │  │  Extractor   │  │  Extractor   │  │  + YT Music InnerTube │
│ (RichSync +  │  │ (Synced LRC  │  │ (Search API  │  │ (REST JSON +          │
│  LRC + Plain)│  │  + Plain)    │  │  + Scraper)  │  │  BrowseEndpoint)      │
└──────────────┘  └──────────────┘  └──────────────┘  └───────────────────────┘
```

- **Data Model & State**:
  - `Lyrics`: Retains `id`, `trackName`, `artistName`, `plainLyrics`, `syncedLyrics` (supporting both standard `[mm:ss.xx]` lines and enhanced `<mm:ss.xx>word` inline tokens), `duration`, `language`, and `source` (`"Musixmatch"`, `"LRCLIB"`, `"Genius"`, `"Lyrica"`, `"YouTube Music"`).
  - `LyricWordSegment(val startTimeMs: Long, val endTimeMs: Long, val word: String)`: Parsed per-word timing slice attached to `LyricLine(val timestamp: Long, val endTimestamp: Long, val text: String, val words: List<LyricWordSegment>, val translation: String?)`.
  - `LyricsProviderFilter`: Enum (`ALL`, `MUSIXMATCH`, `LRCLIB`, `GENIUS`, `LYRICA`, `YOUTUBE_MUSIC`) used in `LyricsBrowserBottomSheet` to filter search results by source.
- **Interactive Component & State Mapping**:
  - **High-Frequency Playback Tick (`LyricsScreen`)**: Calculates `activeLineIndex` and `lineProgress` (`0f..1f`) every playback position update (50ms–100ms cadence during active lyrics view) so word-by-word and line-by-line highlights animate smoothly without jitter.
  - **Multi-Source Search (`LyricsBrowserBottomSheet`)**: Displays all ranked candidates from the parallel providers, lets the user filter by provider chip, script chip, or language chip, and pins the chosen `LyricsSearchResult` into `SavedLyricsStore`.
  - **Graceful Fallback & Error Recovery**: Every extractor runs inside `supervisorScope` with independent exception isolation; if one provider is rate-limited or returns empty results, the aggregator seamlessly ranks results from the remaining providers.
