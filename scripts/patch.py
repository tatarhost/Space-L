#!/usr/bin/env python3
"""Patch the UI-only Space L hook or append its compiled DEX to an unsigned APK."""
from __future__ import annotations
import argparse
import re
import sys
import zipfile
from pathlib import Path

ACTIVITY_DESCRIPTOR = "Lcom/teamcuriosity/addons/ExtendedUnityActivity;"
HOOK = "invoke-static {p0}, Lcom/spaceL/menu/SpaceLMenu;->init(Landroid/app/Activity;)V"
ON_CREATE = re.compile(r"^\.method\s+[^\n]*onCreate\(Landroid/os/Bundle;\)V\s*$", re.M)
SUPER_ON_CREATE = re.compile(
    r"^\s*invoke-super(?:/range)?\s+\{[^}]+\},\s*L[^;]+;->onCreate\(Landroid/os/Bundle;\)V\s*$",
    re.M,
)
CLASS_SUPER = re.compile(r"^\.super\s+(L[^;\s]+;)\s*$", re.M)
DEX_NAME = re.compile(r"classes(\d*)\.dex$")

def find_activity(root: Path) -> Path:
    candidates = []
    for path in root.rglob("ExtendedUnityActivity.smali"):
        source = path.read_text(encoding="utf-8", errors="replace")
        if ACTIVITY_DESCRIPTOR in source:
            candidates.append(path)
    if len(candidates) != 1:
        raise RuntimeError(f"expected exactly one {ACTIVITY_DESCRIPTOR}, found {len(candidates)}")
    return candidates[0]

def inject_activity(decoded: Path) -> bool:
    path = find_activity(decoded)
    source = path.read_text(encoding="utf-8")
    if HOOK in source:
        print(f"hook already present: {path}")
        return False

    method_match = ON_CREATE.search(source)
    if method_match:
        method_end = source.find(".end method", method_match.end())
        if method_end < 0:
            raise RuntimeError("onCreate(Bundle) has no .end method")
        method = source[method_match.start():method_end]
        super_match = SUPER_ON_CREATE.search(method)
        if not super_match:
            raise RuntimeError(
                "existing onCreate(Bundle) has no superclass call; refusing to guess"
            )
        insert_at = method_match.start() + super_match.end()
        source = source[:insert_at] + "\n    " + HOOK + source[insert_at:]
        print(f"inserted UI hook into existing onCreate: {path}")
    else:
        parent_match = CLASS_SUPER.search(source)
        if not parent_match:
            raise RuntimeError("Activity direct superclass descriptor was not found")
        parent = parent_match.group(1)
        method = (
            "\n\n.method public onCreate(Landroid/os/Bundle;)V\n"
            "    .locals 0\n"
            f"    invoke-super {{p0, p1}}, {parent}->onCreate(Landroid/os/Bundle;)V\n"
            f"    {HOOK}\n"
            "    return-void\n"
            ".end method\n"
        )
        source = source.rstrip() + method
        print(f"added onCreate override calling {parent}: {path}")

    path.write_text(source, encoding="utf-8")
    return True

def append_dex(unsigned_apk: Path, dex_file: Path, output_apk: Path) -> str:
    if not unsigned_apk.is_file() or not dex_file.is_file():
        raise RuntimeError("unsigned APK or DEX input not found")
    if unsigned_apk.resolve() == output_apk.resolve():
        raise RuntimeError("output APK must differ from input APK")
    dex_bytes = dex_file.read_bytes()
    if not dex_bytes.startswith(b"dex\n"):
        raise RuntimeError("invalid DEX magic header")
    output_apk.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(unsigned_apk, "r") as source:
        names = source.namelist()
        indexes = []
        for name in names:
            match = DEX_NAME.fullmatch(Path(name).name)
            if match:
                indexes.append(int(match.group(1) or "1"))
        next_index = max(indexes, default=0) + 1
        dex_name = "classes.dex" if next_index == 1 else f"classes{next_index}.dex"
        if dex_name in names:
            raise RuntimeError(f"refusing to overwrite {dex_name}")
        with zipfile.ZipFile(output_apk, "w", allowZip64=True) as target:
            for info in source.infolist():
                target.writestr(info, source.read(info.filename))
            target.writestr(dex_name, dex_bytes, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(output_apk, "r") as check:
        if dex_name not in check.namelist():
            raise RuntimeError(f"DEX insertion verification failed: {dex_name}")
    print(f"added {dex_name} to {output_apk}")
    return dex_name

def main() -> int:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--decoded", type=Path)
    mode.add_argument("--apk", type=Path)
    parser.add_argument("--dex", type=Path)
    parser.add_argument("--output-apk", type=Path)
    args = parser.parse_args()
    try:
        if args.decoded is not None:
            root = args.decoded.resolve()
            if not root.is_dir():
                raise RuntimeError(f"decoded directory does not exist: {root}")
            inject_activity(root)
        else:
            if args.dex is None or args.output_apk is None:
                parser.error("--apk requires --dex and --output-apk")
            append_dex(args.apk.resolve(), args.dex.resolve(), args.output_apk.resolve())
        return 0
    except Exception as error:
        print(f"patch failed: {error}", file=sys.stderr)
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
