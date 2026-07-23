package neunix.pagevibe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfiumCore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class PdfCore {

    // =========================================================
    // STATE
    // =========================================================

    private var pdfiumCore: PdfiumCore? = null
    private var pdfDocument: PdfDocument? = null
    private var pfd: ParcelFileDescriptor? = null

    private val renderLock = Any()
    private val closed = AtomicBoolean(false)

    // Pages that have failed once (JVM-level exception/error from native
    // code) are marked dead and NEVER retried for the lifetime of this
    // open document. Without this, a single corrupt/malicious page gets
    // re-attempted every time the user scrolls past it, prefetches near
    // it, or the slider passes over it — each attempt being another
    // chance for the native PDFium parser to misbehave.
    private val poisonedPages = ConcurrentHashMap.newKeySet<Int>()

    private val TAG = "PdfCore"

    // =========================================================
    // RENDER EXECUTOR
    // Single thread ensures we don't hammer the native engine
    // simultaneously, preventing native crashes from concurrent access.
    // =========================================================

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "PdfCore-Render").apply { priority = Thread.MAX_PRIORITY }
    }

    // =========================================================
    // PAGE CACHE (DYNAMIC MEMORY SIZING)
    // Limits cache to 25% of the total available Java heap, with a floor
    // so extremely memory-constrained devices don't end up with a cache
    // too small to hold even a single page.
    // =========================================================

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxOf(maxMemoryKb / 4, 8 * 1024)

    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.byteCount / 1024

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            old: Bitmap,
            new: Bitmap?
        ) {
            if (evicted && !old.isRecycled) {
                try {
                    old.recycle()
                } catch (t: Throwable) {
                    Log.e(TAG, "Bitmap recycle failed during eviction: ${t.message}")
                }
            }
        }
    }

    // =========================================================
    // SCREEN METRICS
    // =========================================================

    private var screenWidth = 1080
    private var screenHeight = 1920

    // Hard ceiling on any rendered bitmap dimension. A corrupt or
    // malicious PDF can report an absurd page size in points; without
    // this cap that translates directly into a request for a
    // multi-gigabyte bitmap the instant the page is opened — an OOM (or
    // worse) that happens BEFORE any OOM fallback logic below even runs.
    private val MAX_RENDER_DIMENSION = 4096

    fun setScreenSize(w: Int, h: Int) {
        screenWidth = maxOf(w, 1)
        screenHeight = maxOf(h, 1)
    }

    fun getScreenWidth() = screenWidth
    fun getScreenHeight() = screenHeight

    // =========================================================
    // OPEN / CLOSE
    // =========================================================

    fun open(context: Context, uri: Uri) {
        val file = FileUtils.getFileFromUri(context, uri)

        if (!file.exists() || file.length() <= 0L) {
            throw IllegalStateException("PDF file is empty or not yet available: ${file.length()} bytes")
        }

        var lastError: Throwable? = null
        for (attempt in 1..3) {
            var descriptor: ParcelFileDescriptor? = null
            try {
                descriptor = ParcelFileDescriptor.open(
                    file, ParcelFileDescriptor.MODE_READ_ONLY
                )

                val core = PdfiumCore(context.applicationContext)
                val doc = core.newDocument(descriptor)

                val pages = safePageCount(doc)
                if (pages <= 0) {
                    safeCloseDocument(doc)
                    try { descriptor.close() } catch (ignored: Throwable) {}
                    throw IllegalStateException("PDF opened but reports 0 pages")
                }

                pfd = descriptor
                pdfiumCore = core
                pdfDocument = doc
                poisonedPages.clear()
                closed.set(false)
                return
            } catch (e: Throwable) {
                // Catch Throwable, not Exception — a malformed PDF can trip
                // native asserts that surface as Error subtypes (e.g.
                // OutOfMemoryError), which a plain Exception catch would
                // let through as an uncaught crash.
                lastError = e
                try { descriptor?.close() } catch (ignored: Throwable) {}
                pfd = null
                if (attempt < 3) {
                    try { Thread.sleep(150L * attempt) } catch (ignored: InterruptedException) {}
                }
            }
        }
        throw IllegalStateException("Failed to open PDF after retries", lastError)
    }

    private fun safePageCount(doc: PdfDocument): Int {
        return try { doc.getPageCount() } catch (t: Throwable) { 0 }
    }

    fun pageCount(): Int {
        if (closed.get()) return 0
        val doc = pdfDocument ?: return 0
        return try { doc.getPageCount() } catch (t: Throwable) { 0 }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        try { executor.shutdownNow() } catch (ignored: Throwable) {}
        synchronized(renderLock) {
            val doc = pdfDocument
            try {
                if (doc != null) safeCloseDocument(doc)
            } catch (e: Throwable) {
                Log.e(TAG, "Error closing Pdfium document: ${e.message}")
            } finally {
                try { pfd?.close() } catch (ignored: Throwable) {}
                pdfDocument = null
                pdfiumCore = null
                pfd = null
            }
        }
        try { bitmapCache.evictAll() } catch (ignored: Throwable) {}
        poisonedPages.clear()
    }

    private fun safeCloseDocument(doc: PdfDocument) {
        try { doc.close() } catch (t: Throwable) {
            Log.e(TAG, "doc.close() failed: ${t.message}")
        }
    }

    // =========================================================
    // RENDER (BLOCKING)
    // Guaranteed to return a usable, non-null bitmap and NEVER throw.
    // A failure (corrupt page, native error, OOM) yields a blank white
    // page instead of propagating — callers never need a try/catch
    // around this to stay crash-free.
    // =========================================================

    fun renderPage(index: Int, width: Int, height: Int): Bitmap {
        val safeWidth = clampDimension(width)
        val safeHeight = clampDimension(height)

        if (closed.get()) return blank(safeWidth, safeHeight)
        if (index < 0) return blank(safeWidth, safeHeight)
        if (poisonedPages.contains(index)) return blank(safeWidth, safeHeight)

        val key = "${index}_${safeWidth}x${safeHeight}"

        synchronized(renderLock) {
            try {
                bitmapCache.get(key)?.let { cached ->
                    if (!cached.isRecycled) return cached
                    bitmapCache.remove(key)
                }
            } catch (t: Throwable) {
                // A corrupted cache entry is not fatal — fall through and
                // re-render instead of propagating.
            }
        }

        val bmp = try {
            renderPageInternal(index, safeWidth, safeHeight)
        } catch (t: Throwable) {
            Log.e(TAG, "renderPage($index) failed, poisoning page: ${t.message}")
            poisonedPages.add(index)
            return blank(safeWidth, safeHeight)
        }

        synchronized(renderLock) {
            if (!closed.get()) {
                try { bitmapCache.put(key, bmp) } catch (t: Throwable) {
                    Log.e(TAG, "Cache put failed: ${t.message}")
                }
            }
        }
        return bmp
    }

    private fun clampDimension(v: Int): Int = v.coerceIn(1, MAX_RENDER_DIMENSION)

    // =========================================================
    // PRE-FETCH
    // =========================================================

    fun prefetchAround(index: Int, range: Int = 1): List<Future<*>> {
        val count = pageCount()
        val futures = mutableListOf<Future<*>>()
        if (count <= 0) return futures

        for (i in (index - range)..(index + range)) {
            if (i < 0 || i >= count || i == index) continue
            if (poisonedPages.contains(i)) continue

            val key = "${i}_${screenWidth}x${screenHeight}"
            var alreadyCached = false
            synchronized(renderLock) {
                val cached = try { bitmapCache.get(key) } catch (t: Throwable) { null }
                alreadyCached = cached != null && !cached.isRecycled
            }
            if (alreadyCached) continue

            try {
                futures += executor.submit {
                    if (!closed.get()) {
                        try {
                            renderPage(i, screenWidth, screenHeight)
                        } catch (t: Throwable) {
                            // renderPage() already never throws, but stay
                            // defensive against future changes.
                        }
                    }
                }
            } catch (t: Throwable) {
                // Executor rejected/shutdown mid-prefetch — safe to ignore,
                // this was only ever a performance hint.
            }
        }
        return futures
    }

    fun cancelPrefetch(futures: List<Future<*>>) {
        futures.forEach {
            try { it.cancel(false) } catch (ignored: Throwable) {}
        }
    }

    // =========================================================
    // ASYNC RENDER
    // =========================================================

    fun renderPageAsync(
        index: Int,
        width: Int = screenWidth,
        height: Int = screenHeight,
        onDone: (Bitmap) -> Unit,
        onError: (Exception) -> Unit = {}
    ): Future<*>? {
        return try {
            executor.submit {
                try {
                    if (!closed.get()) onDone(renderPage(index, width, height))
                } catch (e: Exception) {
                    onError(e)
                } catch (t: Throwable) {
                    onError(Exception("Native render failure: ${t.message}", t))
                }
            }
        } catch (t: Throwable) {
            null
        }
    }

    // =========================================================
    // CACHE CONTROL
    // =========================================================

    fun evictExcept(keepStart: Int, keepEnd: Int) {
        synchronized(renderLock) {
            try {
                val snapshot = bitmapCache.snapshot()
                for (key in snapshot.keys) {
                    val idx = key.substringBefore("_").toIntOrNull() ?: continue
                    if (idx < keepStart || idx > keepEnd) {
                        bitmapCache.remove(key)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "evictExcept failed: ${t.message}")
            }
        }
    }

    fun clearCache() {
        synchronized(renderLock) {
            try { bitmapCache.evictAll() } catch (t: Throwable) {
                Log.e(TAG, "clearCache failed: ${t.message}")
            }
        }
    }

    // =========================================================
    // INTERNAL RENDER (WITH OOM + NATIVE-ERROR FALLBACKS)
    // =========================================================

    private fun renderPageInternal(index: Int, width: Int, height: Int): Bitmap {
        synchronized(renderLock) {
            val doc = pdfDocument ?: throw IllegalStateException("Document not open")

            val count = safePageCount(doc)
            if (index >= count) {
                throw IndexOutOfBoundsException("Page $index out of range (count=$count)")
            }

            var page: PdfPage? = null
            try {
                page = doc.openPage(index)

                val rawW = try { page?.getPageWidthPoint() ?: 0 } catch (t: Throwable) { 0 }
                val rawH = try { page?.getPageHeightPoint() ?: 0 } catch (t: Throwable) { 0 }

                val pageW = if (rawW in 1..20000) rawW.toFloat() else 612f
                 val pageH = if (rawH in 1..20000) rawH.toFloat() else 792f

                val scale = minOf(width / pageW, height / pageH)
                val renderW = (pageW * scale).toInt().coerceIn(1, width)
                val renderH = (pageH * scale).toInt().coerceIn(1, height)

                val offsetX = ((width - renderW) / 2f).toInt()
                val offsetY = ((height - renderH) / 2f).toInt()

                var bitmap: Bitmap
                try {
                    // Primary Attempt: High Quality 32-bit ARGB
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    Log.w(TAG, "OOM during ARGB_8888 allocation. Evicting cache and falling back to RGB_565.")
                    clearCache()
                    System.gc()
                    bitmap = try {
                        Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                    } catch (fatalOom: OutOfMemoryError) {
                        Log.e(TAG, "Fatal OOM even on RGB_565 fallback — returning minimal placeholder.")
                        Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
                    }
                }

                bitmap.eraseColor(Color.WHITE)

                try {
                    page?.renderPageBitmap(
                        bitmap, offsetX, offsetY, renderW, renderH, true
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "PDFium failed to render page $index: ${t.message}")
                }

                return bitmap

            } finally {
                if (page != null) {
                    try { page.close() } catch (t: Throwable) {
                        Log.e(TAG, "page.close() failed: ${t.message}")
                    }
                }
            }
        }
    }

    private fun blank(w: Int, h: Int): Bitmap {
        return try {
            val b = Bitmap.createBitmap(maxOf(w, 1), maxOf(h, 1), Bitmap.Config.ARGB_8888)
            b.eraseColor(Color.WHITE)
            b
        } catch (e: Throwable) {
            val b = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
            b.eraseColor(Color.WHITE)
            b
        }
    }
}