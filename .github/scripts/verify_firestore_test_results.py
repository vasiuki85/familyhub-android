#!/usr/bin/env python3
"""Verify firebase emulators:exec + node --test ran the full Firestore rules suite."""

from __future__ import annotations

import argparse
import re
import sys
import tempfile
from pathlib import Path

COUNTERS = ("tests", "pass", "fail", "skipped", "cancelled", "todo")
COUNTER_RE = re.compile(
    r"(?:^|\s)(?P<name>tests|pass|fail|skipped|cancelled|todo)\s+(?P<value>\d+)"
)
SUMMARY_CONTINUATION_RE = re.compile(r"(?:^|\s)(?:suites|duration_ms)\s+\d+")


def _read_log_text(log_file: Path) -> str:
    raw = log_file.read_bytes()
    if b"\x00" in raw:
        raw = raw.replace(b"\x00", b"")
    return raw.decode("utf-8", errors="replace")


def _extract_summary_blocks(log_text: str) -> list[dict[str, int]]:
    blocks: list[dict[str, int]] = []
    current: dict[str, int] | None = None

    for line in log_text.splitlines():
        stripped = line.strip()
        if not stripped:
            if current is not None and "tests" in current:
                blocks.append(current)
            current = None
            continue

        counter_matches = list(COUNTER_RE.finditer(stripped))
        if counter_matches:
            if current is None:
                current = {}
            for match in counter_matches:
                current[match.group("name")] = int(match.group("value"))
            continue

        if current is not None and SUMMARY_CONTINUATION_RE.search(stripped):
            continue

        if current is not None and "tests" in current:
            blocks.append(current)
        current = None

    if current is not None and "tests" in current:
        blocks.append(current)

    return blocks


def _parse_counters(log_text: str) -> dict[str, int]:
    blocks = _extract_summary_blocks(log_text)
    if not blocks:
        return {}
    return blocks[-1]


def verify_log(log_file: Path, expected_tests: int = 76) -> None:
    if not log_file.is_file():
        raise SystemExit(f"Missing Firestore test log: {log_file}")

    counters = _parse_counters(_read_log_text(log_file))
    missing = [name for name in COUNTERS if name not in counters]
    if missing:
        raise SystemExit(f"Missing Firestore test counters in log: {', '.join(missing)}")

    tests = counters["tests"]
    passed = counters["pass"]
    failed = counters["fail"]
    skipped = counters["skipped"]
    cancelled = counters["cancelled"]
    todo = counters["todo"]

    if tests <= 0:
        raise SystemExit("Zero Firestore rules tests executed")

    if tests != expected_tests:
        raise SystemExit(f"Expected {expected_tests} Firestore tests, got {tests}")

    if passed != expected_tests:
        raise SystemExit(
            f"Expected pass == {expected_tests}, got pass={passed} "
            f"(tests={tests}, fail={failed}, skipped={skipped}, cancelled={cancelled}, todo={todo})"
        )

    if failed != 0 or skipped != 0 or cancelled != 0 or todo != 0:
        raise SystemExit(
            "Firestore counters must be fail=skipped=cancelled=todo=0; "
            f"got fail={failed}, skipped={skipped}, cancelled={cancelled}, todo={todo}"
        )

    print(f"Firestore rules tests passed: tests={tests} pass={passed}")


def _run_self_tests() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)

        ok_log = root / "ok.log"
        ok_log.write_text(
            "\n".join(
                [
                    "ℹ tests 67",
                    "ℹ pass 67",
                    "ℹ fail 0",
                    "ℹ cancelled 0",
                    "ℹ skipped 0",
                    "ℹ todo 0",
                ]
            ),
            encoding="utf-8",
        )
        verify_log(ok_log, 67)

        missing_counters = root / "missing.log"
        missing_counters.write_text("ℹ tests 67\nℹ pass 67\n", encoding="utf-8")
        _expect_failure(lambda: verify_log(missing_counters, 67), "missing counters")

        skipped_one = root / "skipped.log"
        skipped_one.write_text(
            "\n".join(
                [
                    "ℹ tests 67",
                    "ℹ pass 66",
                    "ℹ fail 0",
                    "ℹ cancelled 0",
                    "ℹ skipped 1",
                    "ℹ todo 0",
                ]
            ),
            encoding="utf-8",
        )
        _expect_failure(lambda: verify_log(skipped_one, 67), "66 pass + 1 skipped")

        false_name = root / "false-name.log"
        false_name.write_text(
            "FamilyHubMigrationsTest mentioned in stderr\nℹ tests 67\nℹ pass 67\n",
            encoding="utf-8",
        )
        _expect_failure(lambda: verify_log(false_name, 67), "missing counters with false text")

        stale_then_incomplete = root / "stale-then-incomplete.log"
        stale_then_incomplete.write_text(
            "\n".join(
                [
                    "older emulator boot",
                    "ℹ tests 67",
                    "ℹ pass 67",
                    "ℹ fail 0",
                    "ℹ cancelled 0",
                    "ℹ skipped 0",
                    "ℹ todo 0",
                    "ℹ duration_ms 12000",
                    "rerun started",
                    "ℹ tests 67",
                    "ℹ pass 67",
                ]
            ),
            encoding="utf-8",
        )
        _expect_failure(
            lambda: verify_log(stale_then_incomplete, 67),
            "full old summary block plus incomplete latest block",
        )

        stale_then_complete = root / "stale-then-complete.log"
        stale_then_complete.write_text(
            "\n".join(
                [
                    "ℹ tests 10",
                    "ℹ pass 10",
                    "ℹ fail 0",
                    "ℹ cancelled 0",
                    "ℹ skipped 0",
                    "ℹ todo 0",
                    "rerun finished",
                    "ℹ tests 67",
                    "ℹ pass 67",
                    "ℹ fail 0",
                    "ℹ cancelled 0",
                    "ℹ skipped 0",
                    "ℹ todo 0",
                ]
            ),
            encoding="utf-8",
        )
        verify_log(stale_then_complete, 67)

    print("Firestore verifier self-tests OK")


def _expect_failure(fn, label: str) -> None:
    try:
        fn()
    except SystemExit:
        return
    raise SystemExit(f"Expected failure for self-test case: {label}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("log_file", nargs="?", default="firestore-test.log")
    parser.add_argument("expected_tests", nargs="?", type=int, default=76)
    parser.add_argument("--self-test", action="store_true", help="run verifier self-tests")
    args = parser.parse_args(argv)

    if args.self_test:
        _run_self_tests()
        return 0

    try:
        verify_log(Path(args.log_file), args.expected_tests)
    except SystemExit as exc:
        print(exc, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
