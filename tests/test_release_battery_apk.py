"""Offline contract tests for the unsigned-release battery DEX/manifest audit (#71)."""

import importlib.util
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path


SCRIPT = Path(__file__).with_name("check-release-battery-apk.py")
SPEC = importlib.util.spec_from_file_location("passolibero_release_battery_audit", SCRIPT)
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)

CLEAN_DEXDUMP = (
    "Opened 'classes.dex', DEX version '035'\n"
    "Class #0\n"
    "  Class descriptor  : 'Lit/example/passolibero/TrackingService;'\n"
)
CLEAN_MANIFEST = (
    "N: android=http://schemas.android.com/apk/res/android\n"
    "E: manifest (line=1)\n"
    "  E: application (line=5)\n"
    "    E: activity (line=10)\n"
    '      A: android:name="it.example.passolibero.MainActivity"\n'
)


class ReleaseBatteryBinaryAuditTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "app-release-unsigned.apk"
        self.mapping = self.root / "mapping.txt"
        self.dexdump = self.root / "dexdump"
        self.aapt = self.root / "aapt"
        self.dexdump.write_text("fake dexdump tool", encoding="utf-8")
        self.aapt.write_text("fake aapt tool", encoding="utf-8")
        self.mapping.write_text(
            "it.example.passolibero.TrackingService -> a.a:\n", encoding="utf-8"
        )
        self.make_apk()
        self.dex_output = CLEAN_DEXDUMP
        self.manifest_output = CLEAN_MANIFEST
        self.calls = []
        self.dex_error = None

    def make_apk(self, dex_entries=None):
        entries = dex_entries or {"classes.dex": b"synthetic-dex"}
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("AndroidManifest.xml", b"binary-xml-fixture")
            for name, value in entries.items():
                archive.writestr(name, value)

    def fake_run(self, command, **kwargs):
        self.calls.append(command)
        self.assertTrue(kwargs.get("check"))
        self.assertTrue(kwargs.get("capture_output"))
        self.assertTrue(kwargs.get("text"))
        if command[0] == str(self.dexdump):
            if self.dex_error is not None:
                raise subprocess.CalledProcessError(2, command, stderr=self.dex_error)
            return subprocess.CompletedProcess(command, 0, self.dex_output, "")
        if command[0] == str(self.aapt):
            return subprocess.CompletedProcess(command, 0, self.manifest_output, "")
        self.fail("Unexpected external command: " + str(command))

    def audit(self):
        return AUDIT.audit_release(
            self.apk, self.mapping, self.dexdump, self.aapt, runner=self.fake_run
        )

    def test_accepts_clean_release_and_invokes_both_platform_tools(self):
        self.audit()
        self.assertEqual(2, len(self.calls))
        self.assertEqual(str(self.dexdump), self.calls[0][0])
        self.assertEqual(str(self.aapt), self.calls[1][0])

    def test_rejects_original_debug_recorder_dex_class(self):
        self.dex_output += (
            "Class #1\n"
            "  Class descriptor  : 'Lit/example/passolibero/BatteryRecorder;'\n"
        )
        with self.assertRaisesRegex(ValueError, "BatteryRecorder"):
            self.audit()

    def test_rejects_release_mapping_containing_obfuscated_debug_class(self):
        self.mapping.write_text(
            "it.example.passolibero.BatteryAutoCapture -> a.b:\n"
            "    void onSessionStart() -> a\n",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "BatteryAutoCapture"):
            self.audit()

    def test_rejects_debug_diagnostics_activity_from_apk_manifest(self):
        self.manifest_output += (
            "    E: activity (line=40)\n"
            '      A: android:name="it.example.passolibero.BatteryDiagnosticsActivity"\n'
        )
        with self.assertRaisesRegex(ValueError, "BatteryDiagnosticsActivity"):
            self.audit()

    def test_string_only_reference_is_not_mistaken_for_dex_class_definition(self):
        self.dex_output += (
            "  #string reference: Lit/example/passolibero/BatteryRecorder;\n"
        )
        self.audit()

    def test_absent_unsigned_release_apk_fails_closed(self):
        self.apk.unlink()
        with self.assertRaisesRegex(ValueError, "APK"):
            self.audit()

    def test_absent_mapping_fails_closed_even_if_dex_looks_clean(self):
        self.mapping.unlink()
        with self.assertRaisesRegex(ValueError, "mapping"):
            self.audit()

    def test_missing_dex_or_malformed_dexdump_fails_closed(self):
        self.make_apk({"not-a-dex.bin": b"test"})
        with self.assertRaisesRegex(ValueError, "DEX"):
            self.audit()
        self.make_apk()
        self.dex_output = "no class definitions emitted"
        with self.assertRaisesRegex(ValueError, "DEX"):
            self.audit()

    def test_failed_dexdump_is_not_interpreted_as_no_debug_classes(self):
        self.dex_error = "broken dex"
        with self.assertRaisesRegex(ValueError, "dexdump"):
            self.audit()

    def test_missing_binary_analyzer_fails_closed(self):
        self.aapt.unlink()
        with self.assertRaisesRegex(ValueError, "aapt"):
            self.audit()

    def test_multiple_dex_files_are_all_examined(self):
        self.make_apk({"classes.dex": b"one", "classes2.dex": b"two"})
        self.audit()
        dex_calls = [x for x in self.calls if x[0] == str(self.dexdump)]
        self.assertEqual(2, len(dex_calls))


if __name__ == "__main__":
    unittest.main()
