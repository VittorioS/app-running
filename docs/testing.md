# Verifica locale e CI

## Requisiti
JDK 17, Android SDK con platforms;android-35 e build-tools;35.0.0, Python 3 e accesso ai repository Gradle/Maven almeno per la prima compilazione. Non sono richiesti token, credenziali di firma o servizi cloud per verificare una PR.

## Firma e release APK
Per le prove di metadata, versionCode e sicurezza del workflow eseguire `python3 -m unittest discover -s tests -p 'test_release_metadata.py'`. Per una release pre-produzione firmata su `main` seguire `docs/release.md`; non si eseguono release con credenziali dalle PR.

## Issue #5 — test lifecycle, sensori e persistenza
La CI esegue il replay deterministico GPS/step counter in `tests/TrackingReplayTest.java` tramite `sh tests/run-core-tests.sh`. I test coprono fix fuori ordine, GPS assente, ripresa, contatore ritardato/assente, conservazione dei tempi e jitter.

Sul dispositivo emulato Android 11, `TrackingUiInstrumentedTest` verifica Home, sessione, storico, ricreazione Activity e contratto permessi **senza concedere/revocare permessi né avviare il tracking**. `TrackingPersistenceInstrumentedTest` verifica `RunState`, persistenza SQLite/GPS e upgrade reale del database v1→v7 usando un contesto di prova con database e preferenze isolati. Il test non deve accedere a `sessions.db` reale del dispositivo.

Per il collaudo manuale di schermo spento, foreground service, sensor fallback e conservazione storico dopo l'aggiornamento usare [la matrice dispositivo reale](issue-5-device-matrix.md). L'emulatore **non** riproduce fedelmente step detector, ottimizzazione batteria e GPS all'aperto.

## Android 11 — requisito obbligatorio
La versione minima supportata è Android 11 (API 30). Il job CI `android11` avvia un emulatore API 30, installa l'APK ed esegue smoke test delle schermate Home e Storico. Il job `verify` controlla anche la proprietà minSdk dell'APK. Per riprodurre localmente, avvia un emulatore Android 11 e lancia `sh ./gradlew :app:connectedDebugAndroidTest`. Per modifiche a GPS, permessi, passi e foreground service è obbligatoria anche una verifica manuale su telefono fisico Android 11: un emulatore non riproduce tutti i sensori e il comportamento a schermo spento.

## Verifiche di qualità e architettura
```sh
python3 tests/check-architecture.py
python3 -m unittest discover -s tests -p 'test_architecture.py'
python3 -m unittest discover -s tests -p 'test_release_metadata.py'
sh ./gradlew :app:spotlessCheck
```
Il guardiano degli import applica la matrice di `docs/clean-architecture.md` ai nuovi package ma non ai file legacy nel package radice. Spotless formatta i nuovi package secondo AOSP e controlla gli script Gradle; non riscrive il codice storico. Per correggere la formattazione: `sh ./gradlew :app:spotlessApply`. Il repository deve essere completo di `origin/main` (`git fetch origin main`; la CI usa `fetch-depth: 0`).

## Issue #17 / ADR-002 — Prima suite migrata a Gradle JUnit

`WeatherRulesTest` si trova ora in `app/src/test/java/it/example/passolibero/` ed è eseguito da `sh ./gradlew :app:testDebugUnitTest` (job `verify`). La migrazione riproduce **20 asserzioni** della vecchia `tests/WeatherRulesTest.java`: 17 verifiche booleane e 3 attese di `IllegalArgumentException`, con gli stessi input e valori limite. Prima della rimozione del runner duplicato, la [CI di confronto](https://github.com/VittorioS/app-running/actions/runs/42663767918) ha superato sia la suite Java originale in `tests/run-core-tests.sh` sia il nuovo test Gradle/JUnit, oltre ad Android 11.

`tests/run-core-tests.sh` continua a eseguire **tutte le altre** suite indipendenti da Android; per il controllo completo eseguire **entrambi** `sh tests/run-core-tests.sh` e `sh ./gradlew :app:testDebugUnitTest`. La logica `WeatherRules`, la UI e il servizio meteo non sono stati modificati. Revert della singola PR ripristina il runner originale senza modifiche ai dati.

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

## Issue #6 — invarianti di ripresa del service

`tests/SessionRecoveryPolicyTest.java` (eseguito in `tests/run-core-tests.sh`) confronta il calcolo puro di recupero con la **formula legacy** per 1000 tracce deterministiche. Verifica anche ordine/numero delle letture clock, fallback dopo reboot, intervalli non osservati classificati esclusivamente come `UNKNOWN` e assenza di doppio conteggio. I test GPS stale/out-of-order sono in `TrackingReplayTest.java`; lo smoke Android API 30 controlla anche manifest e foreground lifecycle nelle PR successive del piano #6.

## Issue #6 — foreground Android e fix GNSS

`tests/ForegroundStartPolicyTest.java` testa permessi/GPS con corto circuito e tipo HEALTH soltanto su API 34+; `Android11CompatibilityTest` verifica su emulatore API 30 che non si leggano permessi HEALTH. `tests/GpsFixPolicyTest.java` confronta le espressioni precedenti di `TrackingService` con la policy pura su tracce fisse e 1000 campioni deterministici (sprint normale 1 Hz, fix fuori ordine, lentezza incerta, jitter, STILL). I test automatici non utilizzano GPS hardware.

## ADR-002 / Issue #19 — Policy massima velocità corsa

`domain/tracking/RunPeakSpeedPolicy` è la regola Java pura; la facciata legacy continua a offrire `RunPeakSpeedPolicy.update` a `TrackingService`. Unità km/h, stessa gestione di `NaN`, limiti GPS e movimento. `tests/run-core-tests.sh` esegue il test originale `RunPeakSpeedPolicyTest` e la caratterizzazione indipendente `RunPeakSpeedCharacterizationTest`, che confronta formula originale, facciata e dominio **bit per bit** (boundary, 5.000 input seedati, sequenza di aggiornamenti). I job `verify` e `android11` restano obbligatori; nessuna modifica funzionale, a SQLite, permessi, GPS o firma.
