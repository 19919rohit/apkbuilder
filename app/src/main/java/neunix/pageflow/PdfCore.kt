package neunix.pageflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class PdfCore {

    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val isClosed = AtomicBoolean(false)

    fun open(context: Context, uri: Uri) {

        val file = FileUtils.getFileFromUri(context, uri)

        pfd = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        )

        renderer = PdfRenderer(pfd!!)
    }

    fun pageCount(): Int {
        return renderer?.pageCount ?: 0
    }

    // FAST SYNC RENDER (used by ViewPager)
    fun renderPage(index: Int, width: Int, height: Int): Bitmap {

        val r = renderer ?: throw IllegalStateException("Renderer not ready")

        val page = r.openPage(index)

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        bitmap.eraseColor(Color.WHITE)

        page.render(
            bitmap,
            null,
            null,
            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
        )

        page.close()

        return bitmap
    }

    // ASYNC PRELOAD (ONLY for next/prev)
    fun renderPageAsync(
        index: Int,
        width: Int,
        height: Int,
        callback: (Bitmap) -> Unit
    ) {

        if (isClosed.get()) return

        executor.execute {

            try {
                val bmp = renderPage(index, width, height)

                if (!isClosed.get()) {
                    callback(bmp)
                }

            } catch (_: Exception) {
                // ignore safely
            }
        }
    }

    fun close() {

        isClosed.set(true)

        try {
            renderer?.close()
            pfd?.close()
        } catch (_: Exception) {}

        executor.shutdownNow()
    }
}