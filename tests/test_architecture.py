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


if __name__ == "__main__":
    unittest.main()
