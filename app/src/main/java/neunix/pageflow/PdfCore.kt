package neunix.pageflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PdfCore {

    interface BitmapCallback {
        fun onBitmap(bitmap: Bitmap)
    }

    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null

    // SINGLE THREAD ONLY
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    // MAIN THREAD CALLBACK
    private val mainHandler = Handler(Looper.getMainLooper())

    // STRICT PDF LOCK
    private val renderLock = Any()

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

    fun renderPage(
        index: Int,
        width: Int,
        height: Int
    ): Bitmap {

        synchronized(renderLock) {

            val safeRenderer = renderer
                ?: throw IllegalStateException("Renderer closed")

            var page: PdfRenderer.Page? = null

            try {

                page = safeRenderer.openPage(index)

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

                return bitmap

            } finally {

                try {
                    page?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun renderPageAsync(
        index: Int,
        width: Int,
        height: Int,
        callback: BitmapCallback
    ) {

        executor.execute {

            try {

                val bmp = renderPage(
                    index,
                    width,
                    height
                )

                mainHandler.post {
                    callback.onBitmap(bmp)
                }

            } catch (_: Exception) {
            }
        }
    }

    fun close() {

        try {
            renderer?.close()
        } catch (_: Exception) {
        }

        try {
            pfd?.close()
        } catch (_: Exception) {
        }

        executor.shutdownNow()
    }
}