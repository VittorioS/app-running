"""Verify SQLite schema v6 and conservative migrations from each historical version."""
import re
import sqlite3
from pathlib import Path

source = Path("app/src/main/java/it/example/passolibero/TrackerDb.java").read_text()
create_body = source.split("@Override public void onCreate", 1)[1].split("@Override public void onUpgrade", 1)[0]
statements = ["".join(re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', text))
              for text in re.findall(r"db\.execSQL\((.*?)\);", create_body, re.S)]
migrations = [(int(version), sql) for version, sql in re.findall(
    r'if \(oldV < (\d+)\) db\.execSQL\("([^"]+)"\)', source)]
assert [v for v, _ in migrations] == [2, 3, 4]
v5_block = re.search(r'if \(oldV < 5\) \{(.*?)\}', source, re.S)
assert v5_block, "Missing conservative v5 upgrade"
v5_sql = re.findall(r'db\.execSQL\("([^"]+)"\)', v5_block.group(1))
assert len(v5_sql) == 2 and "run_steps" in v5_sql[0] and "walk_steps" in v5_sql[1]
v6_sql = re.findall(r'if \(oldV < 6\) db\.execSQL\("([^"]+)"\)', source)
assert len(v6_sql) == 1 and "run_max_speed_kmh" in v6_sql[0]

with sqlite3.connect(":memory:") as db:
    for sql in statements:
        db.execute(sql)
    columns = {r[1]: r for r in db.execute("PRAGMA table_info(sessions)")}
    assert "run_steps" in columns and "walk_steps" in columns
    assert "run_max_speed_kmh" in columns and columns["run_max_speed_kmh"][3] == 0
    assert columns["run_steps"][3] == 0 and columns["walk_steps"][3] == 0
    db.execute("INSERT INTO sessions (started_at, run_steps, walk_steps) VALUES (100, 0, 0)")
    assert db.execute("SELECT run_steps, walk_steps, run_max_speed_kmh FROM sessions").fetchone() == (0, 0, None)

for old_version in (1, 2, 3, 4, 5):
    db = sqlite3.connect(":memory:")
    old_schema = statements[0].replace("run_max_speed_kmh REAL, ", "")
    if old_version < 5:
        old_schema = old_schema.replace("run_steps INTEGER, walk_steps INTEGER, ", "")
    if old_version < 4:
        old_schema = old_schema.replace("moving_unknown_ms INTEGER NOT NULL DEFAULT 0, ", "")
    if old_version < 3:
        old_schema = old_schema.replace("unknown_ms INTEGER NOT NULL DEFAULT 0, ", "")
    if old_version < 2:
        old_schema = old_schema.replace("goal_m INTEGER NOT NULL DEFAULT 0, ", "")
    db.execute(old_schema)
    for sql in statements[1:]:
        db.execute(sql)
    db.execute("INSERT INTO sessions (started_at, distance_m, run_ms, walk_ms, still_ms, moving_ms) "
               "VALUES (123, 321.5, 5000, 2000, 1000, 7000)")
    db.execute("INSERT INTO points (session_id, t_wall, lat, lon, accuracy_m, speed_kmh, delta_m, movement) "
               "VALUES (1, 456, 40, 14, 5, 8, 3, 'RUN')")
    for target_version, sql in migrations:
        if old_version < target_version:
            db.execute(sql)
    if old_version < 5:
        for sql in v5_sql:
            db.execute(sql)
    for sql in v6_sql:
        db.execute(sql)
    row = db.execute("SELECT started_at, distance_m, run_ms, walk_ms, still_ms, moving_ms, "
                     "moving_unknown_ms, run_max_speed_kmh FROM sessions").fetchone()
    assert row == (123, 321.5, 5000, 2000, 1000, 7000, 0, None), (old_version, row)
    assert db.execute("SELECT t_wall, movement FROM points").fetchone() == (456, "RUN")
    db.execute("UPDATE sessions SET moving_unknown_ms=3000, moving_ms=10000")
    assert db.execute("SELECT moving_ms, moving_unknown_ms FROM sessions").fetchone() == (10000, 3000)
    db.execute("UPDATE sessions SET run_steps=120, walk_steps=40, run_max_speed_kmh=15.4")
    assert db.execute("SELECT run_steps, walk_steps, run_max_speed_kmh FROM sessions").fetchone() == (120, 40, 15.4)
    db.close()
print("PASS: schema v6 and migrations 1/2/3/4/5 -> 6 preserve sessions, GPS, and missing historical max speed.")
