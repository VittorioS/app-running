"""Verify SQLite schema v7 and conservative migrations from each historical version."""
import re
import sqlite3
from pathlib import Path

source = Path("app/src/main/java/it/example/passolibero/TrackerDb.java").read_text()
create_body = source.split("@Override public void onCreate", 1)[1].split("@Override public void onUpgrade", 1)[0]
statements = ["".join(re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', text))
              for text in re.findall(r"db\.execSQL\((.*?)\);", create_body, re.S)]
migrations = [(int(version), sql) for version, sql in re.findall(
    r'if \(oldV < (\d+)\) db\.execSQL\("([^"]+)"\)', source)
              if int(version) < 5]
assert [v for v, _ in migrations] == [2, 3, 4]
v5_block = re.search(r'if \(oldV < 5\) \{(.*?)\}', source, re.S)
assert v5_block, "Missing conservative v5 upgrade"
v5_sql = re.findall(r'db\.execSQL\("([^"]+)"\)', v5_block.group(1))
assert len(v5_sql) == 2 and "run_steps" in v5_sql[0] and "walk_steps" in v5_sql[1]
v6_sql = re.findall(r'if \(oldV < 6\) db\.execSQL\("([^"]+)"\)', source)
assert len(v6_sql) == 1 and "run_max_speed_kmh" in v6_sql[0]
v7_block = re.search(r"if \(oldV < 7\) \{(.*?)\}", source, re.S)
assert v7_block and "checkpoint_interval_m" in v7_block.group(1)
assert "createSprintTable(db);" in v7_block.group(1)
assert "CREATE TABLE IF NOT EXISTS sprints" in source
sprint_body = source.split("private static void createSprintTable", 1)[1].split(
    "public long newSession", 1)[0]
sprint_statements = ["".join(re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', part))
                     for part in re.findall(r"db\.execSQL\((.*?)\);", sprint_body, re.S)]
assert len(sprint_statements) == 2
assert 'database.delete("sprints", "session_id=?", args)' in source

with sqlite3.connect(":memory:") as db:
    for sql in statements + sprint_statements:
        db.execute(sql)
    columns = {r[1]: r for r in db.execute("PRAGMA table_info(sessions)")}
    assert "run_steps" in columns and "walk_steps" in columns
    assert "run_max_speed_kmh" in columns and columns["run_max_speed_kmh"][3] == 0
    assert "checkpoint_interval_m" in columns and columns["checkpoint_interval_m"][3] == 0
    assert columns["run_steps"][3] == 0 and columns["walk_steps"][3] == 0
    db.execute("INSERT INTO sessions (started_at, run_steps, walk_steps) VALUES (100, 0, 0)")
    assert db.execute("SELECT run_steps, walk_steps, run_max_speed_kmh, checkpoint_interval_m FROM sessions").fetchone() == (0, 0, None, None)
    assert "sprints" in {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}

for old_version in (1, 2, 3, 4, 5, 6):
    db = sqlite3.connect(":memory:")
    old_schema = statements[0].replace("checkpoint_interval_m INTEGER, ", "")
    if old_version < 6:
        old_schema = old_schema.replace("run_max_speed_kmh REAL, ", "")
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
    if old_version < 6:
        for sql in v6_sql:
            db.execute(sql)
    db.execute("ALTER TABLE sessions ADD COLUMN checkpoint_interval_m INTEGER")
    for sql in sprint_statements:
        db.execute(sql)
    row = db.execute("SELECT started_at, distance_m, run_ms, walk_ms, still_ms, moving_ms, "
                     "moving_unknown_ms, run_max_speed_kmh, checkpoint_interval_m FROM sessions").fetchone()
    assert row == (123, 321.5, 5000, 2000, 1000, 7000, 0, None, None), (old_version, row)
    assert db.execute("SELECT t_wall, movement FROM points").fetchone() == (456, "RUN")
    db.execute("UPDATE sessions SET moving_unknown_ms=3000, moving_ms=10000")
    assert db.execute("SELECT moving_ms, moving_unknown_ms FROM sessions").fetchone() == (10000, 3000)
    db.execute("UPDATE sessions SET run_steps=120, walk_steps=40, run_max_speed_kmh=15.4, checkpoint_interval_m=250")
    assert db.execute("SELECT run_steps, walk_steps, run_max_speed_kmh, checkpoint_interval_m FROM sessions").fetchone() == (120, 40, 15.4, 250)
    db.execute("INSERT INTO sprints (session_id, recorded_at, mode, target, countdown_s, elapsed_ms, distance_m, samples, outcome) VALUES (1, 1000, 'TIME', 10, 5, 10000, 35, 12, 'TEMPO')")
    assert db.execute("SELECT target, distance_m FROM sprints WHERE session_id=1").fetchone() == (10, 35)
    db.close()
print("PASS: schema v7 and migrations 1/2/3/4/5/6 -> 7 preserve sessions, GPS, checkpoint and sprint history.")
