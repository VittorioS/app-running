"""Self-tests: architecture rules must fail on illegal imports and packages."""
import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("check-architecture.py")
SPEC = importlib.util.spec_from_file_location("passolibero_architecture_check", SCRIPT)
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class ArchitectureGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def create(self, layer, name, imports="", package=None, source_set="main", subpackage=""):
        folder = self.root / source_set / "java/it/example/passolibero" / layer
        if subpackage:
            folder = folder / subpackage.replace(".", "/")
        folder.mkdir(parents=True, exist_ok=True)
        suffix = "." + subpackage if subpackage else ""
        pkg = package or "it.example.passolibero." + layer + suffix
        path = folder / (name + ".java")
        path.write_text(
            f"package {pkg};\n{imports}\npublic final class {name} {{}}\n",
            encoding="utf-8",
        )
        return path

    def test_domain_must_not_depend_on_android_or_data(self):
        self.create(
            "domain", "RunPolicy",
            "import android.location.Location;\n"
            "import it.example.passolibero.data.TrackerDb;",
        )
        violations = GUARD.violations(self.root)
        self.assertEqual(len(violations), 2)
        self.assertTrue(any("Android" in message for message in violations))
        self.assertTrue(any("must not import data" in message for message in violations))

    def test_application_does_not_import_android_or_presentation(self):
        self.create(
            "application", "FinishSession",
            "import android.content.Context;\n"
            "import it.example.passolibero.presentation.SessionScreen;",
        )
        self.assertEqual(len(GUARD.violations(self.root)), 2)

    def test_data_adapter_can_import_android_and_domain(self):
        self.create(
            "data", "SqliteSessions",
            "import android.database.sqlite.SQLiteDatabase;\n"
            "import it.example.passolibero.domain.Session;",
        )
        self.assertEqual(GUARD.violations(self.root), [])

    def test_presentation_does_not_depend_directly_on_data(self):
        self.create(
            "presentation", "SessionScreen",
            "import it.example.passolibero.data.SqliteSessions;",
        )
        self.assertEqual(len(GUARD.violations(self.root)), 1)

    def test_package_path_and_wildcards(self):
        self.create(
            "domain", "RunPolicy",
            "import java.util.*;",
            package="it.example.passolibero.wrong",
        )
        self.assertEqual(len(GUARD.violations(self.root)), 2)

    def test_legacy_root_package_is_exempt(self):
        self.create(
            "legacy", "ExistingService",
            "import android.app.Service;",
            package="it.example.passolibero",
        )
        self.assertEqual(GUARD.violations(self.root), [])


    def test_nested_packages_and_debug_source_set_are_checked(self):
        self.create(
            "application", "LoadSession",
            "import it.example.passolibero.platform.location.GpsAdapter;",
            source_set="debug", subpackage="tracking.recovery",
        )
        found = GUARD.violations(self.root)
        self.assertEqual(len(found), 1)
        self.assertIn("must not import platform", found[0])

    def test_fully_qualified_android_and_data_references_without_imports(self):
        path = self.create("domain", "Calculation")
        path.write_text(
            "package it.example.passolibero.domain;\n"
            "final class Calculation {\n"
            "  android.location.Location gps;\n"
            "  it.example.passolibero.data.local.SqliteSessions db;\n"
            "}\n",
            encoding="utf-8",
        )
        found = GUARD.violations(self.root)
        self.assertEqual(len(found), 2)
        self.assertTrue(any("reference Android" in msg for msg in found))
        self.assertTrue(any("reference data" in msg for msg in found))

    def test_fully_qualified_static_call_cannot_bypass_import_check(self):
        path = self.create("presentation", "SessionScreen")
        path.write_text(
            "package it.example.passolibero.presentation;\n"
            "class SessionScreen {\n"
            "  int x = it.example.passolibero.data.local.SqliteSessions.count();\n"
            "}\n",
            encoding="utf-8",
        )
        found = GUARD.violations(self.root)
        self.assertEqual(len(found), 1)
        self.assertIn("must not reference data", found[0])

    def test_import_is_not_reported_again_as_fully_qualified_reference(self):
        self.create(
            "domain", "Calculation",
            "import it.example.passolibero.data.local.SqliteSessions;",
        )
        found = GUARD.violations(self.root)
        self.assertEqual(len(found), 1)
        self.assertIn("must not import data", found[0])

    def test_comments_strings_chars_and_text_blocks_are_not_dependencies(self):
        path = self.create("domain", "Calculation")
        path.write_text(
            'package it.example.passolibero.domain;\n'
            '// android.location.Location in a comment\n'
            '/* it.example.passolibero.platform.GpsAdapter block */\n'
            'final class Calculation {\n'
            '  String s = "it.example.passolibero.data.SqliteSessions";\n'
            '  String quote = "escaped \\"android.location.Location\\"";\n'
            '  char mark = \'a\';\n'
            '  String block = """\n'
            '    it.example.passolibero.presentation.Home\n'
            '  """;\n'
            '}\n',
            encoding="utf-8",
        )
        self.assertEqual(GUARD.violations(self.root), [])

    def test_legal_fully_qualified_domain_use_from_application(self):
        path = self.create("application", "FinishSession")
        path.write_text(
            "package it.example.passolibero.application;\n"
            "final class FinishSession {\n"
            "  Object x = it.example.passolibero.domain.SessionRecoveryPolicy.class;\n"
            "}\n",
            encoding="utf-8",
        )
        self.assertEqual(GUARD.violations(self.root), [])

    def test_legacy_root_remains_exempt_even_with_qualified_platform_references(self):
        path = self.create("legacy", "ExistingActivity",
            package="it.example.passolibero")
        path.write_text(
            "package it.example.passolibero;\n"
            "class ExistingActivity {\n"
            "  it.example.passolibero.data.local.SqliteSessions db;\n"
            "  android.content.Context context;\n"
            "}\n",
            encoding="utf-8",
        )
        self.assertEqual(GUARD.violations(self.root), [])



BATTERY_GUARD_SCRIPT = Path(__file__).with_name("check-battery-debug-boundary.py")
BATTERY_SPEC = importlib.util.spec_from_file_location(
    "passolibero_battery_boundary", BATTERY_GUARD_SCRIPT
)
BATTERY_GUARD = importlib.util.module_from_spec(BATTERY_SPEC)
BATTERY_SPEC.loader.exec_module(BATTERY_GUARD)


class BatteryDebugSourceBoundaryTest(unittest.TestCase):
    """Regression fixtures for P9a's debug-only source-set boundary."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.app = Path(self.temp.name) / "app"
        self.debug = self.app / "src/debug/java/it/example/passolibero"
        self.debug.mkdir(parents=True)
        for name in ("BatteryRecorder", "BatteryAutoCapture", "BatteryDiagnosticsActivity"):
            (self.debug / (name + ".java")).write_text(
                "package it.example.passolibero;\n"
                + "final class " + name + " {}\n",
                encoding="utf-8",
            )
        self.debug_manifest = self.app / "src/debug/AndroidManifest.xml"
        self.debug_manifest.write_text(
            '<manifest><application><activity '
            'android:name=".BatteryDiagnosticsActivity"/></application></manifest>\n',
            encoding="utf-8",
        )

    def test_expected_debug_only_files_are_accepted(self):
        main = self.app / "src/main/java/it/example/passolibero"
        main.mkdir(parents=True)
        (main / "TrackingService.java").write_text(
            'class TrackingService { String target = "BatteryAutoCapture"; }\n',
            encoding="utf-8",
        )
        self.assertEqual([], BATTERY_GUARD.violations(self.app))

    def test_missing_debug_recorder_is_reported(self):
        (self.debug / "BatteryRecorder.java").unlink()
        errors = BATTERY_GUARD.violations(self.app)
        self.assertTrue(any("BatteryRecorder" in error for error in errors))

    def test_duplicate_recorder_declaration_in_main_is_rejected(self):
        main = self.app / "src/main/java/it/example/passolibero"
        main.mkdir(parents=True)
        (main / "NotTheRecorderFilename.java").write_text(
            "package it.example.passolibero;\nclass BatteryRecorder {}\n",
            encoding="utf-8",
        )
        errors = BATTERY_GUARD.violations(self.app)
        self.assertTrue(any("main" in error and "BatteryRecorder" in error for error in errors))

    def test_release_source_cannot_contain_auto_capture(self):
        release = self.app / "src/release/java/it/example/passolibero"
        release.mkdir(parents=True)
        (release / "BatteryAutoCapture.java").write_text(
            "package it.example.passolibero;\nclass BatteryAutoCapture {}\n",
            encoding="utf-8",
        )
        errors = BATTERY_GUARD.violations(self.app)
        self.assertTrue(any("release" in error and "BatteryAutoCapture" in error for error in errors))

    def test_release_manifest_cannot_register_debug_diagnostics_activity(self):
        release_manifest = self.app / "src/release/AndroidManifest.xml"
        release_manifest.parent.mkdir(parents=True)
        release_manifest.write_text(
            '<manifest><application><activity '
            'android:name=".BatteryDiagnosticsActivity"/></application></manifest>\n',
            encoding="utf-8",
        )
        errors = BATTERY_GUARD.violations(self.app)
        self.assertTrue(any("release" in error and "BatteryDiagnosticsActivity" in error
                            for error in errors))

    def test_debug_manifest_must_register_diagnostics_activity(self):
        self.debug_manifest.write_text(
            "<manifest><application/></manifest>\n", encoding="utf-8"
        )
        errors = BATTERY_GUARD.violations(self.app)
        self.assertTrue(any("debug" in error and "BatteryDiagnosticsActivity" in error
                            for error in errors))


if __name__ == "__main__":
    unittest.main()
