####################################
# General settings
####################################

# Keep source info for stacktraces
-keepattributes SourceFile,LineNumberTable

# Keep annotations
-keepattributes *Annotation*


####################################
# Android generated classes
####################################

# Keep R classes
-keep class **.R
-keep class **.R$* { *; }


####################################
# Android entry points (MANDATORY)
####################################

-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider


####################################
# XML onClick handlers
####################################

-keepclassmembers class * {
    public void *(android.view.View);
}


####################################
# Kotlin support (SAFE)
####################################

-keep class kotlin.Metadata { *; }

-dontwarn kotlin.**
-dontwarn kotlinx.**


####################################
# AndroidX / Jetpack (SAFE warnings only)
####################################

-dontwarn androidx.**
-dontwarn javax.annotation.**


####################################
# Your application code
####################################

# Keep everything in your app package
-keep class neunix.stego.** { *; }


####################################
# StegEngine (extra safety)
####################################

# If StegEngineCore uses reflection or native logic
-keep class neunix.stego.StegEngineCore { *; }


####################################
# Logging (removed in release)
####################################

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}


####################################
# Optimization flags (SAFE defaults)
####################################

-dontoptimize
-dontpreverify


####################################
# End of file
####################################