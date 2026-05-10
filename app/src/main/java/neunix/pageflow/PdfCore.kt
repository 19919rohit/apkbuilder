package neunix.pageflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class PdfCore {

    interface BitmapCallback {
        fun onBitmap(bitmap: Bitmap)
    }

    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)

    fun open(context: Context, uri: Uri) {

        val file = FileUtils.getFileFromUri(context, uri)

        pfd = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        )

        renderer = PdfRenderer(pfd!!)
    }

    fun pageCount(): Int = renderer?.pageCount ?: 0

    fun renderPage(index: Int, width: Int, height: Int): Bitmap {

        val page = renderer!!.openPage(index)

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        bitmap.eraseColor(Color.WHITE)

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        page.close()

        return bitmap
    }

    fun renderPageAsync(
        index: Int,
        width: Int,
        height: Int,
        callback: BitmapCallback
    ) {

        if (closed.get()) return

        executor.execute {
            try {
                val bmp = renderPage(index, width, height)

                if (!closed.get()) {
                    callback.onBitmap(bmp)
                }

            } catch (_: Exception) {}
        }
    }

    fun close() {
        closed.set(true)

        try {
            renderer?.close()
            pfd?.close()
        } catch (_: Exception) {}

        executor.shutdownNow()
    }
}