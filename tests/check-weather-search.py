"""Regression: search must not invoke methods introduced only in Android API 33."""
from pathlib import Path
api = Path("app/src/main/java/it/example/passolibero/WeatherApi.java").read_text()
screen = Path("app/src/main/java/it/example/passolibero/WeatherSettingsActivity.java").read_text()
assert 'URLEncoder.encode(query.trim(), "UTF-8")' in api
assert 'buffer.toString("UTF-8")' in api
assert 'URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)' not in api
assert 'buffer.toString(StandardCharsets.UTF_8)' not in api
assert 'cityResults = Ui.vertical(this)' in screen
assert 'for (WeatherApi.City city : found)' in screen
assert 'selectedCity = city;' in screen
assert 'if (found.isEmpty())' in screen
assert 'new Spinner(' not in screen
print("PASS: ricerca città compatibile con Android API 30+ e selezione risultati esplicita")
