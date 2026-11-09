"""Lo storico conserva la stessa impostazione visuale della sessione live."""
from pathlib import Path

history = Path("app/src/main/java/it/example/passolibero/SessionDetailActivity.java").read_text()
live = Path("app/src/main/java/it/example/passolibero/ActiveSessionActivity.java").read_text()
database = Path("app/src/main/java/it/example/passolibero/TrackerDb.java").read_text()
service = Path("app/src/main/java/it/example/passolibero/TrackingService.java").read_text()
json = Path("app/src/main/java/it/example/passolibero/SessionJsonExporter.java").read_text()

for label in ("RITMO MEDIO (min/km)", "VELOCITÀ MEDIA",
              "VELOCITÀ MASSIMA CORSA", "RITMO ATTUALE (min/km)",
              "VELOCITÀ ATTUALE", "PASSI ATTRIBUITI", "CADENZA MEDIA"):
    assert label in live and label in history, label
assert "sprintCard" in live and "sprintCard" in history
assert '"🏃 Corsa", "🚶 Cammina", "∑ Totali", "⚡ Sprint"' in history
assert '"🏃 Corsa", "🚶 Cammina", "∑ Totali", "⚡ Sprint"' in live
assert 'statsCard.setVisibility(sprint ? View.GONE : View.VISIBLE)' in history
assert 'statsCard.setVisibility(sprintTab ? View.GONE : View.VISIBLE)' in live
assert "SessionTabStats.meters(tab, session.runM, session.walkM)" in history
assert "SessionTabStats.milliseconds(tab, session.runMs, session.walkMs)" in history
assert "SessionTabStats.averageCadence(milliseconds, tabSteps)" in history
assert "session.maxRunSpeedKmh" in history
assert 'currentSpeed.setText("-- km/h")' in history
assert "map.update(db, sessionId)" in history
assert "map.zoomToRoute()" in history
assert "checkpointIntervalM" in history
assert "db.sprints(sessionId)" in history
assert "db.sprints(sessionId)" in live
assert "SprintTrial.averageSpeedKmh(distance, elapsed, samples)" in live
assert 'writer.name("sprints")' in json
assert "db.recordSprint(state.id, sprint, now)" in service
assert "sprintRecorded = true;" in service
assert "CREATE TABLE IF NOT EXISTS sprints" in database
assert 'database.delete("sprints", "session_id=?", args)' in database
assert "TrackingService.SPRINT_START" not in history
print("PASS: tab, metriche, percorso, sprint e sola lettura dello storico.")
