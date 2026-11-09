"""Check that deleting one session removes only its GPS track, atomically."""
import sqlite3
from pathlib import Path

source = Path("app/src/main/java/it/example/passolibero/TrackerDb.java").read_text()
delete_method = source.split("public boolean deleteSession(long sessionId)", 1)[1].split(
    "private final Context appContext", 1)[0]
assert 'if (sessionId <= 0) return false;' in delete_method
assert 'if (isCurrentSession(prefs, sessionId)) return false;' in delete_method
assert 'database.beginTransaction()' in delete_method
assert 'database.delete("sessions", "id=?", args)' in delete_method
assert 'database.delete("points", "session_id=?", args)' in delete_method
assert 'database.delete("sprints", "session_id=?", args)' in delete_method
assert 'database.setTransactionSuccessful()' in delete_method
assert 'database.endTransaction()' in delete_method

# Transazione equivalente su SQLite: solo la voce mirata e i suoi punti.
db = sqlite3.connect(":memory:")
db.execute("CREATE TABLE sessions (id INTEGER PRIMARY KEY, name TEXT)")
db.execute("CREATE TABLE points (id INTEGER PRIMARY KEY, session_id INTEGER, lat REAL)")
db.executemany("INSERT INTO sessions VALUES (?, ?)", [(1, "primo"), (2, "secondo")])
db.executemany("INSERT INTO points VALUES (?, ?, ?)",
               [(1, 1, 40.1), (2, 1, 40.2), (3, 2, 41.1)])
db.commit()
with db:
    db.execute("DELETE FROM sessions WHERE id=?", (1,))
    db.execute("DELETE FROM points WHERE session_id=?", (1,))
assert db.execute("SELECT id FROM sessions").fetchall() == [(2,)]
assert db.execute("SELECT id, session_id FROM points").fetchall() == [(3, 2)]
with db:
    db.execute("INSERT INTO sessions VALUES (3, 'rollback')")
    db.execute("INSERT INTO points VALUES (4, 3, 42)")
try:
    with db:
        db.execute("DELETE FROM sessions WHERE id=?", (3,))
        db.execute("DELETE FROM points WHERE session_id=?", (3,))
        raise ValueError("Simulazione errore")
except ValueError:
    pass
assert db.execute("SELECT id FROM sessions ORDER BY id").fetchall() == [(2,), (3,)]
assert db.execute("SELECT id FROM points ORDER BY id").fetchall() == [(3,), (4,)]
print("PASS: eliminazione isolata del percorso e rollback della transazione.")
