package neunix.pageflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class PdfCore {

    // =========================================================
    // STATE
    // =========================================================

    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var cacheFile: File? = null

    private val renderLock = Any()
    private val closed = AtomicBoolean(false)

    // =========================================================
    // RENDER EXECUTOR
    // Single thread — PdfRenderer is not thread-safe
    // =========================================================

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "PdfCore-Render").apply { priority = Thread.MAX_PRIORITY }
    }

    // =========================================================
    // PAGE CACHE
    // Keeps rendered bitmaps in memory keyed by "index_WxH"
    // Max 12 MB allocated — LruCache evicts oldest automatically
    // =========================================================

    private val bitmapCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            old: Bitmap,
            new: Bitmap?
        ) {
            if (evicted && !old.isRecycled) old.recycle()
        }
    }

    // =========================================================
    // SCREEN METRICS (set once after surface is known)
    // =========================================================

    private var screenWidth  = 1080
    private var screenHeight = 1920

    fun setScreenSize(w: Int, h: Int) {
        screenWidth  = w
        screenHeight = h
    }

    // =========================================================
    // OPEN / CLOSE
    // =========================================================

    /**
     * Opens a PDF from a Uri. Copies it to cache dir first so
     * PdfRenderer has a real file path (required on all API levels).
     * Safe to call on a background thread.
     */
    fun open(context: Context, uri: Uri) {

        val file = FileUtils.getFileFromUri(context, uri)
        cacheFile = file

        pfd = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        )

        renderer = PdfRenderer(pfd!!)
    }

    fun pageCount(): Int = renderer?.pageCount ?: 0

    fun close() {
        if (!closed.compareAndSet(false, true)) return

        executor.shutdownNow()

        synchronized(renderLock) {
            safeClose { renderer?.close() }
            safeClose { pfd?.close() }
        }

        bitmapCache.evictAll()
    }

    // =========================================================
    // RENDER (blocking, call from background thread)
    // =========================================================

    /**
     * Renders a page synchronously. Returns a cached bitmap if
     * available, otherwise renders and caches it.
     *
     * Width / height are optional — defaults to screen size.
     */
    fun renderPage(
        index: Int,
        width: Int  = screenWidth,
        height: Int = screenHeight
    ): Bitmap {

        if (closed.get()) throw IllegalStateException("PdfCore is closed")

        val key = "${index}_${width}x${height}"

        bitmapCache.get(key)?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        val bmp = renderPageInternal(index, width, height)
        bitmapCache.put(key, bmp)
        return bmp
    }

    // =========================================================
    // PRE-FETCH (fire-and-forget, returns a Future for cancellation)
    // =========================================================

    /**
     * Pre-renders pages around the current index so they are
     * ready in cache before the user flips to them.
     *
     * Call this after every page navigation.
     */
    fun prefetchAround(index: Int, range: Int = 2): List<Future<*>> {

        val count   = pageCount()
        val futures = mutableListOf<Future<*>>()

        for (i in (index - range)..(index + range)) {
            if (i < 0 || i >= count || i == index) continue

            val key = "${i}_${screenWidth}x${screenHeight}"
            if (bitmapCache.get(key) != null) continue   // already cached

            val f = executor.submit {
                if (!closed.get()) {
                    try {
                        renderPage(i, screenWidth, screenHeight)
                    } catch (_: Exception) { }
                }
            }

            futures += f
        }

        return futures
    }

    /**
     * Cancels any queued prefetch work. Call before a large jump
     * (e.g. slider seek) so stale pre-fetches don't block the
     * render you actually need.
     */
    fun cancelPrefetch(futures: List<Future<*>>) {
        futures.forEach { it.cancel(false) }
    }

    // =========================================================
    // ASYNC RENDER (callback on calling thread's executor)
    // =========================================================

    fun renderPageAsync(
        index: Int,
        width: Int  = screenWidth,
        height: Int = screenHeight,
        onDone: (Bitmap) -> Unit,
        onError: (Exception) -> Unit = {}
    ): Future<*> {

        return executor.submit {
            try {
                val bmp = renderPage(index, width, height)
                onDone(bmp)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    // =========================================================
    // CACHE CONTROL
    // =========================================================

    /**
     * Evicts all cached bitmaps that are NOT within [keepStart, keepEnd].
     * Call this after a large slider jump to free memory.
     */
    fun evictExcept(keepStart: Int, keepEnd: Int) {
        val snapshot = bitmapCache.snapshot()
        for (key in snapshot.keys) {
            val idx = key.substringBefore("_").toIntOrNull() ?: continue
            if (idx < keepStart || idx > keepEnd) {
                bitmapCache.remove(key)
            }
        }
    }

    fun clearCache() = bitmapCache.evictAll()

    // =========================================================
    // INTERNAL RENDER
    // =========================================================

    private fun renderPageInternal(
        index: Int,
        width: Int,
        height: Int
    ): Bitmap {

        synchronized(renderLock) {

            val r = renderer
                ?: throw IllegalStateException("Renderer not open")

            if (index < 0 || index >= r.pageCount) {
                throw IndexOutOfBoundsException(
                    "Page $index out of range [0, ${r.pageCount})"
                )
            }

            val page = r.openPage(index)

            return try {

                // Fit PDF page into target dimensions while preserving
                // aspect ratio — centres the content like a real reader
                val pageW = page.width.toFloat()
                val pageH = page.height.toFloat()

                val scale = minOf(
                    width  / pageW,
                    height / pageH
                )

                val renderW = (pageW * scale).toInt().coerceAtLeast(1)
                val renderH = (pageH * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(
                    width, height,
                    Bitmap.Config.ARGB_8888
                )

                bitmap.eraseColor(Color.WHITE)

                // Centre the content on the white canvas
                val offsetX = ((width  - renderW) / 2f).toInt()
                val offsetY = ((height - renderH) / 2f).toInt()

                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(offsetX.toFloat(), offsetY.toFloat())
                }

                page.render(
                    bitmap,
                    null,
                    matrix,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                bitmap

            } finally {
                safeClose { page.close() }
            }
        }
    }

    // =========================================================
    // UTIL
    // =========================================================

    private inline fun safeClose(block: () -> Unit) {
        try { block() } catch (_: Exception) { }
    }
}