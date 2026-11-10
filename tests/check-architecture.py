#!/usr/bin/env python3
"""Incremental import-boundary guard for new Clean Architecture Java packages.

Legacy source files in the root it.example.passolibero package are deliberately
exempt until migrated; this guard checks imports and fully qualified references,
but cannot statically prove reflection targets or runtime dependency behavior.
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


# Ignore Java comments and literal contents when scanning for qualified references.
# This includes Java text blocks. Keep newlines so diagnostics remain readable.
NON_CODE_RE = re.compile(
    r'(?s:""".*?""")|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|(?s:/\*.*?\*/)'
)
QUALIFIED_RE = re.compile(
    r'\b(?:it\.example\.passolibero|androidx?|com\.android)(?:\.[A-Za-z_$][\w$]*)+\b'
)


def code_only(source: str) -> str:
    """Blank comments and literals, retaining line breaks for diagnostics."""
    return NON_CODE_RE.sub(
        lambda m: "".join("\n" if char == "\n" else " " for char in m.group()),
        source,
    )


def dependency_error(layer: str, target: str, operation: str) -> str | None:
    """Reject forbidden dependencies for an import or an in-code type reference."""
    if layer in ANDROID_FREE and target.startswith(
        ("android.", "androidx.", "com.android.")
    ):
        return f"{layer} must not {operation} Android: {target}"
    prefix = ROOT_PACKAGE + "."
    if target.startswith(prefix):
        root = target[len(prefix):].split(".", 1)[0]
        if root in LAYERS and root not in LAYERS[layer]:
            return f"{layer} must not {operation} {root}: {target}"
    return None


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
        # Java's comments, strings, chars and text blocks are never executable
        # dependencies. Inspect declarations/imports before scanning the body.
        code = code_only(source.read_text(encoding="utf-8"))
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
            error = dependency_error(layer, imported, "import")
            if error:
                messages.append(f"{source}: {error}")

        # A reference such as new it.example.passolibero.data.SqliteSessions()
        # bypasses the import guard. The package/import lines are blanked to
        # avoid counting a declaration or import twice.
        body = PACKAGE_RE.sub("", code)
        body = IMPORT_RE.sub("", body)
        for match in QUALIFIED_RE.finditer(body):
            target = match.group(0)
            error = dependency_error(layer, target, "reference")
            if error:
                messages.append(f"{source}: {error}")
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
