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

    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var cacheFile: File? = null

    private val renderLock = Any()
    private val closed = AtomicBoolean(false)

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "PdfCore-Render").apply { priority = Thread.MAX_PRIORITY }
    }

    private val bitmapCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
        override fun entryRemoved(evicted: Boolean, key: String, old: Bitmap, new: Bitmap?) {
            if (evicted && !old.isRecycled) old.recycle()
        }
    }

    // =========================================================
    // SCREEN SIZE — exposed as getters for Java interop
    // =========================================================

    private var screenWidth  = 1080
    private var screenHeight = 1920

    fun setScreenSize(w: Int, h: Int) {
        screenWidth  = w
        screenHeight = h
    }

    fun getScreenWidth()  = screenWidth
    fun getScreenHeight() = screenHeight

    // =========================================================
    // OPEN / CLOSE
    // =========================================================

    fun open(context: Context, uri: Uri) {
        val file = FileUtils.getFileFromUri(context, uri)
        cacheFile = file
        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
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
    // RENDER
    // =========================================================

    fun renderPage(index: Int, width: Int, height: Int): Bitmap {

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
    // PRE-FETCH
    // =========================================================

    fun prefetchAround(index: Int, range: Int = 2): List<Future<*>> {
        val count   = pageCount()
        val futures = mutableListOf<Future<*>>()

        for (i in (index - range)..(index + range)) {
            if (i < 0 || i >= count || i == index) continue
            val key = "${i}_${screenWidth}x${screenHeight}"
            if (bitmapCache.get(key) != null) continue

            futures += executor.submit {
                if (!closed.get()) {
                    try { renderPage(i, screenWidth, screenHeight) }
                    catch (_: Exception) { }
                }
            }
        }

        return futures
    }

    fun cancelPrefetch(futures: List<Future<*>>) {
        futures.forEach { it.cancel(false) }
    }

    // =========================================================
    // CACHE CONTROL
    // =========================================================

    fun evictExcept(keepStart: Int, keepEnd: Int) {
        val snapshot = bitmapCache.snapshot()
        for (key in snapshot.keys) {
            val idx = key.substringBefore("_").toIntOrNull() ?: continue
            if (idx < keepStart || idx > keepEnd) bitmapCache.remove(key)
        }
    }

    fun clearCache() = bitmapCache.evictAll()

    // =========================================================
    // INTERNAL RENDER
    // =========================================================

    private fun renderPageInternal(index: Int, width: Int, height: Int): Bitmap {
        synchronized(renderLock) {
            val r = renderer ?: throw IllegalStateException("Renderer not open")

            if (index < 0 || index >= r.pageCount) {
                throw IndexOutOfBoundsException(
                    "Page $index out of range [0, ${r.pageCount})")
            }

            val page = r.openPage(index)

            return try {
                val pageW = page.width.toFloat()
                val pageH = page.height.toFloat()
                val scale = minOf(width / pageW, height / pageH)

                val renderW = (pageW * scale).toInt().coerceAtLeast(1)
                val renderH = (pageH * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                val offsetX = ((width  - renderW) / 2f).toInt()
                val offsetY = ((height - renderH) / 2f).toInt()

                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(offsetX.toFloat(), offsetY.toFloat())
                }

                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap

            } finally {
                safeClose { page.close() }
            }
        }
    }

    private inline fun safeClose(block: () -> Unit) {
        try { block() } catch (_: Exception) { }
    }
}