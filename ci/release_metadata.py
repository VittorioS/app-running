#!/usr/bin/env python3
"""Validate and generate the JSON update channel for trusted APK releases."""
import argparse
import hashlib
import json
import re
from pathlib import Path

CHANNEL = "debug-pr-2"
PACKAGE = "it.example.passolibero"
SHA256 = re.compile(r"[a-fA-F0-9]{64}\Z")
COMMIT = re.compile(r"[a-f0-9]{40}\Z")
MAX_CODE = 2_147_483_647


def version_code(text):
    if not re.fullmatch(r"[1-9][0-9]*", str(text)):
        raise ValueError("Version code must be a positive decimal integer")
    code = int(text)
    if code > MAX_CODE:
        raise ValueError("Version code exceeds Android integer limit")
    return code


def certificate(text):
    digest = text.strip().lower()
    if not SHA256.fullmatch(digest):
        raise ValueError("Invalid SHA-256 certificate fingerprint")
    return digest


def name(code):
    return f"passolibero-debug-v{code}.apk"


def previous_version(metadata_path, expected_cert):
    if not metadata_path.is_file():
        raise ValueError("Existing debug-pr-2/update.json is required")
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    if (metadata.get("schema") != 1 or metadata.get("channel") != CHANNEL
            or metadata.get("packageName") != PACKAGE):
        raise ValueError("Previous channel, schema or application ID mismatch")
    old_code = version_code(str(metadata.get("versionCode", "")))
    if metadata.get("apk") != name(old_code):
        raise ValueError("Previous APK name/version mismatch")
    if certificate(str(metadata.get("certificateSha256", ""))) != expected_cert:
        raise ValueError("The previous APK used another signing certificate")
    certificate(str(metadata.get("sha256", "")))
    return old_code


def check_next(code, previous_path, expected_cert):
    previous = previous_version(previous_path, expected_cert)
    if code <= previous:
        raise ValueError(f"versionCode {code} must exceed published {previous}")
    return previous


def hash_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(128 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def generate(code, apk, expected_cert, source_commit):
    if not COMMIT.fullmatch(source_commit):
        raise ValueError("Invalid source commit")
    if apk.name != name(code) or not apk.is_file() or not apk.stat().st_size:
        raise ValueError("APK filename or contents invalid")
    return {
        "schema": 1,
        "channel": CHANNEL,
        "packageName": PACKAGE,
        "versionCode": code,
        "apk": name(code),
        "sha256": hash_file(apk),
        "certificateSha256": expected_cert,
        "sourceCommit": source_commit,
        "sourceBranch": "main",
    }


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="action", required=True)
    verify = commands.add_parser("check")
    verify.add_argument("--code", required=True)
    verify.add_argument("--previous", type=Path, required=True)
    verify.add_argument("--fingerprint", type=Path, required=True)
    publish = commands.add_parser("generate")
    publish.add_argument("--code", required=True)
    publish.add_argument("--apk", type=Path, required=True)
    publish.add_argument("--fingerprint", type=Path, required=True)
    publish.add_argument("--source-commit", required=True)
    publish.add_argument("--output", type=Path, required=True)

    args = parser.parse_args()
    code = version_code(args.code)
    cert = certificate(args.fingerprint.read_text(encoding="utf-8"))
    if args.action == "check":
        previous = check_next(code, args.previous, cert)
        print(f"versionCode {code} > previous version {previous}; certificate OK")
    else:
        metadata = generate(code, args.apk, cert, args.source_commit)
        args.output.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        print(f"Metadata verified: versionCode {code} hash {metadata['sha256']}")


if __name__ == "__main__":
    main()
