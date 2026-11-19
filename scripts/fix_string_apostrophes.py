#!/usr/bin/env python3
"""Escape apostrophes in Android string resources."""

from __future__ import annotations

import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"


def fix_folder(folder: Path) -> None:
    for path in sorted(folder.glob("strings*.xml")):
        tree = ET.parse(path)
        root = tree.getroot()
        changed = 0
        for element in root.findall("string"):
            text = element.text
            if not text or "'" not in text:
                continue
            normalized = text.replace("\\'", "'")
            fixed = normalized.replace("'", "\\'")
            if fixed != text:
                element.text = fixed
                changed += 1
        if not changed:
            continue
        ET.indent(root, space="    ")
        tree.write(path, encoding="utf-8", xml_declaration=True)
        content = path.read_text(encoding="utf-8")
        if content.startswith("<?xml version='1.0'"):
            content = content.replace(
                "<?xml version='1.0' encoding='utf-8'?>",
                '<?xml version="1.0" encoding="utf-8"?>',
                1,
            )
            path.write_text(content, encoding="utf-8", newline="\n")
        print(f"{path.name}: {changed}")


def main() -> None:
    fix_folder(RES / "values")
    fix_folder(RES / "values-ru")


if __name__ == "__main__":
    main()
