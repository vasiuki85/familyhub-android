#!/usr/bin/env python3
"""Verify debug APK ZIP 16 KiB alignment and ELF PT_LOAD p_align >= 0x4000."""

from __future__ import annotations

import argparse
import os
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

PT_LOAD = 1
MIN_P_ALIGN = 0x4000
DEFAULT_APK = Path("app/build/outputs/apk/debug/app-debug.apk")


def _u16(data: bytes, offset: int) -> int:
    return struct.unpack_from("<H", data, offset)[0]


def _u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def _u64(data: bytes, offset: int) -> int:
    return struct.unpack_from("<Q", data, offset)[0]


def parse_elf_load_alignments(elf_bytes: bytes) -> list[int]:
    if len(elf_bytes) < 64 or elf_bytes[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    ei_class = elf_bytes[4]
    ei_data = elf_bytes[5]
    if ei_data != 1:
        raise ValueError("only little-endian ELF is supported")
    if ei_class == 1:
        e_phoff = _u32(elf_bytes, 28)
        e_phentsize = _u16(elf_bytes, 42)
        e_phnum = _u16(elf_bytes, 44)
        p_type_off = 0
        p_align_off = 28
        align_size = 4
    elif ei_class == 2:
        e_phoff = _u64(elf_bytes, 32)
        e_phentsize = _u16(elf_bytes, 54)
        e_phnum = _u16(elf_bytes, 56)
        p_type_off = 0
        p_align_off = 48
        align_size = 8
    else:
        raise ValueError(f"unsupported ELF class {ei_class}")

    aligns: list[int] = []
    for index in range(e_phnum):
        base = e_phoff + index * e_phentsize
        if base + e_phentsize > len(elf_bytes):
            raise ValueError("ELF program header truncated")
        p_type = _u32(elf_bytes, base + p_type_off)
        if p_type != PT_LOAD:
            continue
        if align_size == 4:
            aligns.append(_u32(elf_bytes, base + p_align_off))
        else:
            aligns.append(_u64(elf_bytes, base + p_align_off))
    return aligns


def check_elf_bytes(elf_bytes: bytes, label: str) -> None:
    aligns = parse_elf_load_alignments(elf_bytes)
    if not aligns:
        raise SystemExit(f"{label}: no PT_LOAD segments")
    for align in aligns:
        if align < MIN_P_ALIGN:
            raise SystemExit(
                f"{label}: PT_LOAD p_align={align:#x} < {MIN_P_ALIGN:#x}"
            )


def iter_apk_shared_objects(apk_path: Path):
    with zipfile.ZipFile(apk_path, "r") as zf:
        names = [name for name in zf.namelist() if name.startswith("lib/") and name.endswith(".so")]
        if not names:
            raise SystemExit(f"No lib/**/*.so entries in {apk_path}")
        for name in sorted(names):
            yield name, zf.read(name)


def find_zipalign() -> Path | None:
    env = os.environ.get("ZIPALIGN")
    if env:
        candidate = Path(env)
        if candidate.is_file():
            return candidate
    sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_root:
        return None
    build_tools = Path(sdk_root) / "build-tools"
    if not build_tools.is_dir():
        return None
    versions = sorted(
        (p for p in build_tools.iterdir() if p.is_dir()),
        key=lambda p: p.name,
        reverse=True,
    )
    for version_dir in versions:
        exe = version_dir / ("zipalign.exe" if os.name == "nt" else "zipalign")
        if exe.is_file():
            return exe
    return None


def run_zipalign_check(apk_path: Path, zipalign: Path) -> None:
    cmd = [str(zipalign), "-c", "-P", "16", "-v", "4", str(apk_path)]
    print("Running:", " ".join(cmd))
    completed = subprocess.run(cmd, check=False, capture_output=True, text=True)
    if completed.stdout:
        print(completed.stdout, end="" if completed.stdout.endswith("\n") else "\n")
    if completed.stderr:
        print(completed.stderr, end="" if completed.stderr.endswith("\n") else "\n", file=sys.stderr)
    if completed.returncode != 0:
        raise SystemExit(f"zipalign 16 KiB check failed for {apk_path}")


def verify_apk(apk_path: Path, *, skip_zipalign: bool = False) -> None:
    if not apk_path.is_file():
        raise SystemExit(f"Missing APK: {apk_path}")

    if not skip_zipalign:
        zipalign = find_zipalign()
        if zipalign is None:
            raise SystemExit(
                "zipalign not found; set ANDROID_HOME/ANDROID_SDK_ROOT or ZIPALIGN"
            )
        run_zipalign_check(apk_path, zipalign)
    else:
        print("Skipping zipalign (requested)")

    so_count = 0
    for name, blob in iter_apk_shared_objects(apk_path):
        check_elf_bytes(blob, name)
        so_count += 1
        print(f"OK ELF 16 KiB load align: {name}")
    print(f"Native page-size guard OK ({so_count} shared objects)")


def _minimal_elf64(p_align: int) -> bytes:
    """Tiny ELF64 LE with one PT_LOAD for self-tests (not a runnable binary)."""
    e_phoff = 64
    e_phentsize = 56
    e_phnum = 1
    header = bytearray(64)
    header[0:4] = b"\x7fELF"
    header[4] = 2  # ELFCLASS64
    header[5] = 1  # ELFDATA2LSB
    header[6] = 1  # EV_CURRENT
    struct.pack_into("<H", header, 16, 3)  # ET_DYN
    struct.pack_into("<H", header, 18, 0xB7)  # EM_AARCH64
    struct.pack_into("<I", header, 20, 1)
    struct.pack_into("<Q", header, 32, e_phoff)
    struct.pack_into("<H", header, 52, 64)  # e_ehsize
    struct.pack_into("<H", header, 54, e_phentsize)
    struct.pack_into("<H", header, 56, e_phnum)

    phdr = bytearray(e_phentsize)
    struct.pack_into("<I", phdr, 0, PT_LOAD)
    struct.pack_into("<I", phdr, 4, 5)  # PF_R|PF_X
    struct.pack_into("<Q", phdr, 8, 0)  # p_offset
    struct.pack_into("<Q", phdr, 16, 0)  # p_vaddr
    struct.pack_into("<Q", phdr, 24, 0)  # p_paddr
    struct.pack_into("<Q", phdr, 32, 0x1000)  # p_filesz
    struct.pack_into("<Q", phdr, 40, 0x1000)  # p_memsz
    struct.pack_into("<Q", phdr, 48, p_align)
    return bytes(header + phdr)


def _write_fixture_apk(path: Path, so_name: str, elf_bytes: bytes) -> None:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as zf:
        # Uncompressed entry so local zipalign checks can still inspect contents.
        zf.writestr(so_name, elf_bytes)


def _run_self_tests() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        ok_elf = _minimal_elf64(0x4000)
        bad_elf = _minimal_elf64(0x1000)
        check_elf_bytes(ok_elf, "fixture-ok")
        try:
            check_elf_bytes(bad_elf, "fixture-bad")
        except SystemExit:
            pass
        else:
            raise SystemExit("Expected failing ELF fixture to be rejected")

        ok_apk = root / "ok.apk"
        bad_apk = root / "bad.apk"
        _write_fixture_apk(ok_apk, "lib/arm64-v8a/libok.so", ok_elf)
        _write_fixture_apk(bad_apk, "lib/arm64-v8a/libbad.so", bad_elf)

        verify_apk(ok_apk, skip_zipalign=True)
        try:
            verify_apk(bad_apk, skip_zipalign=True)
        except SystemExit:
            pass
        else:
            raise SystemExit("Expected failing APK fixture to be rejected")

    print("Native page-size guard self-tests OK")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "apk",
        nargs="?",
        default=str(DEFAULT_APK),
        help="path to APK (default: debug app-debug.apk)",
    )
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument(
        "--skip-zipalign",
        action="store_true",
        help="only check ELF PT_LOAD alignments inside the APK",
    )
    args = parser.parse_args(argv)

    if args.self_test:
        _run_self_tests()
        return 0

    try:
        verify_apk(Path(args.apk), skip_zipalign=args.skip_zipalign)
    except SystemExit as exc:
        print(exc, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
