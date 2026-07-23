#!/usr/bin/env python3
"""
Standalone String Rebrander: PageFlow -> PageVibe
───────────────────────────────────────────────────────────────────
Scans the current directory (or app folder) and replaces remaining
internal strings, layout tags, preferences, and custom views.
"""

import os
import re

OLD_SLUG = "pageflow"
NEW_SLUG = "pagevibe"

OLD_CAP = "PageFlow"
NEW_CAP = "PageVibe"

def main():
    target_dir = os.getcwd()
    print(f"🔍 Scanning directory: {target_dir}")
    print(f"🔄 Replacing '{OLD_SLUG}'/'{OLD_CAP}' with '{NEW_SLUG}'/'{NEW_CAP}'...\n")

    extensions = (".java", ".kt", ".xml", ".pro", ".bak", ".gradle", ".properties")
    modified_files_count = 0

    for root, dirs, files in os.walk(target_dir):
        # Skip git directories or build cache folders to avoid altering binaries/git index
        if ".git" in root or ".gradle" in root or "build" in root:
            continue

        for f_name in files:
            if f_name.endswith(extensions):
                file_path = os.path.join(root, f_name)
                try:
                    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                        content = f.read()

                    original_content = content

                    # Replace variations carefully
                    content = content.replace(OLD_CAP, NEW_CAP)
                    content = content.replace(OLD_SLUG, NEW_SLUG)
                    content = content.replace(OLD_SLUG.upper(), NEW_SLUG.upper())

                    if content != original_content:
                        with open(file_path, "w", encoding="utf-8") as f:
                            f.write(content)
                        print(f"✅ Updated: {os.path.relpath(file_path, target_dir)}")
                        modified_files_count += 1

                except Exception as e:
                    print(f"⚠️ Error processing {file_path}: {e}")

    print(f"\n🎉 Rebrand complete! Modified {modified_files_count} file(s).")

if __name__ == "__main__":
    main()
