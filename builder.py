#!/usr/bin/env python3
"""
ApkBuilder v7 — Ultimate Android Project Generator & APK Builder
───────────────────────────────────────────────────────────────────
Author: InfiniteProgrammer
"""

import os
import sys
import json,time
import base64
import subprocess
from datetime import datetime
import requests
import zipfile
import io
import requests
import itertools
import shutil

CONFIG_FILE = "builder_config.json"
PROJECTS_DIR = "ApkBuilder/projects"
ASSETS_DIR = "assets"
OUTPUTS_DIR = "outputs"
REPO_URL = "https://github.com/19919rohit/apkbuilder.git"
BUILD_POOL_INTERVAL = 20
BUILD_ESTIMATED_TIME = 180
GITHUB_OWNER = "19919rohit"
GITHUB_REPO = "apkbuilder"
GITHUB_API = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}"
GITHUB_ACTIONS_URL = f"https://github.com/{GITHUB_OWNER}/{GITHUB_REPO}/actions"

# read token from bashrc env (export GITHUB_TOKEN="ghp_xxx")
GITHUB_TOKEN = os.popen("echo $GITHUB_TOKEN").read().strip()

GITHUB_HEADERS = {
    "Accept": "application/vnd.github+json",
    "User-Agent": "ApkBuilder-Termux",
}
if GITHUB_TOKEN:
    GITHUB_HEADERS["Authorization"] = f"Bearer {GITHUB_TOKEN}"

def select_project_if_needed(base_dir):
    """
    Ensures the user is inside a valid project folder.
    If not, lists available projects and asks which one to use.
    Returns the absolute path to the selected project.
    """
    current_dir = os.getcwd()

    # Check if current folder looks like a project (has build.gradle)
    if os.path.exists(os.path.join(current_dir, "build.gradle")):
        print(f"[builder] 📂 Using current project: {os.path.basename(current_dir)}")
        return current_dir

    # If not, check for projects under base_dir/projects/
    projects_dir = os.path.join(base_dir, "projects")
    if not os.path.exists(projects_dir):
        print(f"[builder] ⚠️ No projects directory found at {projects_dir}")
        sys.exit(1)

    projects = [
        p for p in os.listdir(projects_dir)
        if os.path.isdir(os.path.join(projects_dir, p))
        and os.path.exists(os.path.join(projects_dir, p, "build.gradle"))
    ]

    if not projects:
        print("[builder] ⚠️ No valid projects found (missing build.gradle).")
        sys.exit(1)

    print(f"[builder] 📦 {len(projects)} project(s) found:")
    for i, name in enumerate(projects, start=1):
        print(f"  {i}. {name}")

    while True:
        try:
            choice = int(input(f"[builder] 👉 Select project (1-{len(projects)}): "))
            if 1 <= choice <= len(projects):
                selected = projects[choice - 1]
                project_path = os.path.join(projects_dir, selected)
                print(f"[builder] ✅ Selected project: {selected}")
                return project_path
        except ValueError:
            pass
        print("[builder] ⚠️ Invalid input. Try again.")

def find_project_dir():
    """Auto-detect project directory based on current location or config."""
    cwd = os.getcwd()

    # Case 1: inside a project folder
    if os.path.exists(os.path.join(cwd, "app", "build.gradle")):
        return cwd

    # Case 2: inside ApkBuilder root
    if os.path.exists(PROJECTS_DIR):
        projects = [p for p in os.listdir(PROJECTS_DIR) if os.path.isdir(os.path.join(PROJECTS_DIR, p))]
        if projects:
            print(f"📂 Found projects: {', '.join(projects)}")
            choice = input("👉 Which project to build? ").strip()
            if choice in projects:
                return os.path.join(PROJECTS_DIR, choice)
            log("❌ Invalid project name.")
            sys.exit(1)

    # Case 3: fallback via config
    cfg = read_json(CONFIG_FILE)
    if cfg and "app_name" in cfg:
        guess = os.path.join(PROJECTS_DIR, cfg["app_name"])
        if os.path.exists(guess):
            return guess

    log("❌ Could not locate project directory.")
    sys.exit(1)
    

# -------------------------------------------------------------
# Utilities
# -------------------------------------------------------------
# -------------------------------------------------------------
# 🎨 Enhanced Colored Log Function (auto color by message)
# -------------------------------------------------------------
def log(msg):
    """Prints colored logs based on content automatically."""
    RESET = "\033[0m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    RED = "\033[91m"
    BLUE = "\033[94m"
    MAGENTA = "\033[95m"
    CYAN = "\033[96m"
    GRAY = "\033[90m"

    # Auto-detect level from message text
    lower = msg.lower()
    if any(x in lower for x in ["error", "failed", "exception", "could not", "❌"]):
        color = RED
        icon = "❌"
    elif any(x in lower for x in ["warn", "⚠️"]):
        color = YELLOW
        icon = "⚠️"
    elif any(x in lower for x in ["success", "done", "complete", "✅"]):
        color = GREEN
        icon = "✅"
    elif any(x in lower for x in ["download", "build", "creating", "init", "⬇️", "📦"]):
        color = CYAN
        icon = "🔧"
    elif any(x in lower for x in ["info", "ℹ️"]):
        color = BLUE
        icon = "ℹ️"
    else:
        color = MAGENTA
        icon = "📦"

    # Only show colors if terminal supports ANSI
    if not sys.stdout.isatty():
        print(f"[builder] {msg}")
    else:
        print(f"{color}[builder]{RESET} {icon} {color}{msg}{RESET}")

def run(cmd, cwd=None, check=False):
    subprocess.run(cmd, shell=True, cwd=cwd, check=check)

def write_file(path, content):
    dir_path = os.path.dirname(path)
    if dir_path:
        os.makedirs(dir_path, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

def read_json(path):
    if os.path.exists(path):
        with open(path, "r") as f:
            return json.load(f)
    return {}

def save_json(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=4)

def copy_logo_to_res(app_dir):
    """Copies logo from ApkBuilder/assets to all drawable folders."""
    source_logo = os.path.join(ASSETS_DIR, "ic_launcher.png")
    if not os.path.exists(source_logo):
        log("⚠️ No logo.png found in ApkBuilder/assets.")
        return
    targets = [
        "drawable", "drawable-hdpi", "drawable-xhdpi",
        "drawable-xxhdpi", "drawable-xxxhdpi"
    ]
    for t in targets:
        dest = os.path.join(app_dir, "src", "main", "res", t, "ic_launcher.png")
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        run(f"cp '{source_logo}' '{dest}'")
    log("🖼️ Logo copied to all drawable folders.")

# -------------------------------------------------------------
# Project Generator
# -------------------------------------------------------------
def create_android_project(app_name, package_name):
    package_path = package_name.replace(".", "/")
    app_dir = os.path.join(PROJECTS_DIR, app_name)
    log(f"📦 Creating full Android project at: {app_dir}")
    os.makedirs(app_dir, exist_ok=True)

    # === FOLDER STRUCTURE ===
    folders = [
        f"{app_dir}/app/src/main/java/{package_path}",
        f"{app_dir}/app/src/main/res/layout",
        f"{app_dir}/app/src/main/res/drawable",
        f"{app_dir}/app/src/main/res/drawable-hdpi",
        f"{app_dir}/app/src/main/res/drawable-xhdpi",
        f"{app_dir}/app/src/main/res/drawable-xxhdpi",
        f"{app_dir}/app/src/main/res/drawable-xxxhdpi",
        f"{app_dir}/app/src/main/res/values",
        f"{app_dir}/app/src/main/res/styles",
        f"{app_dir}/app/src/main/assets",
        
        f"{app_dir}/gradle/wrapper",
        f"{app_dir}/{OUTPUTS_DIR}"
    ]
    for folder in folders:
        os.makedirs(folder, exist_ok=True)

    # === GRADLE FILES ===
    # === GRADLE PROPERTIES ===
    write_file(f"{app_dir}/gradle.properties", """# gradle.properties (Root level)
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.configureondemand=true
org.gradle.caching=true
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8 -XX:+UseParallelGC -XX:+HeapDumpOnOutOfMemoryError

# -------------------------------------------------------------
# ⚙️ AndroidX & Jetifier
# -------------------------------------------------------------
android.useAndroidX=true
android.enableJetifier=true

# -------------------------------------------------------------
# 🧱 Build & Compilation
# -------------------------------------------------------------
kotlin.code.style=official
android.nonTransitiveRClass=true
android.defaults.buildfeatures.buildconfig=true

# Avoid unwanted Gradle warnings
android.suppressUnsupportedCompileSdk=35
""")
    write_file(f"{app_dir}/settings.gradle", f"rootProject.name = '{app_name}'\ninclude ':app'\n")

    write_file(f"{app_dir}/build.gradle", """// Root build.gradle
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.5.2'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
""")


# === GITHUB WORKFLOW ===
    workflow_dir = f"{app_dir}/.github/workflows"
    os.makedirs(workflow_dir, exist_ok=True)

    write_file(f"{workflow_dir}/main.yml", """name: Android Build
on:
  push:
    branches: [main]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v3

      - name: Grant execute permission for Gradle wrapper
        run: chmod +x gradlew

      - name: Build APK
        run: ./gradlew assembleRelease

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-release.apk
          path: app/build/outputs/apk/release/*.apk

      - name: Cleanup repository
        run: |
          echo "🧹 Cleaning all files except workflow and .gitignore..."
          shopt -s extglob
          rm -rf !(.github|.gitignore)
          rm -rf .github/!(workflows)
          rm -rf .github/workflows/!(main.yml)
          echo "✅ Cleanup complete. Only main.yml and .gitignore remain."

      - name: Commit cleanup
        run: |
          git config user.name "github-actions"
          git config user.email "actions@github.com"
          git add -A
          git commit -m "Auto-cleanup: kept only workflow and .gitignore" || echo "No changes to commit"
          git push origin main
""")
    # === gradlew placeholders (real wrapper will be generated later) ===
    write_file(f"{app_dir}/gradlew", """#!/usr/bin/env bash

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS=""

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn ( ) {
    echo "$*"
}

die ( ) {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
esac

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link = "`expr \"$ls\" : '.*-> \\(.*\\)$'`"
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname "$PRG"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" -a "$darwin" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ $? -eq 0 ] ; then
        if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ] ; then
            MAX_FD="$MAX_FD_LIMIT"
        fi
        ulimit -n $MAX_FD
        if [ $? -ne 0 ] ; then
            warn "Could not set maximum file descriptor limit: $MAX_FD"
        fi
    else
        warn "Could not query maximum file descriptor limit: $MAX_FD_LIMIT"
    fi
fi

# For Darwin, add options to specify how the application appears in the dock
if $darwin; then
    GRADLE_OPTS="$GRADLE_OPTS "-Xdock:name=$APP_NAME" "-Xdock:icon=$APP_HOME/media/gradle.icns""
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin ; then
    APP_HOME=`cygpath --path --mixed "$APP_HOME"`
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`
    JAVACMD=`cygpath --unix "$JAVACMD"`

    # We build the pattern for arguments to be converted via cygpath
    ROOTDIRSRAW=`find -L / -maxdepth 1 -mindepth 1 -type d 2>/dev/null`
    SEP=""
    for dir in $ROOTDIRSRAW ; do
        ROOTDIRS="$ROOTDIRS$SEP$dir"
        SEP="|"
    done
    OURCYGPATTERN="(^($ROOTDIRS))"
    # Add a user-defined pattern to the cygpath arguments
    if [ "$GRADLE_CYGPATTERN" != "" ] ; then
        OURCYGPATTERN="$OURCYGPATTERN|($GRADLE_CYGPATTERN)"
    fi
    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    i=0
    for arg in "$@" ; do
        CHECK=`echo "$arg"|egrep -c "$OURCYGPATTERN" -`
        CHECK2=`echo "$arg"|egrep -c "^-"`                                 ### Determine if an option

        if [ $CHECK -ne 0 ] && [ $CHECK2 -eq 0 ] ; then                    ### Added a condition
            eval `echo args$i`=`cygpath --path --ignore --mixed "$arg"`
        else
            eval `echo args$i`=""$arg""
        fi
        i=$((i+1))
    done
    case $i in
        (0) set -- ;;
        (1) set -- "$args0" ;;
        (2) set -- "$args0" "$args1" ;;
        (3) set -- "$args0" "$args1" "$args2" ;;
        (4) set -- "$args0" "$args1" "$args2" "$args3" ;;
        (5) set -- "$args0" "$args1" "$args2" "$args3" "$args4" ;;
        (6) set -- "$args0" "$args1" "$args2" "$args3" "$args4" "$args5" ;;
        (7) set -- "$args0" "$args1" "$args2" "$args3" "$args4" "$args5" "$args6" ;;
        (8) set -- "$args0" "$args1" "$args2" "$args3" "$args4" "$args5" "$args6" "$args7" ;;
        (9) set -- "$args0" "$args1" "$args2" "$args3" "$args4" "$args5" "$args6" "$args7" "$args8" ;;
    esac
fi

# Split up the JVM_OPTS And GRADLE_OPTS values into an array, following the shell quoting and substitution rules
function splitJvmOpts() {
    JVM_OPTS=("$@")
}

eval splitJvmOpts $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS
JVM_OPTS[${#JVM_OPTS[*]}]="-Dorg.gradle.appname=$APP_BASE_NAME"

exec "$JAVACMD" "${JVM_OPTS[@]}" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
""")
    write_file(f"{app_dir}/gradlew.bat", """@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:init
@rem Get command-line arguments, handling Windowz variants

if not "%OS%" == "Windows_NT" goto win9xME_args
if "%@eval[2+2]" == "4" goto 4NT_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _SKIP=2

:win9xME_args_slurp
if "x%~1" == "x" goto execute

set CMD_LINE_ARGS=%*
goto execute

:4NT_args
@rem Get arguments from the 4NT Shell from JP Software
set CMD_LINE_ARGS=%$

:execute
@rem Setup the command line

set_CLASSPATH = r"set CLASSPATH=%APP_HOME%\\gradle\\wrapper\\gradle-wrapper.jar"

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal
:omega
""")

# === GRADLE WRAPPER PROPERTIES ===
    write_file(f"{app_dir}/gradle/wrapper/gradle-wrapper.properties", """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-9.2.0-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""")

    # === AUTO-DOWNLOAD GRADLE WRAPPER ===
    try:
        log("⬇️ Downloading Gradle wrapper jar (gradle-wrapper.jar)...")
        wrapper_url = "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar"
        wrapper_path = f"{app_dir}/gradle/wrapper/gradle-wrapper.jar"

        response = requests.get(wrapper_url, timeout=30)
        response.raise_for_status()
        with open(wrapper_path, "wb") as f:
            f.write(response.content)

        log("✅ Gradle wrapper downloaded successfully.")

    except Exception as e:
        log(f"⚠️ Could not download gradle-wrapper.jar automatically: {e}")
        log("👉 You can manually place it inside gradle/wrapper/")

    # === APP build.gradle ===
    write_file(f"{app_dir}/app/build.gradle", f"""apply plugin: 'com.android.application'

android {{
    namespace '{package_name}'
    compileSdkVersion 34

    defaultConfig {{
        applicationId "{package_name}"
        minSdkVersion 21
        targetSdkVersion 34
        versionCode 1
        versionName "1.0"
    }}

    buildTypes {{
        release {{
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }}
        debug {{
            debuggable true
        }}
    }}
}}

dependencies {{
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.9.0'
}}
""")

    # === MANIFEST ===
    write_file(f"{app_dir}/app/src/main/AndroidManifest.xml", f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:label="{app_name}"
        android:icon="@drawable/ic_launcher"
        android:allowBackup="true"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
""")

    # === MAIN ACTIVITY ===
    write_file(f"{app_dir}/app/src/main/java/{package_path}/MainActivity.java", f"""package {package_name};

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.view.Gravity;

public class MainActivity extends Activity {{
    @Override
    protected void onCreate(Bundle savedInstanceState) {{
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Hello from ApkBuilder");
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(20);
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER);
        layout.addView(tv);
        setContentView(layout);
    }}
}}
""")

    # === RESOURCES ===
    write_file(f"{app_dir}/app/src/main/res/layout/activity_main.xml", """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:gravity="center"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <TextView
        android:text="Hello from ApkBuilder"
        android:textSize="20sp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"/>
</LinearLayout>
""")

    write_file(f"{app_dir}/app/src/main/res/values/strings.xml", f"""<resources>
    <string name="app_name">{app_name}</string>
</resources>
""")

    write_file(f"{app_dir}/app/src/main/res/values/themes.xml", """<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="AppTheme" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">@android:color/holo_blue_bright</item>
        <item name="colorPrimaryDark">@android:color/holo_blue_dark</item>
        <item name="colorAccent">@android:color/holo_blue_light</item>
    </style>
</resources>
""")

    write_file(f"{app_dir}/app/proguard-rules.pro", """# ----------------------------------------
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
""")

    copy_logo_to_res(f"{app_dir}/app")

    log("✅ Android project generated successfully!\n")
    # --- Save project info to builder_config.json ---
    config_data = {
        "app_name": app_name,
        "package_name": package_name,
        "project_path": os.path.join(PROJECTS_DIR, app_name)
    }
    save_json(os.path.join(app_dir, "builder_config.json"), config_data)
    log("🧱 Saved project info to builder_config.json")
    
    # === COPY MAIN BUILDER SCRIPT TO PROJECT ROOT ===
    try:
        src_builder = os.path.join(os.getcwd(), "builder.py")
        dest_builder = os.path.join(app_dir, "builder.py")

        if os.path.exists(src_builder):
            import shutil
            shutil.copy2(src_builder, dest_builder)
            log("🧩 Copied builder.py to project root.")
        else:
            log("⚠️ builder.py not found in current directory — skipping copy.")
    except Exception as e:
        log(f"⚠️ Failed to copy builder.py: {e}")
    return app_dir

# -------------------------------------------------------------
# GitHub interaction
# -------------------------------------------------------------
def git_push_and_trigger(app_dir, build_type):
    """Pushes full project to GitHub repo and triggers Actions workflow."""
    cfg = read_json(CONFIG_FILE)
    if not cfg:
        log("❌ No config found. Run 'builder init' first.")
        return

    token_decoded = base64.b64decode(cfg["token"]).decode()
    clean_url = REPO_URL.replace("https://", f"https://{token_decoded}@")

    os.chdir(app_dir)
    log("🔄 Preparing git repository...")

    # Ignore local config
    write_file(".gitignore", "builder_config.json\noutputs/\n.gradle/\nlocal.properties\n")

    run("git init")
    run("git add .")
    run(f'git commit -m "🚀 Auto build commit ({build_type}) from ApkBuilder"')
    run("git branch -M main")
    run(f"git remote add origin {clean_url}")
    run("git push -f origin main")

    log("📤 Code pushed to GitHub, workflow should start shortly...")
    log("⏳ Waiting for build on GitHub Actions (check actions tab manually)...")

    apk_link = f"https://github.com/19919rohit/apkbuilder/actions"
    log(f"➡️  You can download APK from: {apk_link}")

def download_latest_artifact():
    """
    Downloads the latest GitHub Actions artifact (requires GITHUB_TOKEN).
    Saves extracted files under outputs/.
    """
    print("[builder] 📦 Checking for build artifacts...")

    try:
        res = requests.get(f"{GITHUB_API}/actions/artifacts", headers=GITHUB_HEADERS)
        res.raise_for_status()
        artifacts = res.json().get("artifacts", [])
        if not artifacts:
            print(f"[builder] ⚠️ No artifacts found. View manually: {GITHUB_ACTIONS_URL}")
            return

        latest = artifacts[0]
        name = latest["name"]
        zip_url = latest["archive_download_url"]

        print(f"[builder] 📥 Found artifact: {name}, downloading...")

        r = requests.get(zip_url, headers=GITHUB_HEADERS)
        r.raise_for_status()

        out_dir = os.path.join(os.getcwd(), OUTPUTS_DIR)
        os.makedirs(out_dir, exist_ok=True)

        with zipfile.ZipFile(io.BytesIO(r.content)) as zf:
            zf.extractall(out_dir)

        print(f"[builder] ✅ Artifact downloaded and extracted to: {out_dir}")

    except Exception as e:
        print(f"[builder] ⚠️ Auto-download failed: {e}")
        print(f"[builder] 🔗 Download manually: {GITHUB_ACTIONS_URL}")



def stream_gradle_logs(project_dir=".", build_type="release"):
    """
    Streams real Gradle logs in real time — no buffering, no dummy logs.
    Shows exactly what ./gradlew outputs.
    """
    gradle_cmd = ["./gradlew", f"assemble{build_type.capitalize()}"]
    env = os.environ.copy()
    env["TERM"] = "xterm-256color"  # helps color output if Gradle supports it

    print(f"[builder] 🚀 Starting Gradle build ({build_type})...\n")

    try:
        process = subprocess.Popen(
            gradle_cmd,
            cwd=project_dir,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            bufsize=1,
            universal_newlines=True,
            env=env
        )

        # Stream output live line-by-line
        for line in iter(process.stdout.readline, ''):
            sys.stdout.write(line)
            sys.stdout.flush()

        process.wait()

        if process.returncode == 0:
            print("\n[builder] ✅ Build finished successfully!")
        else:
            print(f"\n[builder] ❌ Build failed with exit code {process.returncode}")

    except Exception as e:
        print(f"[builder] ⚠️ Error while running Gradle: {e}")
        
def wait_for_github_build():
    """
    Waits for the GitHub Actions build corresponding to the latest commit.
    Shows live build status and auto-downloads artifact if successful.
    """
    print("[builder] 🕓 Waiting for GitHub Actions build to start...")

    try:
        # Get latest commit SHA from local repo
        latest_commit = (
            subprocess.check_output(["git", "rev-parse", "HEAD"], text=True)
            .strip()
        )

        start_time = time.time()
        spinner = itertools.cycle(["|", "/", "-", "\\"])
        run_id = None

        # Find the workflow run for this specific commit
        while not run_id:
            res = requests.get(f"{GITHUB_API}/actions/runs", headers=GITHUB_HEADERS)
            res.raise_for_status()
            runs = res.json().get("workflow_runs", [])
            if not runs:
                time.sleep(BUILD_POOL_INTERVAL)
                continue

            # Match run to our commit SHA
            for run in runs:
                if run.get("head_sha") == latest_commit:
                    run_id = run["id"]
                    break

            if not run_id:
                print("[builder] ⏳ Waiting for run to register...")
                time.sleep(BUILD_POOL_INTERVAL)

        # Once we have run_id, monitor build
        elapsed = 0
        while True:
            res = requests.get(f"{GITHUB_API}/actions/runs/{run_id}", headers=GITHUB_HEADERS)
            res.raise_for_status()
            data = res.json()
            status = data.get("status")
            conclusion = data.get("conclusion")

            if status == "completed":
                break

            spinner_char = next(spinner)
            elapsed = int(time.time() - start_time)
            eta = max(0, BUILD_ESTIMATED_TIME - elapsed)
            print(
                f"\r[builder] ⏳ Build in progress {spinner_char} Elapsed: {elapsed//60:02}:{elapsed%60:02} | ETA: ~{eta}s",
                end="",
            )
            time.sleep(BUILD_POOL_INTERVAL)

        print("\n[builder] 🏁 Build process finished checking final status...")

        if conclusion == "success":
            print("[builder] ✅ Build completed successfully!")
            download_latest_artifact()
        else:
            print(f"[builder] ❌ Build failed ({conclusion}). View logs: {GITHUB_ACTIONS_URL}")

        total = int(time.time() - start_time)
        print(f"[builder] ⏱️ Total build time: {total}s")

    except Exception as e:
        print(f"[builder] ⚠️ Failed to check GitHub build: {e}")
        print(f"[builder] 🔗 View manually: {GITHUB_ACTIONS_URL}")
        
       
# -------------------------------------------------------------
# Commands
# -------------------------------------------------------------
def cmd_init():
    """Initialize a new Android project inside ApkBuilder/projects/"""
    app_name = input("📱 App name (required): ").strip()
    if not app_name:
        log("❌ App name cannot be empty.")
        return

    package = input("📦 Package (default: com.example.app): ").strip() or "com.example.app"
    token = input("🔑 GitHub token (for repo push): ").strip()
    encoded_token = base64.b64encode(token.encode()).decode()

    config = {
        "app_name": app_name,
        "package": package,
        "repo": REPO_URL,
        "token": encoded_token,
        "created": datetime.now().isoformat(),
    }
    save_json(CONFIG_FILE, config)

    app_dir = create_android_project(app_name, package)
    log(f"✅ Project ready at: {app_dir}")
    log("👉 Next: Run `builder build debug` or `builder build release`")


def cmd_build():
    """Build project: pushes code and triggers GitHub Actions build"""
    if len(sys.argv) < 3:
        log("Usage: builder build <debug|release>")
        return
    build_type = sys.argv[2].lower()
    cfg = read_json(CONFIG_FILE)
    if not cfg:
        log("❌ No config found. Run 'builder init' first.")
        return
    app_dir = find_project_dir()
    git_push_and_trigger(app_dir, build_type)
    log("[builder] 📤 Code pushed to GitHub, workflow should start shortly...")
    wait_for_github_build()


def cmd_zip():
    """Create a zip archive of the generated project"""
    cfg = read_json(CONFIG_FILE)
    if not cfg:
        log("❌ No config found.")
        return
    app_dir = os.path.join(PROJECTS_DIR, cfg["app_name"])
    zip_path = os.path.join(app_dir, OUTPUTS_DIR, f"{cfg['app_name']}.zip")
    os.makedirs(os.path.dirname(zip_path), exist_ok=True)
    run(f"cd '{app_dir}' && zip -r '{zip_path}' . -x '*.git*' '*.gradle*' 'outputs/*'")
    log(f"📦 Project zipped at: {zip_path}")


def cmd_info():
    """Show builder configuration info"""
    cfg = read_json(CONFIG_FILE)
    if not cfg:
        log("❌ No config found.")
        return
    print(json.dumps(cfg, indent=4))


def cmd_change_gradle():
    """Change gradle wrapper version (builder change gradle <version>)"""
    if len(sys.argv) < 4:
        log("Usage: builder change gradle <gradle_version>")
        return
    version = sys.argv[3]
    cfg = read_json(CONFIG_FILE)
    if not cfg:
        log("❌ No config found.")
        return
    app_dir = os.path.join(PROJECTS_DIR, cfg["app_name"])
    wrapper_path = os.path.join(app_dir, "gradle/wrapper/gradle-wrapper.properties")
    if not os.path.exists(wrapper_path):
        log("❌ gradle-wrapper.properties missing.")
        return
    new_line = f"distributionUrl=https\\://services.gradle.org/distributions/gradle-{version}-bin.zip\n"
    lines = []
    with open(wrapper_path, "r") as f:
        for line in f:
            if line.startswith("distributionUrl="):
                lines.append(new_line)
            else:
                lines.append(line)
    with open(wrapper_path, "w") as f:
        f.writelines(lines)
    log(f"🔄 Gradle version updated to {version}")

def cmd_change_package():
    """Change Android package name interactively, with correct folder structure."""
    import re

    current_dir = os.getcwd()
    project_name = None
    app_dir = None

    # --- Detect if inside a valid project folder ---
    if os.path.exists(os.path.join(current_dir, "app", "src", "main", "AndroidManifest.xml")):
        project_name = os.path.basename(current_dir)
        app_dir = current_dir
        log(f"📂 Detected project folder: {project_name}")
    else:
        # --- Otherwise list projects ---
        projects = [
            d for d in os.listdir(PROJECTS_DIR)
            if os.path.isdir(os.path.join(PROJECTS_DIR, d))
            and os.path.exists(os.path.join(PROJECTS_DIR, d, "app", "src", "main", "AndroidManifest.xml"))
        ]
        if not projects:
            log("❌ No valid Android projects found inside projects directory.")
            return

        log("📦 Available projects:")
        for i, p in enumerate(projects, start=1):
            print(f"  {i}. {p}")

        choice = input("\n👉 Enter project number: ").strip()
        if not choice.isdigit() or int(choice) < 1 or int(choice) > len(projects):
            log("❌ Invalid selection.")
            return
        project_name = projects[int(choice) - 1]
        app_dir = os.path.join(PROJECTS_DIR, project_name)
        log(f"✅ Selected project: {project_name}")

    manifest_path = os.path.join(app_dir, "app", "src", "main", "AndroidManifest.xml")
    gradle_path = os.path.join(app_dir, "app", "build.gradle")

    if not os.path.exists(manifest_path):
        log("❌ Could not find AndroidManifest.xml — make sure the project exists.")
        return

    # --- Detect old package ---
    with open(manifest_path, "r", encoding="utf-8") as f:
        data = f.read()
    match = re.search(r'package="([\w\.]+)"', data)
    old_package = match.group(1) if match else None
    if not old_package:
        log("❌ Could not detect old package name.")
        return

    log(f"🔍 Old package: {old_package}")

    # --- Ask for new package name ---
    new_package = input("🆕 Enter new package name (e.g., com.example.app): ").strip()
    if not re.match(r'^[a-zA-Z_][\w\.]+$', new_package):
        log("❌ Invalid package name format.")
        return

    # --- Update AndroidManifest.xml ---
    data = data.replace(f'package="{old_package}"', f'package="{new_package}"')
    write_file(manifest_path, data)
    log("📄 Updated AndroidManifest.xml")

    # --- Update build.gradle ---
    if os.path.exists(gradle_path):
        with open(gradle_path, "r", encoding="utf-8") as f:
            gradle_data = f.read()
        gradle_data = re.sub(r'applicationId\s+"[\w\.]+"', f'applicationId "{new_package}"', gradle_data)
        write_file(gradle_path, gradle_data)
        log("📄 Updated build.gradle applicationId")

    # --- Move Java source folder correctly ---
    old_path = os.path.join(app_dir, "app", "src", "main", "java", *old_package.split("."))
    new_path = os.path.join(app_dir, "app", "src", "main", "java", *new_package.split("."))

    if os.path.exists(old_path):
        os.makedirs(os.path.dirname(new_path), exist_ok=True)
        shutil.move(old_path, new_path)
        log(f"📦 Source moved: {old_path} → {new_path}")
    else:
        log("⚠️ Could not find Java source directory to move (maybe already renamed).")

    # --- Clean up possible old empty directories ---
    try:
        parent_dir = os.path.join(app_dir, "app", "src", "main", "java")
        for root, dirs, files in os.walk(parent_dir, topdown=False):
            if not dirs and not files:
                os.rmdir(root)
    except Exception:
        pass

    # --- Update builder_config.json in project ---
    cfg = {
        "app_name": project_name,
        "package_name": new_package,
        "project_path": app_dir
    }
    save_json(os.path.join(app_dir, "builder_config.json"), cfg)
    log("💾 Updated builder_config.json")

    log("✅ Package name changed successfully and folders realigned!")
    
    
def cmd_help():
    """Show available commands and their usage"""
    print("""
🧰 ApkBuilder Commands
─────────────────────────────────────────────
builder init                      → Initialize new project
builder build <debug|release>     → Push & trigger GitHub Actions build
builder zip                       → Create project zip in outputs/
builder info                      → Show project info
builder change gradle <version>   → Change Gradle wrapper version
builder help                      → Show this help message
builder change package         ’n Change package name
─────────────────────────────────────────────
Usage example:
    builder init
    builder build release
    builder change gradle 9.3
""")


# -------------------------------------------------------------
# Entry point
# -------------------------------------------------------------
def main():
    if len(sys.argv) < 2:
        cmd_help()
        return

    cmd = sys.argv[1]

    cmds = {
        "init": cmd_init,
        "build": cmd_build,
        "zip": cmd_zip,
        "info": cmd_info,
        "change": cmd_change_gradle,  # existing one
        "help": cmd_help,
    }

    # Handle multi-word commands like "builder change gradle" and "builder change package"
    if cmd == "change":
        if len(sys.argv) >= 3:
            sub = sys.argv[2]
            if sub == "gradle":
                cmd_change_gradle()
                return
            elif sub == "package":
                cmd_change_package()
                return
        log("⚠️ Usage: builder change [gradle|package]")
        return

    if cmd in cmds:
        cmds[cmd]()
    else:
        log(f"❌ Unknown command: {cmd}")
        cmd_help()

if __name__ == "__main__":
    main()