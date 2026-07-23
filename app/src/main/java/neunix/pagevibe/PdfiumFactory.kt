package neunix.pagevibe

import android.content.Context
import io.legere.pdfiumandroid.PdfiumCore

/**
 * Thin bridge so plain Java callers (PdfTextExtractor.java) can obtain a
 * PdfiumCore instance the same way PdfCore.kt already does.
 *
 * WHY THIS EXISTS: confirmed by the actual build error —
 *   "constructor PdfiumCore.PdfiumCore(Context,Config,PdfiumCoreU) is not
 *    applicable ... constructor PdfiumCore.PdfiumCore() is not applicable"
 * — Java only sees the 0-arg and 3-arg constructors. The convenient
 * PdfiumCore(Context) form only exists via Kotlin default parameter
 * values on Config/PdfiumCoreU, which aren't exposed to Java without
 * @JvmOverloads (this library doesn't have it). Rather than guess the
 * Config/PdfiumCoreU constructor arguments — an unverified surface —
 * this reuses the exact Kotlin expression PdfCore.kt already uses, which
 * this project's own build already proved compiles cleanly.
 */
object PdfiumFactory {
    @JvmStatic
    fun createCore(context: Context): PdfiumCore {
        return PdfiumCore(context.applicationContext)
    }
}