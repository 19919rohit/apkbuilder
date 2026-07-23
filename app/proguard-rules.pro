############################################
# BASIC ANDROID SAFETY
############################################

-keep class androidx.** { *; }
-keep class com.google.android.material.** { *; }

############################################
# VIEWPAGER / UI (IMPORTANT)
############################################

-keep class androidx.viewpager2.** { *; }

############################################
# GLIDE (CRITICAL - DO NOT OBFUSCATE)
############################################

-keep public class * implements com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.** { *; }
-keep class com.bumptech.glide.load.engine.** { *; }
-keep class com.bumptech.glide.request.** { *; }

-dontwarn com.bumptech.glide.**

############################################
# PDF RENDERER & PDFIUM 2.0.0 (CRITICAL)
############################################

-keep class android.graphics.pdf.** { *; }

# Keep PDFium classes intact for JNI communication
-keep class io.legere.pdfium.** { *; }

# Prevent ProGuard from renaming native C++ method signatures across the app
-keepclasseswithmembernames class * {
    native <methods>;
}

############################################
# YOUR CORE APP CLASSES (IMPORTANT)
############################################

-keep class neunix.pagevibe.PdfCore { *; }
-keep class neunix.pagevibe.PdfPageAdapter { *; }
-keep class neunix.pagevibe.PdfActivity { *; }

############################################
# ANDROID FILE / URI HANDLING
############################################

-keep class androidx.core.content.FileProvider { *; }

############################################
# REMOVE LOGS (OPTIONAL RELEASE CLEANUP)
############################################

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

############################################
# SAFE DEFAULT OPTIMIZATION RULES
############################################

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

############################################
# PREVENT CRITICAL REFLECTION ISSUES
############################################

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

############################################
# REMOVED / CLEANED UP DEPRECATED RULES
############################################
# PDFBox dependencies and rules have been removed 
# as your project now exclusively runs on PDFium.
