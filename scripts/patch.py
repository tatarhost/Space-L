#!/usr/bin/env python3
"""Inject the UI-only Space L Activity hook into an Apktool decoded directory."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ACTIVITY_DESCRIPTOR = "Lcom/teamcuriosity/addons/ExtendedUnityActivity;"
HOOK = "invoke-static {p0}, Lcom/spaceL/menu/SpaceLMenu;->init(Landroid/app/Activity;)V"
ON_CREATE = re.compile(r"^\.method\s+[^\n]*onCreate\(Landroid/os/Bundle;\)V\s*$", re.M)
SUPER_ON_CREATE = re.compile(
    r"^\s*invoke-super(?:/range)?\s+\{[^}]+\},\s*L[^;]+;->onCreate\(Landroid/os/Bundle;\)V\s*$",
    re.M,
)


def find_activity(root: Path) -> Path:
    candidates = []
    for path in root.rglob("ExtendedUnityActivity.smali"):
        try:
            source = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if ACTIVITY_DESCRIPTOR in source:
            candidates.append(path)
    if len(candidates) != 1:
        raise RuntimeError(
            f"expected exactly one {ACTIVITY_DESCRIPTOR}, found {len(candidates)}"
        )
    return candidates[0]


def inject(path: Path) -> bool:
    source = path.read_text(encoding="utf-8")
    if HOOK in source:
        print(f"hook already present: {path}")
        return False

    method_match = ON_CREATE.search(source)
    if not method_match:
        raise RuntimeError("onCreate(Bundle) was not found in ExtendedUnityActivity")

    method_end = source.find(".end method", method_match.end())
    if method_end < 0:
        raise RuntimeError("onCreate(Bundle) has no .end method")

    method = source[method_match.start():method_end]
    super_match = SUPER_ON_CREATE.search(method)
    if not super_match:
        raise RuntimeError(
            "could not locate the superclass onCreate(Bundle) call; "
            "refusing to guess an insertion point"
        )

    insert_at = method_match.start() + super_match.end()
    source = source[:insert_at] + "\n    " + HOOK + source[insert_at:]
    path.write_text(source, encoding="utf-8")
    print(f"inserted UI hook: {path}")
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--decoded", required=True, type=Path,
                        help="Apktool decoded APK directory")
    args = parser.parse_args()

    root = args.decoded.resolve()
    if not root.is_dir():
        print(f"decoded directory does not exist: {root}", file=sys.stderr)
        return 2

    try:
        activity = find_activity(root)
        changed = inject(activity)
        print(f"Space L UI hook ready (changed={changed})")
        return 0
    except Exception as error:
        print(f"patch failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
