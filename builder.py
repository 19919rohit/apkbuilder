#!/usr/bin/env python3
"""
ApkBuilder v5 — Full-featured Android project automation tool
─────────────────────────────────────────────────────────────
Author: Rohit 
"""

import os
import sys
import json
import base64
import subprocess
from datetime import datetime


CONFIG_FILE = "builder_config.json"
OUTPUTS_DIR = "outputs"

# -------------------------------------------------------------
# Utilities
# -------------------------------------------------------------
def log(msg):
    print(f"[builder] {msg}")

def run(cmd, cwd=None, check=False):
    subprocess.run(cmd, shell=True, cwd=cwd, check=check)

def write_file(path, content):
    dirn = os.path.dirname(path)
    if dirn and not os.path.exists(dirn):
        os.makedirs(dirn, exist_ok=True)
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

# -------------------------------------------------------------
# Project generator
# -------------------------------------------------------------
def create_project_structure(app_name, package_name):
    log("Creating Android project structure...")

    package_path = package_name.replace(".", "/")
    folders = [
        f"app/src/main/java/{package_path}",
        "app/src/main/res/drawable",
        "app/src/main/res/layout",
        "app/src/main/res/values",
        "app/src/main/assets",
        "app/src/main/manifest",
        "gradle/wrapper",
        OUTPUTS_DIR
    ]
    for folder in folders:
        os.makedirs(folder, exist_ok=True)

    # Empty Gradle wrapper files
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
cd "`dirname \"$PRG\"`/" >/dev/null
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
    GRADLE_OPTS="$GRADLE_OPTS \"-Xdock:name=$APP_NAME\" \"-Xdock:icon=$APP_HOME/media/gradle.icns\""
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
            eval `echo args$i`="\"$arg\""
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
    write_file("gradle/wrapper/gradle-wrapper.properties", """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""")
    

    # settings.gradle
    write_file("settings.gradle", f"rootProject.name = '{app_name}'\ninclude ':app'")

    # Root build.gradle
    write_file("build.gradle", """// Top-level Gradle build file
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

    # app/build.gradle
    write_file("app/build.gradle", f"""apply plugin: 'com.android.application'

android {{
    compileSdkVersion 34
    namespace '{package_name}'

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
    }}
}}

dependencies {{
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.9.0'
}}
""")

    # manifest
    write_file("app/src/main/AndroidManifest.xml", f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{package_name}">
    <application
        android:label="{app_name}"
        android:icon="@drawable/ic_launcher">
        <activity android:name=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
""")

    # MainActivity
    write_file(f"app/src/main/java/{package_path}/MainActivity.java", f"""package {package_name};

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

    # layout
    write_file("app/src/main/res/layout/activity_main.xml", """<?xml version="1.0" encoding="utf-8"?>
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

    # strings.xml
    write_file("app/src/main/res/values/strings.xml", f"""<resources>
    <string name="app_name">{app_name}</string>
</resources>
""")

    # proguard
    write_file("app/proguard-rules.pro", "# ProGuard rules placeholder")

    log("✅ Android project created successfully!")

# -------------------------------------------------------------
# GitHub interaction
# -------------------------------------------------------------
def git_push(repo_url, token):
    token_decoded = base64.b64decode(token).decode()
    clean_url = repo_url.replace("https://", f"https://{token_decoded}@")

    run("git init")
    run("git add .")
    run('git commit -m "🚀 Auto build commit from ApkBuilder"')
    run("git branch -M main")
    run(f"git remote add origin {clean_url}")
    run("git push -f origin main")

def git_cleanup(repo_url, token):
    """Delete everything except workflow files from GitHub."""
    token_decoded = base64.b64decode(token).decode()
    clean_url = repo_url.replace("https://", f"https://{token_decoded}@")
    os.system('git rm -r * --cached')
    os.system('git add .github/workflows')
    os.system('git commit -m "🧹 Cleaned repository, kept workflows only" || true')
    os.system(f'git push -f {clean_url} main')

# -------------------------------------------------------------
# Commands
# -------------------------------------------------------------
def cmd_init():
    app_name = input("📱 App name: ").strip()
    package = input("📦 Package (e.g. com.example.app): ").strip()
    repo = input("🌐 GitHub repo URL: ").strip()
    token = input("🔑 GitHub token: ").strip()

    config = {
        "app_name": app_name,
        "package": package,
        "repo": repo,
        "token": base64.b64encode(token.encode()).decode(),
        "created": datetime.now().isoformat()
    }
    save_json(CONFIG_FILE, config)

    create_project_structure(app_name, package)
    log("✅ Project initialized! Use 'builder build' to start building.")

def cmd_build():
    cfg = read_json(CONFIG_FILE)
    if not cfg:
        log("❌ Run 'builder init' first.")
        return
    git_push(cfg["repo"], cfg["token"])
    log("🚀 Build triggered! Check GitHub Actions.")

def cmd_add_dep():
    dep = input("Enter Gradle dependency: ").strip()
    with open("app/build.gradle", "a") as f:
        f.write(f"\n    {dep}")
    log("✅ Dependency added.")

def cmd_info():
    cfg = read_json(CONFIG_FILE)
    if not cfg:
        log("❌ No project found.")
        return
    print(json.dumps(cfg, indent=4))

def cmd_clean_repo():
    cfg = read_json(CONFIG_FILE)
    if not cfg:
        log("❌ Not initialized.")
        return
    git_cleanup(cfg["repo"], cfg["token"])
    log("🧹 Repo cleaned (only workflows kept).")

def cmd_zip():
    run(f"zip -r {OUTPUTS_DIR}/project.zip . -x '*.git*'")
    log("📦 Project zipped in outputs/")

def cmd_open():
    run("termux-open .")
    log("📂 Opened project folder.")

def cmd_ls():
    os.system("ls -R app | head -50")

def cmd_remove_build():
    run("rm -rf app/build")
    log("🧽 Cleaned build folder.")

def cmd_refresh():
    cfg = read_json(CONFIG_FILE)
    if cfg:
        run("git pull origin main")
        log("🔄 Synced with remote.")
    else:
        log("❌ Not initialized.")

def cmd_setname():
    newname = input("Enter new app name: ").strip()
    cfg = read_json(CONFIG_FILE)
    cfg["app_name"] = newname
    save_json(CONFIG_FILE, cfg)
    log(f"🆕 App name updated to {newname}")

def cmd_export_token():
    cfg = read_json(CONFIG_FILE)
    if cfg:
        os.environ["APKBUILDER_TOKEN"] = base64.b64decode(cfg["token"]).decode()
        log("🔐 Token exported to environment variable.")

def cmd_upgrade_gradle():
    run("sed -i 's/gradle-8.5/gradle-8.7/g' gradle/wrapper/gradle-wrapper.properties")
    log("⬆️ Gradle upgraded.")

def cmd_help():
    print("""
🧰 ApkBuilder Commands
────────────────────────────
builder init             → Initialize project
builder build            → Push & trigger build
builder add dep          → Add dependency
builder info             → Show project info
builder clean repo       → Clean repo, keep workflows only
builder zip              → Zip project to outputs/
builder open             → Open folder
builder ls               → List project structure
builder remove build     → Remove app/build
builder refresh          → Pull latest
builder setname          → Rename app
builder export token     → Export token env
builder upgrade gradle   → Upgrade Gradle version
builder help             → Show commands
────────────────────────────
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
        "add": cmd_add_dep,
        "info": cmd_info,
        "clean": cmd_clean_repo,
        "zip": cmd_zip,
        "open": cmd_open,
        "ls": cmd_ls,
        "remove": cmd_remove_build,
        "refresh": cmd_refresh,
        "setname": cmd_setname,
        "export": cmd_export_token,
        "upgrade": cmd_upgrade_gradle,
        "help": cmd_help,
    }
    if cmd in cmds:
        cmds[cmd]()
    else:
        log(f"❌ Unknown command '{cmd}'")
        cmd_help()

if __name__ == "__main__":
    main()