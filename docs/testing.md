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

## ADR-002 / Issue #20 — Countdown vocale sprint

La policy `domain/sprint/SprintCountdownVoicePlan` mantiene l'orologio monotono, i countdown fra 5 e 10 secondi, le frasi originali `10`–`1` e `Via, sprint` e il limite di ritardo di 650 ms. La classe legacy rimane una facciata della stessa API usata da `TrackingService`; `VoiceCoach` e `TrackingService` non vengono modificati.

`sh tests/run-core-tests.sh` esegue il test `SprintCountdownVoicePlanTest` e `SprintCountdownCharacterizationTest`: quest'ultimo confronta il comportamento precedente con facciata e Domain per casi di invalidità, limiti di overflow, scadenze e 3.000 tracciati seedati. Validazione iniziale: [CI #42788887534](https://github.com/VittorioS/app-running/actions/runs/42788887534), `verify` e `android11` verdi. Non serve collaudo su sensore reale per la sola estrazione deterministica.

## ADR-002 / Issue #21 — Snapshot e adapter preferenze

Il test di caratterizzazione `RunStatePrefsCharacterizationTest` è stato introdotto **prima** della migrazione. Dopo un iniziale errore di aspettativa fra default di lettura (`checkpoint_interval` mancante → 1000) e valore scritto esplicitamente da un nuovo `RunState` (0), la [CI baseline #42876149805](https://github.com/VittorioS/app-running/actions/runs/42876149805) ha superato sia `verify` sia `android11`. Questo distingue il formato esistente da comportamenti immaginati.

La facciata legacy continua a leggere/salvare le stesse chiavi. `domain/session/RunSnapshot` e `AdaptiveProfileSnapshot` conservano i valori senza normalizzazioni; `data/local/PreferencesSnapshotMapper` mantiene ordine dei `put`, `apply()` e la lettura di `System.currentTimeMillis()` nello stesso punto del salvataggio. I test Android API 30 su preferenze **isolate** verificano default/assenza chiavi, tipi, round-trip di ogni campo persistito, `NaN`, infinito, `-0.0`, marker di sessione, prefissi non correlati, preesistente snapshot parziale, errori di tipo e adattamento `AppPrefs`. `TrackingPersistenceInstrumentedTest` continua a coprire il ripristino di sessione e SQLite storico nel sandbox.

**Limiti:** il riavvio del processo e il reboot fisico, il ripristino di una vera installazione utente e un aggiornamento in-place su telefono reale richiedono ancora collaudo manuale con backup prima del merge o della distribuzione. `verify` e `android11` finali della PR rimangono obbligatori. Nessun test accede alle preferenze reali dell'utente.

## ADR-002 / Issue #22 — Caratterizzazione e reader SQLite

`TrackerDbReadCharacterizationTest` usa `sessions.db` con nome univoco nel sandbox di test: sessioni complete e con colonne `NULL`, ordinamento `recent` per `started_at DESC`, recupero per ID, punti per `id ASC` con paginazione e limiti 1–2000, sprint con media storica ricalcolata, matching `SPRINT%` con finestre temporali inclusive. Il port `SessionReadPort` e l'adapter `SqliteSessionReadAdapter` sono testati direttamente contro la facade legacy. `TrackingPersistenceInstrumentedTest` resta l'oracolo per l'upgrade reale di DB v1→v7 e conservazione delle righe.

La caratterizzazione è stata acquisita **prima** dell'estrazione: [CI baseline #42909192999](https://github.com/VittorioS/app-running/actions/runs/42909192999), `verify` e `android11` success. L'estrazione non cambia schema, query, filtri, transazioni, writer, cancellazioni o UI. Gli esiti finali della CI sulla PR vanno registrati separatamente; i test su telefono reale/aggiornamento in-place con backup non sono ancora eseguiti.


## ADR-002 / Issue #23 — Caratterizzazione prima del primo writer SQLite

`TrackerDbSessionInsertCharacterizationTest` è stato introdotto **prima** dell'estrazione: test strumentali reali su database v7 isolati, senza accesso a sessioni utente, che verificano ID, campi default/`NULL`, `started_at` 0/negativo, comportamento `AUTOINCREMENT` su storico preesistente, partecipazione a transazioni del chiamante e rollback, eccezione da `insertOrThrow`, nessuna scrittura implicita in punti/sprint. La [CI baseline #42912444912](https://github.com/VittorioS/app-running/actions/runs/42912444912) ha passato `verify` e `android11` **prima** del codice di produzione. Dopo l'estrazione viene confrontato il port con l'espressione SQL originale e con la facciata legacy sul medesimo database di test.

L'estrazione riguarda **solo** `newSession`: schema SQLite v7, query di lettura, snapshot periodici, punti GPS, sprint, transazioni e cancellazioni non cambiano. La validazione finale CI e il collaudo con upgrade sopra un'installazione reale/backup restano passi distinti, non sostituiti dalla caratterizzazione API30.


## ADR-002 / Issue #23 — Secondo writer P3c: caratterizzazione punti

`TrackerDbPointInsertCharacterizationTest` è stato introdotto **prima** dell'estrazione in una PR draft separata e ha superato [CI baseline #42916190123](https://github.com/VittorioS/app-running/actions/runs/42916190123) (`verify` e `android11` success). Verifica su SQLite v7 isolato gli otto valori persistiti, gli ID con tempi uguali, gli overload enum/stringa, le etichette sprint, l'assenza di scritture collaterali, il rollback in transazione esterna, la sessione assente, il vincolo `movement NOT NULL` e gli errori propagati. Il test post-estrazione confronta riga per riga una chiamata SQL originale, il port diretto e la facciata legacy. I risultati della CI **post-estrazione** devono essere registrati separatamente; non è un test di aggiornamento in-place su telefono reale con dati storici.


## ADR-002 / Issue #23 — Terzo writer: baseline e confronto snapshot

`TrackerDbSnapshotCharacterizationTest` è stato acquisito **prima** di modificare `TrackerDb.snapshot`. La [CI baseline #42919287431](https://github.com/VittorioS/app-running/actions/runs/42919287431) ha superato entrambi `verify` e `android11` sul commit di soli test. Casi su SQLite v7 isolato: campi/contatori, aggiornamenti ripetuti, orologio di `ended_at`, mantenimento di `COMPLETED` per snapshot successivo non finito, `NULL` per velocità non valida/checkpoint non positivo, riga storica con `NULL`, transazione del chiamante/rollback, ID assente ed eccezioni del database. Il test aggiunto dopo l'estrazione confronta ogni colonna (tipo e valore SQLite) di update SQL originale, port e facade su tre sessioni equivalenti. La CI finale G2 deve essere verificata separatamente. Nessun test su telefono fisico o upgrade in-place su dati utente è stato dichiarato eseguito.


## ADR-002 / Issue #23 — Quarto writer: caratterizzazione e parità sprint

`TrackerDbSprintInsertCharacterizationTest` è stato introdotto **prima** dell'estrazione del writer: [CI G1 #42922590503](https://github.com/VittorioS/app-running/actions/runs/42922590503) con `verify` e `android11` entrambi verdi. Test su SQLite v7 isolato: FINISHED/TEMPO con campioni validi e valore medio, CANCELLED e `NULL` per peak/average senza velocità, stati non terminali, ID invalido, inserimenti duplicati, transazioni del chiamante con rollback, sessione mancante e errori SQLite. Dopo il refactoring, il test di parità confronta colonna per colonna l'SQL originale, il port diretto e la facade; il timestamp `recorded_at` è verificato separatamente per il corretto clock wall. Restano non eseguiti test su telefono reale, upgrade in-place con backup e storici utente.


## ADR-002 / Issue #24 — Test di ingresso lifecycle e gap device

`TrackingLifecycleEntryCharacterizationTest` è stato acquisito **prima** della modifica di `TrackingService`. La prima [CI G1 #43278546563](https://github.com/VittorioS/app-running/actions/runs/43278546563) aveva `verify` verde e un'asserzione test errata sul numero di letture monotone nel fallback: con `savedStart<=0` il controllo fa short-circuit e legge il clock una sola volta, con timestamp salvato futuro lo legge due volte. Nessuna modifica di produzione: corretto il test e aggiunta la seconda diramazione in `3ff827e8`. La [CI G1 corretta #43279313079](https://github.com/VittorioS/app-running/actions/runs/43279313079) ha superato `verify` e `android11` **prima** dell'estrazione.

Dopo l'estrazione, `SessionEntryCoordinatorTest` usa fake deterministici e il test Android confronta la nuova decisione con il predicato originale su preferenze isolate (ID zero/negativi/positivi, marker inattivo, errore typed). Questi test **non eseguono** il worker reale, `onStartCommand`, FGS, registrazioni sensori, permessi o process death. Restano obbligatori collaudi reali Android 11/telefono: START esplicito, STOP durante init, riavvio processo, schermo bloccato, GPS negato/spento, durata e recupero, oltre ai test su sessioni utente con backup prima di release.


## ADR-002 / Issue #24 — Baseline e parità dei comandi di servizio

`TrackingCommandDispatchCharacterizationTest` è stato aggiunto **prima** della classificazione di produzione: [CI G1 #43291994935](https://github.com/VittorioS/app-running/actions/runs/43291994935) `verify` e `android11` success. I test definiscono una traccia di riferimento degli effetti immediati di `onStartCommand`: STOP non sticky e prioritario, etichette/sprint sticky prima del gate, START/Intent nullo/azione sconosciuta al gate, errore permessi e fallimento acquisizione FGS senza inizializzazione, ordine del foreground prima dell'enqueue. Dopo l'estrazione il test verifica la parità di selezione con il classificatore e test JVM verificano matching esatto, valori nulli e maiuscole. **La traccia è simulata**, non esercita `onStartCommand` o l'FGS reale: test di servizio su API30 e collaudo fisico restano gate separati.

## ADR-002 / Issue #24 — Protocollo G3 fisico prima di P4b

Per la verifica del lifecycle reale START/STOP/recovery di `TrackingService` su **telefono fisico Android 11/API 30**, usare il [protocollo G3 e relativo modello di report](refactoring/g3-lifecycle-android11.md). La matrice distingue test automatizzati e prove manuali, prescrive dati sintetici/backup e richiede esiti `PASS`, `FAIL` o `NON ESEGUITO` per ogni scenario. Un documento o una CI verde **non** costituiscono evidenza di G3 superato; la #24 e il prerequisito per P4b/#25 restano soggetti a collaudo e sign-off umano.
