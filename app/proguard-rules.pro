############################################
# NEUNIX STUDIOS - RELEASE R8 RULES
# Optimized for APK size reduction
############################################


############################################
# KEEP ATTRIBUTES REQUIRED FOR REFLECTION
############################################

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod


############################################
# ANDROID COMPONENTS
############################################

# Activities, Services, Receivers, Providers
# Referenced by AndroidManifest automatically
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider


############################################
# VIEW BINDING / DATA BINDING SAFETY
############################################

-keepclassmembers class * {
    public <init>(...);
}


############################################
# VIEWPAGER2
############################################

-keep class androidx.viewpager2.widget.** { *; }


############################################
# GLIDE IMAGE LOADING
############################################

-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * implements com.bumptech.glide.module.GlideModule

-dontwarn com.bumptech.glide.**


############################################
# PDFIUM RENDERER
############################################

# JNI communication
-keep class io.legere.pdfium.** { *; }

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}


############################################
# PDFBOX ANDROID
############################################

# Optional JPEG2000 support
-dontwarn com.gemalto.jp2.**

# PDFBox reflection/classes
-keep class com.tom_roush.pdfbox.pdmodel.** { *; }
-keep class com.tom_roush.pdfbox.filter.** { *; }
-keep class com.tom_roush.pdfbox.cos.** { *; }


############################################
# YOUR CORE CLASSES
############################################

-keep class neunix.pagevibe.PdfCore { *; }
-keep class neunix.pagevibe.PdfPageAdapter { *; }
-keep class neunix.pagevibe.PdfActivity { *; }


############################################
# FILE PROVIDER
############################################

-keep class androidx.core.content.FileProvider { *; }


############################################
# FIREBASE CLOUD MESSAGING
############################################

-keep class com.google.firebase.messaging.** { *; }


############################################
# WEBVIEW JAVASCRIPT INTERFACE
############################################

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}


############################################
# REMOVE LOGGING IN RELEASE
############################################

-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
}


############################################
# ENUM SAFETY
############################################

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


############################################
# OPTIMIZATION
############################################

-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

-allowaccessmodification
-repackageclasses ''


############################################
# REMOVE UNUSED WARNINGS
############################################

-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlin.**