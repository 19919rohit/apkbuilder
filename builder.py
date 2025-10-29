#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
builder.py — Universal Android Project Builder & GitHub Deployer
Author: Rohit 
"""

import os, subprocess, sys, json, urllib.request, shutil, zipfile, time

# ------------------------- GLOBALS -------------------------
ROOT = os.getcwd()
GRADLE_VERSION = "8.14"
JAR_URL = f"https://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip"
WRAPPER_JAR_URL = "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar"
GITHUB_REPO = "https://github.com/19919rohit/apkbuilder.git"
GITHUB_USER = "19919rohit"
CONFIG_FILE = "builder_config.json"

# ------------------------- UTILITIES -------------------------
def log(msg): print(f"[builder] {msg}")

def run(cmd):
    log(f"$ {cmd}")
    os.system(cmd)

def write_file(path, content):
    directory = os.path.dirname(path)
    if directory:  # only make dirs if not empty
        os.makedirs(directory, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

def download(url, dest):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    log(f"Downloading: {url}")
    urllib.request.urlretrieve(url, dest)
    log(f"Saved to {dest}")

def load_token():
    return os.getenv("GITHUB_TOKEN") or os.getenv("GITHUB_TOKEN")

# ------------------------- INIT -------------------------
def init_project():
    log("🧱 Creating Android project structure...")
    folders = [
        "app/src/main/java/com/example/app",
        "app/src/main/res/layout",
        "app/src/main/res/values",
        "gradle/wrapper"
    ]
    for d in folders: os.makedirs(d, exist_ok=True)

    write_file("settings.gradle", "rootProject.name = 'PushNotifications'\ninclude ':app'\n")
    write_file("gradle.properties", "org.gradle.jvmargs=-Xmx2048m\nandroid.useAndroidX=true\n")
    write_file("build.gradle", """
// Top-level Gradle build file
buildscript {
    repositories { google(); mavenCentral() }
    dependencies { classpath 'com.android.tools.build:gradle:8.4.0' }
}
allprojects {
    repositories { google(); mavenCentral() }
}
task clean(type: Delete) {
    delete rootProject.buildDir
}
""")
    write_file("app/build.gradle", """
plugins {
    id 'com.android.application'
}
android {
    namespace 'com.example.app'
    compileSdk 34
    defaultConfig {
        applicationId "com.example.app"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
dependencies {
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
}
""")
    write_file("app/src/main/AndroidManifest.xml", """
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.app">
    <application android:label="PushNotifications" android:icon="@mipmap/ic_launcher">
        <activity android:name=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""")
    write_file("app/src/main/java/com/example/app/MainActivity.java", """
package com.example.app;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Push Notifications Demo");
        setContentView(tv);
    }
}
""")

    # Placeholder scripts
    write_file("gradlew", """#!/usr/bin/env bash

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
    link=`expr "$ls" : '.*-> \(.*\)$'`
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
    write_file("gradlew.bat", """@if "%DEBUG%" == "" @echo off
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

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

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

    # Gradle wrapper properties
    write_file("gradle/wrapper/gradle-wrapper.properties",
               f"distributionUrl=https\\://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip\n")

    # Download wrapper jar
    jar_path = "gradle/wrapper/gradle-wrapper.jar"
    download(WRAPPER_JAR_URL, jar_path)

    log("✅ Project structure ready.")

# ------------------------- GIT & BUILD -------------------------
def git_setup():
    token = load_token()
    if not token:
        log("❌ Missing GitHub token. Please export it first:\nexport github_token=YOUR_TOKEN")
        return False

    run("git init")
    run("git add .")
    run('git config user.name "builder"')
    run('git config user.email "builder@localhost"')
    run('git commit -m "Initial commit"')

    # Reset remote if exists
    run("git remote remove origin || true")
    remote = f"https://{token}@github.com/{GITHUB_USER}/apkbuilder.git"
    run(f"git remote add origin {remote}")

    run("git push -u origin main || git push -u origin master")
    log("✅ Code pushed to GitHub.")
    return True

def trigger_build(build_type="release"):
    if git_setup():
        log(f"🚀 Build triggered for {build_type}! Check GitHub Actions...")

# ------------------------- COMMANDS -------------------------
def clean_repo():
    for root, dirs, files in os.walk(ROOT, topdown=False):
        for name in files:
            if not name.endswith(".py"):
                try: os.remove(os.path.join(root, name))
                except: pass
        for name in dirs:
            if name not in (".git",):
                try: shutil.rmtree(os.path.join(root, name))
                except: pass
    log("🧹 Cleaned project except builder.py")

def show_help():
    print("""
Available commands:
  builder init                → create new Android project structure
  builder jar                 → redownload gradle-wrapper.jar
  builder clean               → clean repo except builder.py
  builder build [debug|release] → push project & trigger build
  builder reset               → remove .git & reinit
  builder list                → list all project files
  builder zip                 → zip project folder
  builder token               → print detected GitHub token
  builder info                → show project info
  builder version             → show builder version
  builder push                → commit & push changes manually
  builder jarurl              → print gradle-wrapper jar url
  builder props               → rewrite gradle properties
  builder fullclean           → delete everything except builder.py
  builder help                → show this help
""")

# ------------------------- COMMAND EXEC -------------------------
def main():
    if len(sys.argv) < 2:
        show_help()
        return

    cmd = sys.argv[1]
    if cmd == "init": init_project()
    elif cmd == "jar": download(WRAPPER_JAR_URL, "gradle/wrapper/gradle-wrapper.jar")
    elif cmd == "clean": clean_repo()
    elif cmd == "build":
        typ = sys.argv[2] if len(sys.argv) > 2 else "release"
        trigger_build(typ)
    elif cmd == "reset":
        run("rm -rf .git && git init && git add . && git commit -m 'reset'")
    elif cmd == "list":
        for r, d, f in os.walk(ROOT):
            for file in f:
                print(os.path.join(r, file))
    elif cmd == "zip":
        zipname = f"project_{int(time.time())}.zip"
        shutil.make_archive(zipname.replace(".zip",""), 'zip', ROOT)
        log(f"📦 Project zipped -> {zipname}")
    elif cmd == "token":
        print(load_token() or "❌ No token found in env.")
    elif cmd == "info":
        log(f"Repo: {GITHUB_REPO}\nGradle: {GRADLE_VERSION}\nRoot: {ROOT}")
    elif cmd == "version":
        print("Builder.py v3.0 - Fully automated build system")
    elif cmd == "push":
        git_setup()
    elif cmd == "jarurl":
        print(WRAPPER_JAR_URL)
    elif cmd == "props":
        write_file("gradle/wrapper/gradle-wrapper.properties",
                   f"distributionUrl=https\\://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip\n")
    elif cmd == "fullclean":
        for item in os.listdir(ROOT):
            if item != "builder.py":
                if os.path.isdir(item): shutil.rmtree(item)
                else: os.remove(item)
        log("🧼 Everything deleted except builder.py")
    elif cmd == "help":
        show_help()
    else:
        print("❌ Unknown command. Use: builder help")

# -------------------------
if __name__ == "__main__":
    main()