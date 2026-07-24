# PageVibe

A modern, feature-rich PDF reader for Android with realistic page-curl animation,
a cross-document Page Basket, read-aloud, freehand annotation, and a full
reading-progress dashboard.

## Highlights

- Realistic OpenGL page-curl rendering with a center-reserved pinch-zoom zone
  so zooming and page-flipping never conflict
- Page Basket — collect individual pages from any number of different PDFs
  and export them as one new, standalone merged PDF
- Read Aloud (TTS) with live word-level highlighting and true pause/resume
- Freehand pen + highlighter annotation, per page, five colors
- Full-text search with on-page highlight overlay
- Bookmarks rendered as real page-thumbnail cards
- Library — every PDF ever opened, with custom names and cover images that
  propagate across the whole app instantly
- Reading Stats dashboard with streaks, a 7-day activity chart, and a daily
  motivational quote

## Architecture

Single-Activity shell (`MainActivity`) hosting three persistent Fragments via
`add()` + `hide()`/`show()` (never `replace()`):

    MainActivity
     ├─ HomeFragment      dashboard: insights, continue reading, recents
     ├─ LibraryFragment   full collection grid, search, sort, management
     └─ BasketFragment    cross-document page collection + export

    PdfActivity (full-screen reader)
     ├─ PdfReaderController      authoritative "settled page" state
     ├─ PdfCore (Kotlin)         PDFium rendering, caching, OOM-safe
     ├─ PdfTextExtractor         PDFium text/word extraction, search, TTS data
     ├─ CurlView/Renderer/Mesh/Page   OpenGL page-curl engine
     ├─ GalleryZoomView          gesture arbitration: zoom vs. page-flip
     ├─ DrawingView              annotation layer
     ├─ HighlightOverlayView     search + TTS highlight overlay
     ├─ PdfSearchController / PdfReadAloudController /
     │  PdfBookmarkController / PdfTocController / PdfDrawController
     └─ ReadingStatsController   session-based reading time & streak tracking

Data layer: lightweight SharedPreferences-backed JSON stores, one per
concern — `LibraryManager`, `PageBasketManager`, `ReadingStatsController` —
no local database, no ORM, at this app's scale it isn't warranted.

## Tech Stack

| Layer              | Technology                                            |
|---------------------|--------------------------------------------------------|
| Language            | Java 17-style + Kotlin (rendering core)                |
| PDF engine          | PDFium (`io.legere:pdfiumandroid`)                      |
| PDF writing         | `android.graphics.pdf.PdfDocument` (built-in, zero-dep) |
| Page-curl rendering | Custom OpenGL ES engine                                 |
| TTS                 | Android's native `TextToSpeech`                         |
| Navigation          | `BottomNavigationView` + persistent Fragments           |

## Build

    ./gradlew assembleDebug

Requires: Android Studio (latest), Android SDK 24+, JDK 17.

Key dependency:

    implementation 'io.legere:pdfiumandroid:2.0.1'

## Permissions

| Permission                                   | Why                                              |
|-----------------------------------------------|---------------------------------------------------|
| `ACTION_OPEN_DOCUMENT` (no runtime permission) | Selecting PDFs to open                            |
| `WRITE_EXTERNAL_STORAGE` (API ≤ 28 only)       | Saving Page Basket exports on older Android        |

Android 10+ uses `MediaStore` for Basket exports — no storage permission needed at all.

## Known internal note

The Java package is `neunix.pagevibe` (the app's original working name).
The display name shown to users is now "PageVibe" (`strings.xml` → `app_name`).
A full package rename was intentionally deferred to avoid a partial,
build-breaking rename across files not all touched in the same pass —
do it as one dedicated, complete pass across every source file if desired.

## Robustness principles

- Every native PDF operation (open/render/extract) is defensively wrapped;
  a single corrupt or malicious PDF is quarantined per-page, never retried
  indefinitely, and can never crash the app
- OOM during rendering falls back to a lighter bitmap format automatically
- File-locking around the local PDF cache eliminates races on large-file opens
- Every screen fails gracefully to a sensible default (missing thumbnail,
  missing metadata, failed extraction) rather than an error state

## License

Provided as-is for personal and portfolio use — add your preferred license
(MIT, Apache 2.0, etc.) before publishing.