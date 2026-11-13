#!/usr/bin/env python3
"""Guard ADR-002 P9a: battery recorder and auto-capture stay debug-only.

This checks source-set placement and manifests, not compiled release APK contents.
A separate release-package inspection/manual Android 11 test is still required.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

DEBUG_CLASSES = (
    "BatteryRecorder",
    "BatteryAutoCapture",
    "BatteryDiagnosticsActivity",
)
PACKAGE = "it.example.passolibero"
ACTIVITY = "BatteryDiagnosticsActivity"


def has_declaration(source: str, name: str) -> bool:
    """Detect class declarations even if the source file has an unexpected name."""
    return bool(
        re.search(
            r"\b(?:class|interface|record|enum)\s+" + re.escape(name) + r"\b",
            source,
        )
    )


def has_debug_activity(manifest: Path) -> bool:
    if not manifest.is_file():
        return False
    body = manifest.read_text(encoding="utf-8")
    return bool(
        re.search(
            r'android:name\s*=\s*["\'](?:\.|it\.example\.passolibero\.)'
            + ACTIVITY
            + r'["\']',
            body,
        )
    )


def violations(app: Path) -> list[str]:
    """Find accidental release/main inclusions or missing debug-only declarations."""
    problems: list[str] = []
    debug = app / "src/debug/java/it/example/passolibero"
    for name in DEBUG_CLASSES:
        source = debug / (name + ".java")
        if not source.is_file():
            problems.append(f"{source}: missing required debug-only source")
            continue
        code = source.read_text(encoding="utf-8")
        if not has_declaration(code, name):
            problems.append(f"{source}: expected declaration of {name}")
        if not re.search(r"(?m)^\s*package\s+" + PACKAGE.replace(".", r"\.") + r"\s*;", code):
            problems.append(f"{source}: unexpected Java package")

    debug_manifest = app / "src/debug/AndroidManifest.xml"
    if not has_debug_activity(debug_manifest):
        problems.append(f"{debug_manifest}: missing debug-only {ACTIVITY} registration")

    for source_set in ("main", "release"):
        source_root = app / "src" / source_set / "java"
        for java in sorted(source_root.rglob("*.java")):
            # A direct class declaration under main/release would be compiled into
            # that variant; reflection strings referring to debug remain allowed.
            code = java.read_text(encoding="utf-8")
            for name in DEBUG_CLASSES:
                if has_declaration(code, name):
                    problems.append(
                        f"{java}: {name} must be declared only in src/debug/java"
                    )

        manifest = app / "src" / source_set / "AndroidManifest.xml"
        if has_debug_activity(manifest):
            problems.append(
                f"{manifest}: {ACTIVITY} must only be registered in the debug manifest"
            )
    return problems


def main() -> int:
    app = Path(__file__).resolve().parents[1] / "app"
    found = violations(app)
    for item in found:
        print(item, file=sys.stderr)
    if found:
        print(f"Debug battery source-set guard: {len(found)} violation(s).", file=sys.stderr)
        return 1
    print("Debug battery source-set guard: OK (source placement only).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
