#!/usr/bin/env python3
"""Incremental import-boundary guard for new Clean Architecture Java packages.

Legacy source files in the root it.example.passolibero package are deliberately
exempt until migrated; import-based checks cannot detect reflection or FQCN use.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT_PACKAGE = "it.example.passolibero"
LAYERS = {
    "domain": {"domain"},
    "application": {"application", "domain"},
    "data": {"data", "application", "domain"},
    "platform": {"platform", "data", "application", "domain"},
    "presentation": {"presentation", "application", "domain"},
}
ANDROID_FREE = frozenset({"domain", "application"})
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.*]+)\s*;", re.MULTILINE)


def violations(source_root: Path) -> list[str]:
    """Return actionable diagnostics for layered Java files under app/src."""
    messages = []
    for source in sorted(source_root.glob("*/java/it/example/passolibero/**/*.java")):
        try:
            parts = source.relative_to(source_root).parts
            start = parts.index("passolibero") + 1
        except (ValueError, IndexError):
            continue
        relative = parts[start:]
        if not relative or relative[0] not in LAYERS:
            continue  # All pre-existing root-package files are legacy.
        layer = relative[0]
        expected_package = ROOT_PACKAGE + "." + ".".join(relative[:-1])
        code = source.read_text(encoding="utf-8")
        package_match = PACKAGE_RE.search(code)
        actual_package = package_match.group(1) if package_match else "(missing)"
        if actual_package != expected_package:
            messages.append(
                f"{source}: package {actual_package!r}, expected {expected_package!r}"
            )
        for match in IMPORT_RE.finditer(code):
            imported = match.group(1)
            if imported.endswith(".*"):
                messages.append(f"{source}: wildcard imports forbidden: {imported}")
            if layer in ANDROID_FREE and imported.startswith(("android.", "androidx.", "com.android.")):
                messages.append(f"{source}: {layer} must not import Android: {imported}")
            prefix = ROOT_PACKAGE + "."
            if imported.startswith(prefix):
                root = imported[len(prefix):].split(".", 1)[0]
                if root in LAYERS and root not in LAYERS[layer]:
                    messages.append(
                        f"{source}: {layer} must not import {root}: {imported}"
                    )
    return messages


def main() -> int:
    sources = Path(__file__).resolve().parents[1] / "app" / "src"
    messages = violations(sources)
    for message in messages:
        print(message, file=sys.stderr)
    if messages:
        print(f"Architecture check failed: {len(messages)} violation(s).", file=sys.stderr)
        return 1
    print("Architecture package/import rules: OK (legacy root package excluded).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
