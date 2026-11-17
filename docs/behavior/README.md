# PassoLibero — specifica funzionale osservata

> **Baseline:** `main` al commit `e6b4ca793170cdbe4b1f36e4b2db60e5ae205033` (17 novembre 2026). Questa documentazione descrive **il comportamento implementato**, non una roadmap o una proposta di nuove feature. Nei punti in cui l'esito dipende da Android, GNSS, rete o servizi esterni, la garanzia è esplicitamente limitata.

## Come leggere la specifica

Ogni feature combina: (1) contesto e dati in Markdown, (2) `stateDiagram-v2` Mermaid, (3) tabella `stato + evento + guardia -> stato + effetti`, (4) invarianti e casi limite. Gli scenari Gherkin sono raccolti soltanto in [accettazione](acceptance.md), per i comportamenti più critici. I nomi in **MAIUSCOLO** rappresentano stati o eventi concettuali, non necessariamente stringhe persistite nel codice.

**Tre livelli distinti**:
- **Sessione**: lifecycle gestito dal foreground service, marcatore `active` in SharedPreferences e righe SQLite.
- **Movimento**: classificazione e ledger di intervalli `RUN / WALK / STILL / MOVING_UNKNOWN / UNKNOWN`, indipendente dalla pagina aperta.
- **Vista/operazione**: tab, dialoghi, richiesta TTS, download, ricerca città e altre operazioni transitorie. Non sostituiscono lo stato del dominio.

Una transizione con guardia falsa **non è ammessa**; se il codice semplicemente ignora il comando, la tabella lo indica. Una risposta asincrona o la consegna di un allarme Android **non è garantita a un istante esatto**. Una tabella che riassume un sottosistema è una **proiezione funzionale** e non pretende di descrivere ogni callback interno.

## Indice delle feature

| Documento | Responsabilità |
| --- | --- |
| [Sessioni e navigazione](session-lifecycle.md) | home, preflight GPS, avvio esplicito, recupero, stop, lifecycle |
| [Movimento, statistiche e impostazioni](motion-and-metrics.md) | prove GPS/sensori, classificazione, tempi, distanza, checkpoint, obiettivi e apprendimento |
| [Sprint](sprint.md) | preparazione TTS, countdown, misurazione, annullamento, risultati |
| [Mappa, storico ed esportazione](routes-history-export.md) | sezioni del percorso, dettaglio, cancellazione e JSON |
| [Meteo programmato](weather.md) | città, criteri, orari, notifiche e preview |
| [Aggiornamenti APK](updates.md) | canali, download, verifica e richiesta d'installazione |
| [Accettazione](acceptance.md) | pochi scenari Given/When/Then prioritari |

## Navigazione implementata

| Origine | Azione | Destinazione / effetto |
| --- | --- | --- |
| `MainActivity` | Inizia sessione | preflight GPS e poi `TrackingService`; apertura `ActiveSessionActivity` |
| `MainActivity` | Vai alla sessione corrente | `ActiveSessionActivity`, disponibile quando `active=true` |
| `MainActivity` | Riprendi tracciamento interrotto | nuova richiesta START quando heartbeat della sessione è vecchio |
| `MainActivity` | Storico | `HistoryActivity` |
| `HistoryActivity` | Apri dettagli | `SessionDetailActivity` della sessione scelta |
| `MainActivity` | Meteo per correre | `WeatherSettingsActivity` |
| `MainActivity` | Aggiornamenti | `UpdateActivity` |
| Home / sessione | Diagnostica batteria | `BatteryDiagnosticsActivity` **solo in build DEBUG** |

La **mappa live** si trova in `ActiveSessionActivity` e quella storica nel dettaglio della sessione, non in Home (il titolo del README generale può suggerire diversamente). Le quattro tab della sessione sono **Corsa / Cammina / Totali / Sprint**: rappresentano una selezione di vista, non quattro sessioni distinte.

## Limiti e invarianti globali

- Android nativo Java 17, `minSdk 30`, API 30 obbligatoria; nessun aumento dei requisiti minimi.
- Sessioni GPS, tempo, voce, metriche e storico sono offline-first; **tessere OSM**, **previsioni Open-Meteo** e **aggiornamenti GitHub** richiedono rete quando non disponibili in cache.
- Nessun tracking senza sessione esplicitamente avviata o ripristino di una sessione già marcata attiva. Nessun account o upload automatico delle coordinate a un backend dell'app.
- Non inventare corsa, camminata, sosta, punti GPS o intervalli non osservati; preservare sessioni e punti nelle migrazioni.
- Nel ledger `movingMs = runMs + walkMs + movingUnknownMs`; il tempo incerto `unknownMs` è distinto da movimento confermato con andatura ignota.
- Distanze di dominio in metri, tempi in millisecondi, velocità in km/h, ritmo UI in min/km; clock monotono per la durata.
- La CI e i test unitari caratterizzano il comportamento, ma non provano ricezione GNSS, sensori o scheduling su qualunque telefono.

Fonti primarie: [invarianti](../invariants.md), [architettura](../architecture.md), [test](../testing.md), classi Java in `app/src/main/java/it/example/passolibero/`. In caso di divergenza tra questa documentazione e il codice su commit successivi, verificare il comportamento nel codice e aggiornare la specifica insieme alla modifica.
