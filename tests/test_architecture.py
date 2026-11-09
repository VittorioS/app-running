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

    def create(self, layer, name, imports="", package=None):
        folder = self.root / "main/java/it/example/passolibero" / layer
        folder.mkdir(parents=True, exist_ok=True)
        pkg = package or "it.example.passolibero." + layer
        path = folder / (name + ".java")
        path.write_text(
            f"package {pkg};\n{imports}\npublic final class {name} {{}}\n",
            encoding="utf-8",
        )

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


if __name__ == "__main__":
    unittest.main()
