package neunix.pageflow

import android.content.Context
import android.graphics.Bitmap
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

    fun renderPage(index: Int, width: Int, height: Int): Bitmap {
        val page = renderer!!.openPage(index)

        val bitmap = Bitmap.createBitmap(
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        return bitmap
    }

    fun hasPage(index: Int): Boolean {
        return index < renderer!!.pageCount
    }

    fun close() {
        renderer?.close()
        fileDescriptor?.close()
    }
}