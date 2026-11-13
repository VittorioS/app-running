#!/usr/bin/env python3
"""Fail-closed binary audit of a locally built, unsigned Android release APK.

Uses installed Android SDK tools; does not sign, publish, install or upload the APK.
Checks both actual DEX class definitions and the R8 original-name mapping because
minification can rename a mistakenly included debug-only class.
"""
from __future__ import annotations

import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

FORBIDDEN = ("BatteryRecorder", "BatteryAutoCapture", "BatteryDiagnosticsActivity")
PACKAGE = "it.example.passolibero"
CLASS_DESCRIPTOR = re.compile(r"(?m)^\s*Class descriptor\s*:\s*'([^']+)'\s*$")
DEX_FILENAME = re.compile(r"classes(?:[0-9]+)?\.dex")


def _tool_output(runner, command: list[str], label: str) -> str:
    try:
        result = runner(command, check=True, capture_output=True, text=True)
    except (OSError, subprocess.CalledProcessError) as exc:
        raise ValueError(f"{label} failed: unable to inspect unsigned APK") from exc
    if result.returncode != 0:
        raise ValueError(f"{label} failed: nonzero exit status")
    return result.stdout


def audit_release(
    apk: Path,
    mapping: Path,
    dexdump: Path,
    aapt: Path,
    runner=subprocess.run,
) -> int:
    """Return inspected DEX count or raise for any missing evidence/debug inclusion."""
    apk, mapping, dexdump, aapt = map(Path, (apk, mapping, dexdump, aapt))
    for label, path in (
        ("APK", apk),
        ("R8 mapping", mapping),
        ("dexdump", dexdump),
        ("aapt", aapt),
    ):
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f"{label} missing or empty: {path}")

    mapping_text = mapping.read_text(encoding="utf-8")
    if not mapping_text.strip() or " -> " not in mapping_text:
        raise ValueError("R8 mapping missing class entries")
    for name in FORBIDDEN:
        original = re.compile(
            r"(?m)^" + re.escape(PACKAGE + "." + name) + r"\s+->\s+\S+:\s*$"
        )
        if original.search(mapping_text):
            raise ValueError(f"Release R8 mapping contains debug class {name}")

    try:
        with zipfile.ZipFile(apk) as archive:
            names = archive.namelist()
            dex_names = sorted(n for n in names if DEX_FILENAME.fullmatch(n))
            if not dex_names or "AndroidManifest.xml" not in names:
                raise ValueError("APK must contain DEX class definitions and AndroidManifest.xml")
            with tempfile.TemporaryDirectory(prefix="passolibero-release-audit-") as directory:
                for index, name in enumerate(dex_names):
                    extracted = Path(directory) / f"classes-{index}.dex"
                    with archive.open(name) as source, extracted.open("wb") as destination:
                        shutil.copyfileobj(source, destination)
                    output = _tool_output(
                        runner, [str(dexdump), "-f", str(extracted)], "dexdump"
                    )
                    descriptors = CLASS_DESCRIPTOR.findall(output)
                    if not descriptors:
                        raise ValueError(f"DEX class definitions not readable from {name}")
                    for forbidden in FORBIDDEN:
                        descriptor = (
                            "L" + PACKAGE.replace(".", "/") + "/" + forbidden + ";"
                        )
                        if descriptor in descriptors:
                            raise ValueError(
                                f"Release DEX {name} contains debug class {forbidden}"
                            )
    except (zipfile.BadZipFile, OSError) as exc:
        raise ValueError("APK could not be read as a valid ZIP archive") from exc

    manifest = _tool_output(
        runner, [str(aapt), "dump", "xmltree", str(apk), "AndroidManifest.xml"], "aapt"
    )
    if not re.search(r"(?m)^\s*E:\s+manifest\b", manifest):
        raise ValueError("aapt did not decode the release APK manifest")
    if "BatteryDiagnosticsActivity" in manifest:
        raise ValueError("Release manifest contains debug BatteryDiagnosticsActivity")
    return len(dex_names)


def main(argv: list[str]) -> int:
    if len(argv) != 5:
        print(
            "Usage: check-release-battery-apk.py "
            "<unsigned-release.apk> <mapping.txt> <dexdump> <aapt>",
            file=sys.stderr,
        )
        return 2
    try:
        count = audit_release(*(Path(x) for x in argv[1:]))
    except (ValueError, UnicodeError) as exc:
        print(f"Release APK battery audit FAILED: {exc}", file=sys.stderr)
        return 1
    print(
        "Release APK battery audit: OK "
        f"({count} DEX file(s), R8 mapping and merged manifest inspected)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
