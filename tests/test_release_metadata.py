import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import os

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("release_metadata", ROOT / "ci" / "release_metadata.py")
POLICY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(POLICY)
CERT = "a" * 64


class ReleasePolicyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.path = Path(self.temporary.name)

    def previous(self, code=10091, cert=CERT):
        metadata = {
            "schema": 1, "channel": "debug-pr-2", "packageName": "it.example.passolibero",
            "versionCode": code, "apk": POLICY.name(code),
            "sha256": "b" * 64, "certificateSha256": cert,
        }
        p = self.path / "update.json"
        p.write_text(json.dumps(metadata), encoding="utf-8")
        return p

    def test_monotonic_version(self):
        p = self.previous()
        self.assertEqual(POLICY.check_next(10092, p, CERT), 10091)
        for code in (10091, 10090):
            with self.assertRaises(ValueError):
                POLICY.check_next(code, p, CERT)

    def test_no_reset_or_rotation(self):
        with self.assertRaises(ValueError):
            POLICY.check_next(10092, self.path / "absent.json", CERT)
        with self.assertRaises(ValueError):
            POLICY.check_next(10092, self.previous(cert="c" * 64), CERT)

    def test_previous_metadata_is_validated(self):
        p = self.previous()
        metadata = json.loads(p.read_text())
        metadata["channel"] = "stable"
        p.write_text(json.dumps(metadata), encoding="utf-8")
        with self.assertRaises(ValueError):
            POLICY.check_next(10092, p, CERT)

    def test_integer_bounds(self):
        self.assertEqual(POLICY.version_code("10092"), 10092)
        for invalid in ("0", "-1", "+1", "01", "1.2", "2147483648", "abc"):
            with self.assertRaises(ValueError):
                POLICY.version_code(invalid)

    def test_generate_update_metadata(self):
        code = 10092
        apk = self.path / POLICY.name(code)
        apk.write_bytes(b"synthetic test fixture")
        metadata = POLICY.generate(code, apk, CERT, "f" * 40)
        self.assertEqual(metadata["channel"], "debug-pr-2")
        self.assertEqual(metadata["apk"], apk.name)
        self.assertEqual(metadata["sourceBranch"], "main")
        self.assertEqual(metadata["sha256"], POLICY.hash_file(apk))

    def test_signing_script_fails_without_secret(self):
        script = (ROOT / "ci" / "prepare-debug-signing.sh").read_text()
        self.assertNotIn("git push", script)
        self.assertNotIn("keytool -genkeypair", script)
        env = dict(os.environ)
        env.pop("PASSOLIBERO_SIGNING_PASSPHRASE", None)
        env["RUNNER_TEMP"] = self.temporary.name
        env["GITHUB_ENV"] = str(self.path / "github_env")
        run = subprocess.run(["bash", "ci/prepare-debug-signing.sh"],
                             cwd=ROOT, env=env, capture_output=True, text=True, check=False)
        self.assertNotEqual(run.returncode, 0)
        self.assertIn("Missing", run.stdout + run.stderr)

    def test_untrusted_ci_stays_unprivileged(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        release = (ROOT / ".github/workflows/release-debug.yml").read_text()
        self.assertIn("permissions:\n  contents: read", ci)
        self.assertNotIn("secrets.", "\\n".join(line for line in ci.splitlines() if not line.lstrip().startswith("#")))
        self.assertNotIn("gh release upload", ci)
        self.assertNotIn("pull_request:", release)
        self.assertIn("workflow_dispatch:", release)
        self.assertIn("github.ref == 'refs/heads/main'", release)
        self.assertIn("environment: apk-release", release)
        self.assertIn("contents: write", release)


if __name__ == "__main__":
    unittest.main()
