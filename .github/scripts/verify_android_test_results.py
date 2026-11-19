#!/usr/bin/env python3
"""Verify connectedDebugAndroidTest produced real, non-skipped JUnit results."""

from __future__ import annotations

import argparse
import sys
import tempfile
import textwrap
import xml.etree.ElementTree as ET
from pathlib import Path

REQUIRED_CLASSES = (
    "FamilyHubMigrationsTest",
    "TaskManualOrderTransactionInstrumentedTest",
    "TaskCompletionTransitionCoordinatorInstrumentedTest",
    "AccountingMutationCoordinatorInstrumentedTest",
    "MenuMutationCoordinatorInstrumentedTest",
    "ShoppingTopBarTest",
    "Argon2KtEncryptedBackupInstrumentedTest",
    "EncryptedRestoreRoomInstrumentedTest",
    "EncryptedBackupRestoreSectionTest",
)

DEFAULT_RESULTS_DIR = Path("app/build/outputs/androidTest-results/connected")


def _is_skipped(testcase: ET.Element) -> bool:
    if testcase.find("skipped") is not None:
        return True
    return testcase.get("status") == "not run"


def _class_simple_name(classname: str) -> str:
    return classname.rsplit(".", 1)[-1]


def _class_matches_required(classname: str, required: str) -> bool:
    simple = _class_simple_name(classname)
    return simple == required or classname.endswith("." + required)


def verify_results(results_dir: Path) -> None:
    if not results_dir.is_dir():
        raise SystemExit(f"Missing androidTest results directory: {results_dir}")

    xml_files = sorted(results_dir.rglob("TEST-*.xml"))
    if not xml_files:
        raise SystemExit(f"No TEST-*.xml files under {results_dir}")

    executed_testcases: list[tuple[str, str]] = []
    for xml_file in xml_files:
        root = ET.parse(xml_file).getroot()
        for testcase in root.iter("testcase"):
            classname = testcase.get("classname", "")
            name = testcase.get("name", "")
            if not classname:
                continue
            if _is_skipped(testcase):
                continue
            executed_testcases.append((classname, name))

    if not executed_testcases:
        raise SystemExit("Zero non-skipped instrumented testcases in TEST-*.xml files")

    print(f"Instrumented testcases executed (non-skipped): {len(executed_testcases)}")

    executed_classnames = {classname for classname, _ in executed_testcases}
    for required in REQUIRED_CLASSES:
        if not any(_class_matches_required(classname, required) for classname in executed_classnames):
            raise SystemExit(
                f"Required instrumented test class has no non-skipped testcase: {required}"
            )

    print("All required instrumented test classes have non-skipped testcases.")


def _run_self_tests() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)

        # Success case: all mandatory classes with executed testcases.
        ok_dir = root / "ok"
        ok_dir.mkdir()
        ok_xml = textwrap.dedent(
            """\
            <?xml version="1.0" encoding="utf-8"?>
            <testsuite name="com.familyhub.All" tests="9" failures="0" skipped="0">
              <testcase classname="com.familyhub.data.local.db.FamilyHubMigrationsTest" name="migrate" />
              <testcase classname="com.familyhub.data.repository.TaskManualOrderTransactionInstrumentedTest" name="a" />
              <testcase classname="com.familyhub.data.repository.TaskCompletionTransitionCoordinatorInstrumentedTest" name="b" />
              <testcase classname="com.familyhub.data.repository.AccountingMutationCoordinatorInstrumentedTest" name="c" />
              <testcase classname="com.familyhub.data.repository.MenuMutationCoordinatorInstrumentedTest" name="menu" />
              <testcase classname="com.familyhub.ui.screens.shopping.ShoppingTopBarTest" name="d" />
              <testcase classname="com.familyhub.data.backup.Argon2KtEncryptedBackupInstrumentedTest" name="e" />
              <testcase classname="com.familyhub.data.restore.EncryptedRestoreRoomInstrumentedTest" name="f" />
              <testcase classname="com.familyhub.ui.screens.settings.encrypted.EncryptedBackupRestoreSectionTest" name="g" />
            </testsuite>
            """
        )
        (ok_dir / "TEST-com.familyhub.All.xml").write_text(ok_xml, encoding="utf-8")
        verify_results(ok_dir)

        # False positive: class name only appears in XML text, not as executed testcase classname.
        false_dir = root / "false-positive"
        false_dir.mkdir()
        false_xml = textwrap.dedent(
            """\
            <?xml version="1.0" encoding="utf-8"?>
            <testsuite name="com.familyhub.Fake" tests="1" failures="0" skipped="0">
              <testcase classname="com.familyhub.OtherTest" name="mentions FamilyHubMigrationsTest in message">
                <failure message="FamilyHubMigrationsTest missing" />
              </testcase>
            </testsuite>
            """
        )
        (false_dir / "TEST-com.familyhub.Fake.xml").write_text(false_xml, encoding="utf-8")
        _expect_failure(lambda: verify_results(false_dir), "false class name in text")

        # Skipped mandatory class must not count as executed.
        skipped_dir = root / "skipped"
        skipped_dir.mkdir()
        skipped_xml = textwrap.dedent(
            """\
            <?xml version="1.0" encoding="utf-8"?>
            <testsuite name="com.familyhub.Skipped" tests="1" failures="0" skipped="1">
              <testcase classname="com.familyhub.data.local.db.FamilyHubMigrationsTest" name="migrate">
                <skipped />
              </testcase>
            </testsuite>
            """
        )
        (skipped_dir / "TEST-com.familyhub.Skipped.xml").write_text(skipped_xml, encoding="utf-8")
        _expect_failure(lambda: verify_results(skipped_dir), "skipped mandatory class")

        # 53 executed + 1 skipped mandatory should still fail.
        mixed_dir = root / "mixed"
        mixed_dir.mkdir()
        mixed_xml = textwrap.dedent(
            """\
            <?xml version="1.0" encoding="utf-8"?>
            <testsuite name="com.familyhub.Mixed" tests="54" failures="0" skipped="1">
              <testcase classname="com.familyhub.data.local.db.FamilyHubMigrationsTest" name="migrate">
                <skipped />
              </testcase>
              <testcase classname="com.familyhub.data.repository.TaskManualOrderTransactionInstrumentedTest" name="a" />
              <testcase classname="com.familyhub.data.repository.TaskCompletionTransitionCoordinatorInstrumentedTest" name="b" />
              <testcase classname="com.familyhub.data.repository.AccountingMutationCoordinatorInstrumentedTest" name="c" />
              <testcase classname="com.familyhub.data.repository.MenuMutationCoordinatorInstrumentedTest" name="menu" />
              <testcase classname="com.familyhub.ui.screens.shopping.ShoppingTopBarTest" name="d" />
              <testcase classname="com.familyhub.data.backup.Argon2KtEncryptedBackupInstrumentedTest" name="e" />
              <testcase classname="com.familyhub.data.restore.EncryptedRestoreRoomInstrumentedTest" name="f" />
              <testcase classname="com.familyhub.ui.screens.settings.encrypted.EncryptedBackupRestoreSectionTest" name="g" />
            </testsuite>
            """
        )
        (mixed_dir / "TEST-com.familyhub.Mixed.xml").write_text(mixed_xml, encoding="utf-8")
        _expect_failure(lambda: verify_results(mixed_dir), "53 pass + 1 skipped mandatory")

    print("Android verifier self-tests OK")


def _expect_failure(fn, label: str) -> None:
    try:
        fn()
    except SystemExit:
        return
    raise SystemExit(f"Expected failure for self-test case: {label}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "results_dir",
        nargs="?",
        default=str(DEFAULT_RESULTS_DIR),
        help="androidTest connected results directory",
    )
    parser.add_argument("--self-test", action="store_true", help="run verifier self-tests")
    args = parser.parse_args(argv)

    if args.self_test:
        _run_self_tests()
        return 0

    try:
        verify_results(Path(args.results_dir))
    except SystemExit as exc:
        print(exc, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
