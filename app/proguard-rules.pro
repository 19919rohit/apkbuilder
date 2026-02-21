# ----------------------------------------
# ✅ Base ProGuard / R8 Rules for Android
# ----------------------------------------

# Keep line numbers and source file names for debugging (optional)
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ----------------------------------------
# ✅ Common Android & Kotlin rules
# ----------------------------------------
-keep class androidx.** { *; }
-keep class com.google.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Prevent obfuscation of your app's main entry points
-keep public class * extends android.app.Application { *; }
-keep public class * extends android.app.Activity { *; }
-keep public class * extends android.app.Service { *; }
-keep public class * extends android.content.BroadcastReceiver { *; }
-keep public class * extends android.content.ContentProvider { *; }

# Keep your R (resources) classes and prevent their obfuscation
-keep class **.R$* { *; }
-keep class **.R { *; }

# Keep names of methods used in XML layouts (onClick, etc.)
-keepclassmembers class * {
    public void *(android.view.View);
}

# ----------------------------------------
# ✅ Keep annotations (important for Jetpack & DI)
# ----------------------------------------
-keepattributes *Annotation*

# ----------------------------------------
# ✅ Optional: Logging and reflection safety
# ----------------------------------------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ----------------------------------------
# ✅ Optional: Ignore warnings (useful for mixed dependencies)
# ----------------------------------------
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn kotlinx.**
-dontwarn kotlin.**
-dontwarn androidx.**

# ----------------------------------------
# ✅ (Optional) Keep your package entry if dynamic
# (auto replaced by builder.py when changing package)
# ----------------------------------------
-keep class {PACKAGE_NAME}.** { *; }
