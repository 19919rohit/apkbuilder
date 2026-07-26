#!/usr/bin/env python3

import os

OLD = "neunix.pageflow"
NEW = "neunix.pagevibe"

ROOT = "app/src"

extensions = {
    ".java",
    ".xml",
    ".gradle",
    ".kts"
}

changed = 0

print(f"🔧 Replacing {OLD} -> {NEW}")

for root, dirs, files in os.walk(ROOT):
    for file in files:
        if not any(file.endswith(ext) for ext in extensions):
            continue

        path = os.path.join(root, file)

        try:
            with open(path, "r", encoding="utf-8") as f:
                content = f.read()

            if OLD in content:
                new_content = content.replace(OLD, NEW)

                with open(path, "w", encoding="utf-8") as f:
                    f.write(new_content)

                changed += 1
                print("Updated:", path)

        except Exception as e:
            print("Skipped:", path, e)

print()
print(f"✅ Updated {changed} files")

print("\n🔍 Remaining pageflow references:")

found = False

for root, dirs, files in os.walk(ROOT):
    for file in files:
        if file.endswith((".java", ".xml", ".gradle", ".kts")):
            path = os.path.join(root, file)

            try:
                with open(path, "r", encoding="utf-8") as f:
                    if OLD in f.read():
                        print(path)
                        found = True
            except:
                pass

if not found:
    print("✅ No neunix.pageflow references found")

print("\nNow run:")
print("./gradlew clean assembleDebug")