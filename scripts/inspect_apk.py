#!/usr/bin/env python3
"""Create a metadata-only compatibility report for an Apktool decoded APK."""
from __future__ import annotations
import argparse
import re
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
TARGET_ACTIVITY = "com.teamcuriosity.addons.ExtendedUnityActivity"
TARGET_DESCRIPTOR = "Lcom/teamcuriosity/addons/ExtendedUnityActivity;"

def read_manifest(path: Path) -> dict:
    root = ET.parse(path).getroot()
    app = root.find("application")
    components, launchers = [], []
    if app is not None:
        for node in list(app):
            if node.tag not in ("activity", "activity-alias"):
                continue
            name = node.get(ANDROID_NS + "name", "")
            components.append(name)
            for intent in node.findall("intent-filter"):
                actions = {a.get(ANDROID_NS + "name") for a in intent.findall("action")}
                categories = {c.get(ANDROID_NS + "name") for c in intent.findall("category")}
                if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
                    launchers.append(name)
    return {
        "package": root.get("package", ""),
        "version_name": root.get(ANDROID_NS + "versionName", ""),
        "version_code": root.get(ANDROID_NS + "versionCode", ""),
        "components": components,
        "launchers": launchers,
    }

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--decoded", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    manifest_path = args.decoded / "AndroidManifest.xml"
    if not manifest_path.is_file():
        parser.error(f"decoded manifest not found: {manifest_path}")
    meta = read_manifest(manifest_path)

    with zipfile.ZipFile(args.apk, "r") as apk:
        dex_entries = sorted(
            n for n in apk.namelist()
            if re.fullmatch(r"classes(\d*)\.dex", Path(n).name)
        )
        native_libs = [
            n for n in apk.namelist() if n.startswith("lib/") and n.endswith(".so")
        ]

    smali_dirs = sorted(
        p for p in args.decoded.iterdir()
        if p.is_dir() and re.fullmatch(r"smali(?:_classes\d+)?", p.name)
    )
    smali_count = sum(1 for root in smali_dirs for _ in root.rglob("*.smali"))
    target_files, target_on_create, target_super_call = [], False, False

    for path in args.decoded.rglob("ExtendedUnityActivity.smali"):
        source = path.read_text(encoding="utf-8", errors="replace")
        if TARGET_DESCRIPTOR not in source:
            continue
        target_files.append(str(path.relative_to(args.decoded)))
        method = re.search(
            r"^\.method\s+[^\n]*onCreate\(Landroid/os/Bundle;\)V\s*$"
            r"(.*?)^\.end method", source, re.M | re.S
        )
        if method:
            target_on_create = True
            target_super_call = bool(re.search(
                r"invoke-super(?:/range)?\s+\{[^}]+\},\s*"
                r"L[^;]+;->onCreate\(Landroid/os/Bundle;\)V", method.group(1)
            ))

    lines = [
        "# Space L APK compatibility report",
        "",
        "- Package: " + (meta["package"] or "not found"),
        "- Version name: " + (meta["version_name"] or "not declared"),
        "- Version code: " + (meta["version_code"] or "not declared"),
        "- DEX files: " + str(len(dex_entries)) + " (" + (", ".join(dex_entries) or "none") + ")",
        "- Native libraries: " + str(len(native_libs)),
        "- Apktool Smali directories: " + str(len(smali_dirs)),
        "- Total Smali classes: " + str(smali_count),
        "- Target Activity declared in manifest: " + ("yes" if TARGET_ACTIVITY in meta["components"] else "no"),
        "- Target Activity Smali matches: " + str(len(target_files)),
        "- Target onCreate(Bundle): " + ("yes" if target_on_create else "no"),
        "- Superclass onCreate call: " + ("yes" if target_super_call else "no"),
        "",
        "## Launcher activities",
        "",
    ]
    lines.extend(["- " + name for name in meta["launchers"]] or ["- none found"])
    if target_files:
        lines.extend(["", "## Matching Activity paths", ""])
        lines.extend(["- " + name for name in target_files])

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    if len(target_files) != 1 or not target_on_create or not target_super_call:
        raise SystemExit("target Activity is not compatible with the expected hook layout")
    return 0

if __name__ == "__main__":
    main()
