package neunix.pageflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor

class PdfCore(private val context: Context) {

    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    fun open(uri: Uri) {

        val file = FileUtils.getFileFromUri(context, uri)

        fileDescriptor = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        )

        renderer = PdfRenderer(fileDescriptor!!)
    }

    fun pageCount(): Int {
        return renderer?.pageCount ?: 0
    }

    fun renderPage(index: Int, width: Int, height: Int): Bitmap {

        val page = renderer!!.openPage(index)

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

    fun close() {
        renderer?.close()
        fileDescriptor?.close()
    }
}