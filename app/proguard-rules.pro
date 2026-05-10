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
# PDF RENDERER (VERY IMPORTANT)
############################################

-keep class android.graphics.pdf.** { *; }

############################################
# YOUR CORE APP CLASSES (IMPORTANT)
############################################

-keep class neunix.pageflow.PdfCore { *; }
-keep class neunix.pageflow.PdfPageAdapter { *; }
-keep class neunix.pageflow.PdfActivity { *; }

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