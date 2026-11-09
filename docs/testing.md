# Verifica locale e CI

## Requisiti
JDK 17, Android SDK con platforms;android-35 e build-tools;35.0.0, Python 3 e accesso ai repository Gradle/Maven almeno per la prima compilazione. Non sono richiesti token, credenziali di firma o servizi cloud per verificare una PR.

## Android 11 — requisito obbligatorio
La versione minima supportata è Android 11 (API 30). Il job CI `android11` avvia un emulatore API 30, installa l'APK ed esegue smoke test delle schermate Home e Storico. Il job `verify` controlla anche la proprietà minSdk dell'APK. Per riprodurre localmente, avvia un emulatore Android 11 e lancia `sh ./gradlew :app:connectedDebugAndroidTest`. Per modifiche a GPS, permessi, passi e foreground service è obbligatoria anche una verifica manuale su telefono fisico Android 11: un emulatore non riproduce tutti i sensori e il comportamento a schermo spento.

## Verifiche di qualità e architettura
```sh
python3 tests/check-architecture.py
python3 -m unittest discover -s tests -p 'test_architecture.py'
sh ./gradlew :app:spotlessCheck
```
Il guardiano degli import applica la matrice di `docs/clean-architecture.md` ai nuovi package ma non ai file legacy nel package radice. Spotless formatta i nuovi package secondo AOSP e controlla gli script Gradle; non riscrive il codice storico. Per correggere la formattazione: `sh ./gradlew :app:spotlessApply`. Il repository deve essere completo di `origin/main` (`git fetch origin main`; la CI usa `fetch-depth: 0`).

## Verifica veloce senza Android SDK
```sh
sh tests/run-core-tests.sh
python3 tests/check-db-migration.py
```

## Android
Il Gradle Wrapper 8.9 è versionato: per Linux/macOS `sh ./gradlew`, oppure `./gradlew` se eseguibile; Windows `gradlew.bat`.
```sh
sh ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```
Per gli scenari che usano emulatori e sensori, effettuare test strumentali su un dispositivo/API compatibile (i test unitari JVM non avviano Android).

## Tabella verifiche per PR
| Tipo di cambiamento | Minimo |
| --- | --- |
| Solo documentazione | review contenuto e controlli CI |
| Algoritmi | test JVM per happy path + ingressi nulli, stale/out-of-order, transizioni |
| Database | test migrazione da tutte le versioni precedenti + conservazione righe |
| UI | verifiche build/lint, screenshot o video senza dati reali, test navigazione se fattibile |
| Android lifecycle / GPS | test automatici + checklist manuale su telefono reale |
| Rete, permessi, update, signing | revisione sicurezza/privacy e test di errore |

## Test manuali su telefono (da riportare nella PR)
- Prima installazione e aggiornamento sopra una versione con sessioni salvate (backup indipendente prima di prove rischiose).
- Permesso posizione negato/concesso e sensore passi assente.
- Fermo con telefono manipolato, camminata lenta, corsa → stop → camminata.
- GPS perso e poi recuperato, schermo spento, ritorno all'app.
- Modalità offline: tracking e storico restano disponibili; mappe non in cache possono essere vuote.
- Distanza su un percorso misurato, nessun segmento GPS fittizio, durata e totali coerenti.

## Interpretare l'esito
Un APK compilato non prova il corretto funzionamento su hardware. Una PR dichiara sempre i comandi effettivamente eseguiti, i loro risultati e le verifiche non fatte; un fallimento non va nascosto.
