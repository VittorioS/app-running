# Backlog progettuale delle PR — ADR-002 (NON ancora issue operative)

**Stato:** proposta per la revisione del maintainer. Questo documento descrive *lotti candidati*, non autorizza agenti a implementare o pubblicare nulla. Le issue vengono create **solo dopo** che [ADR-002](../adr/ADR-002-refactoring-app.md) è stata accettata.

## Regole di pianificazione

- Ogni codice PR ha **una issue, un contratto osservabile e una responsabilità**. Se cambia dominio/semantica insieme all'adapter, dividere la PR.
- Nessun rename massivo o riformattazione globale; prima test di equivalenza, poi seam, infine spostamento del chiamante e pulizia.
- Le dipendenze tra lotti sono requisiti e non suggerimenti. Le PR successive non vanno mergiate finché le precedenti richieste sono verdi su `main`.
- Ogni PR riporta comandi/risultati effettivi, invarianti confermati, eccezioni legacy, rischio e piano di rollback.
- `verify` + `android11` sempre obbligatori; test fisici aggiuntivi nei casi GPS/FGS/batteria/permessi; pipeline release firmata solo manuale e separata.

## Fasi, lotti candidati e dipendenze

| Lotto PR candidata | Deliverable piccolo e concreto | Dipende da | Rischio / gate di uscita |
| --- | --- | --- | --- |
| **P0 — questa PR documentale** | ADR-002 proposta, mappa, backlog e link per gli agenti | nessuno | Solo documenti; **review/approvazione esplicita** |
| **P1a — test portabili** | Spostare *una* suite algoritmica dallo script `tests/run-core-tests.sh` a Gradle/JUnit senza perdere le asserzioni | ADR accettata | Parità test eseguiti, `verify/android11` verdi |
| **P1b — regole di dipendenza** | Rafforzare il guardiano sulle nuove sottodirectory senza bloccare il legacy; regression test del guardiano | P1a | Nessun falso positivo sui file esistenti |
| **P2a — model invariants** | Scegliere una famiglia di regole pure (statistiche/route/sprint), introdurre test contrattuali e migrare nel domain | P1a | JVM parity su precisione, unità, edge cases |
| **P2b — seconda famiglia domain** | Migrare una seconda famiglia pura mantenendo facade per i client legacy | P2a | test di equivalenza + nessun cambiamento UI |
| **P3a — snapshot port** | Definire view/snapshot Android-free e mapper di `RunState` in adapter prefs senza cambiare chiavi o timing | P2a | round-trip, stesso `apply()`, crash/restart |
| **P3b — session read port** | Isolare il **solo lettore** di `TrackerDb` consumato dallo storico, con adapter SQLite | P3a | DB v7 e dati storici leggibili, API 30 |
| **P3c — session write port** | Migrare scritture snapshot, punti e sprint a un contratto piccolo; nessuna nuova transazione | P3b | stessi ID/ordine, crash recovery, no migrazioni distruttive |
| **P4a — lifecycle use case** | Separare il coordinamento START/STOP/ripresa dal worker Android con seam/fake deterministici | P3c | foreground, STOP prima di init, process death, test device |
| **P4b — tracking state transition** | Spostare una transizione alla volta in application/domain, mantenendo ordine callback/snapshot | P4a | replay GPS + tempi UNKNOWN invariati |
| **P5a — location adapter** | Estrarre registrazione GPS e mapping callback/timestamp dal Service; niente nuove frequenze | P4b | fake location, stale/out-of-order, screen off device |
| **P5b — sensor/voice adapters** | Estrarre handling step/inertial e/o TTS in due PR ulteriormente separate se il diff è ampio | P5a | sensor absent/batched, voice sprint priority, Android11 |
| **P6a — history UI** | `HistoryActivity` usa use case di lettura e delete, non `TrackerDb` diretto | P3b | righe identiche, delete sicura, UI recreation |
| **P6b — session UI/map** | `ActiveSessionActivity` e `TrackMap` migrati **in PR distinte** a modelli di presentazione/reader port | P4b e P3b | section map, auto follow, tab, performance |
| **P6c — export** | `SessionJsonExporter` separa formattazione JSON, sorgente dati e SAF Activity | P3b | JSON schema e punti invariati, errore write |
| **P7a — weather domain/application** | Regole meteo/slot con use case e fake clock; spostare i chiamanti uno alla volta | P1a | stesse città/filtri/orari, no background GPS |
| **P7b — weather adapters/UI** | Separare HTTP, scheduler/receiver, notifiche, infine `WeatherSettingsActivity` | P7a | offline/fusi/BOOT_COMPLETED/perms, preview coerente |
| **P8a — updater ports** | Contratti per fetch metadata, download, verifica e install intent senza cambiare endpoint | P1a | hash/certificato/versionCode invariati, fake IO |
| **P8b — updater UI adapter** | `UpdateActivity` usa use case e stati espliciti; installer Android fuori dall'application | P8a | nessuna installazione non verificata, test fallimenti |
| **P9a — battery debug isolation** | Separare `BatteryRecorder` da Activity debug, stato della misura e CSV senza cambiare sampling | P1a | opt-in, no coordinate, export identico |
| **P9b — debug bridge** | Isolare reflection/ponte sessione→`BatteryAutoCapture` con test debug/release | P9a e P4a | no debug nella release, start/stop invariati |
| **P10a — legacy cleanup** | Rimuovere una facade alla volta dopo migrazione di tutti i client; aggiornare Manifest/guardie | tutte le aree interessate | no import/package-private rotti, API 30 |
| **P10b — chiusura programma** | Audit mappa, doc, check reale Android 11, checklist dati/updater/debug | P10a | criteri ADR soddisfatti e maintainer sign-off |

Le etichette `P#` indicano **ordine logico e dipendenze**, non una richiesta di aprire subito 20 PR. Le fasi P6/P7/P8/P9 possono essere sviluppate indipendentemente in parallelo **dopo i prerequisiti**; non far lavorare agenti contemporaneamente sugli stessi file legacy.

## Template per issue operativa (solo dopo accettazione ADR)

```markdown
### Obiettivo e confine
Una responsabilità, classi chiamanti, layer attuale → target, nessuna feature nuova.

### Contratto da preservare
Input, output, timing, effetti SQLite/Preferences, permessi, errori e privacy.

### Criteri di accettazione
- [ ] Test caratterizzazione prima del refactor
- [ ] Diff isolato (un seam), facade temporanea motivata
- [ ] Nessuna violazione ADR-002 e docs/invariants.md
- [ ] verify + android11 superati; CI post-merge verificata
- [ ] Test manuale su dispositivo se tocca hardware/FGS/updater
- [ ] Rollback e gap di test documentati

### Dipendenze
Issue/PR prerequisita, owner, conflitti possibili.

### Out of scope
Modifiche funzionali, nuove librerie, firma e workflow di release.
```

## Strategia di accettazione dei lotti

**Gate G0 — progetto:** ADR accettata e mappa/backlog approvati.

**Gate G1 — test:** caratterizzazione + baseline/fixture rappresentative già verdi su `main`.

**Gate G2 — migration seam:** contratto port testato; CI `verify` e `android11` verdi; nessun import proibito nuovo; schema/prefs invariati.

**Gate G3 — hardware:** quando serve, test reale su telefono Android 11 (con backup per prove dati) di percorso, sosta, riavvio, GPS perso, notifiche e aggiornamento in-place. Le prove mancanti devono essere dichiarate, non ipotizzate.

**Gate G4 — rollout:** merge umano su branch protetto, CI post-merge, eventuale APK debug firmato usando il workflow trusted **solo su richiesta separata del maintainer**; confrontare con build precedente senza sovrascrivere release/secret.

### Rischi e mitigazioni per ordine di priorità

1. **Perdita storico:** introdurre snapshot/repository con test round-trip e DB storici prima di migrare i writer.
2. **Cambiamento del tracking reale:** Android11 emulato + tracce deterministiche + collaudo manuale con schermo spento; non combinare più cambi di sensore.
3. **Regression updater:** contratti hash/certificato/versionCode, separazione signing/manual release e nessuna installazione automatica non verificata.
4. **Scheduling meteo:** test timezone/clock, BOOT_COMPLETED e nessun GPS in background.
5. **Battery debug:** evitare static state e reflection silenziosa, conservando il confine `src/debug`.
6. **Scope/parallelismo:** un solo owner per file legacy coinvolto; PR piccole, rollback per singola PR.

### Decisione attesa dal maintainer

- [ ] Confermare modalità *behavior-preserving*, senza redesign UI.
- [ ] Accettare questa sequenza e i gate, oppure indicare revisioni.
- [ ] Autorizzare **successivamente** la creazione delle issue operative e l'assegnazione ad agenti.

Finché le caselle non vengono approvate, questo rimane un backlog di **progettazione**.
