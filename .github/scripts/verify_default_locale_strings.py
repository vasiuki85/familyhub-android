#!/usr/bin/env python3
"""Fail CI when default locale strings drift from EN+RU policy."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VALUES = ROOT / "app" / "src" / "main" / "res" / "values"
VALUES_RU = ROOT / "app" / "src" / "main" / "res" / "values-ru"
CYRILLIC = re.compile(r"[А-Яа-яЁё]")
UNESCAPED_APOSTROPHE = re.compile(r"(?<!\\)'")


def load_keys(folder: Path) -> dict[str, str]:
    keys: dict[str, str] = {}
    for path in sorted(folder.glob("strings*.xml")):
        root = ET.parse(path).getroot()
        for element in root.findall("string"):
            name = element.attrib["name"]
            if name in keys:
                raise SystemExit(f"Duplicate key '{name}' in {folder} ({keys[name]} and {path.name})")
            keys[name] = path.name
    return keys


def main() -> int:
    errors: list[str] = []

    for path in sorted(VALUES.glob("strings*.xml")):
        text = path.read_text(encoding="utf-8")
        if CYRILLIC.search(text):
            errors.append(f"{path.relative_to(ROOT)} contains Cyrillic (default locale must be English)")

    for folder in (VALUES, VALUES_RU):
        for path in sorted(folder.glob("strings*.xml")):
            root = ET.parse(path).getroot()
            for element in root.findall("string"):
                value = element.text or ""
                if UNESCAPED_APOSTROPHE.search(value):
                    errors.append(
                        f"{path.relative_to(ROOT)}:{element.attrib['name']} has unescaped apostrophe"
                    )

    en_keys = load_keys(VALUES)
    ru_keys = load_keys(VALUES_RU)

    missing_ru = sorted(set(en_keys) - set(ru_keys))
    missing_en = sorted(set(ru_keys) - set(en_keys))
    if missing_ru:
        errors.append(f"Missing {len(missing_ru)} keys in values-ru (e.g. {missing_ru[:5]})")
    if missing_en:
        errors.append(f"Missing {len(missing_en)} keys in values (e.g. {missing_en[:5]})")

    if errors:
        print("Default locale guard failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    print(
        f"Default locale guard passed ({len(en_keys)} EN keys, {len(ru_keys)} RU keys, parity OK)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
