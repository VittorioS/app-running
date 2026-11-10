# Clean Architecture pragmatica per PassoLibero

**Decisione architetturale (ADR-001).** Adottiamo SOLID e i confini della Clean Architecture in modo incrementale, mantenendo Java 17, Android 11/API 30, i dati utente e la semantica attuale del tracking. **Non** imponiamo subito una riscrittura, un framework DI o quattro moduli Gradle.

## Dipendenze e responsabilità

La direzione delle dipendenze è verso il *domain*. Le interfacce necessarie alla logica vanno definite nel livello più interno che le usa; SQLite, GPS e UI sono dettagli esterni.

| Livello | Package desiderato (sotto `it.example.passolibero`) | Responsabilità | Non deve conoscere |
| --- | --- | --- | --- |
| Domain | `domain` | Regole pure, valori, metriche, movimenti, intervalli; nessun Android | Application, Data, Platform, Presentation, `android.*`, `androidx.*` |
| Application | `application` | Casi d'uso, coordinamento di contratti (es. `SessionRepository`), errori applicativi | Data, Platform, Presentation, `android.*`, `androidx.*` |
| Data | `data` | Adapter SQLite, SharedPreferences, mappe da DB a dominio | Presentation, Platform |
| Platform | `platform` | Adapter Android per GPS/sensori/foreground service; composizione delle dipendenze | Presentation (preferire contratti di navigazione) |
| Presentation | `presentation` | Activity, widget, rendering, stato UI, delega ai casi d'uso | Data, Platform: non interrogare direttamente SQLite dai nuovi schermi |

*Data*, *Platform* e *Presentation* sono adapter esterni, **non** una catena di dipendenze obbligatoria. Un caso d'uso dipende da un'interfaccia interna; l'implementazione concreta entra dall'esterno. Non creare un'interfaccia per ogni classe: usale sui confini reali che devono essere sostituibili o testati.

Esempio:
```text
presentation/SessionScreen -> application/FinishSession -> domain/SessionRepository (port)
                                                            ^
data/SQLiteSessionRepository --------------------------------+
platform/GpsAdapter -> application/RecordLocation -> domain/DistancePolicy
```

## SOLID applicato al progetto

- **S — responsabilità singola:** non fare parsing sensore, classificazione, SQL, TTS e UI nello stesso metodo o classe.
- **O — aperto all'estensione:** nuove strategie di classificazione dietro un contratto solo se esiste una variazione reale; non introdurre gerarchie speculative.
- **L — sostituibilità:** gli adapter di prova e quelli SQLite devono rispettare gli stessi contratti (errori, unità, ordinamento, dati conservati).
- **I — interfacce piccole:** separare ad esempio `SessionReader` da `SessionWriter` solo quando i client li usano separatamente.
- **D — inversione delle dipendenze:** use case e dominio non importano `Activity`, `Service`, `LocationManager`, `SQLiteDatabase` o dettagli UI.

## Stato legacy e strategia di migrazione

La codebase attuale ha classi in `it.example.passolibero` (package radice). **Restano lecite** finché non vengono migrate con test. `TrackingService` è ancora un service con responsabilità sovrapposte: vietato mescolare una sua riscrittura ampia con una normale feature.

Per una nuova feature:
1. Descrivere criteri di accettazione, invarianti e flusso dei dati nella issue.
2. Mettere nuove regole testabili in `domain`, orchestrazione in `application`, adapter in `data`/`platform`, vista in `presentation` quando ha senso. Per integrare codice vecchio, usare piccole facade/adapter anziché migrare tutto.
3. Usare input deterministici nei test; niente GPS reale o Android nelle regole domain/application.
4. Se serve modificare codice legacy, mantenere un diff contenuto, aggiungere regressioni e annotare l'eventuale debito residuo.
5. Prima di spostare classi tra package, pianificare una PR di refactoring **senza cambiamenti funzionali**: prima test di equivalenza, poi estrazione graduale, quindi aggiornamento di import e manifesto/consumer.
6. Mai elevare `minSdk` sopra 30 o attribuire un intervallo sconosciuto a un movimento inventato.

## Controlli reali (e limiti)

- `python3 tests/check-architecture.py` verifica dichiarazioni `package`, import e **riferimenti Java fully-qualified nel codice** dei nuovi package stratificati, inclusi sottopackage e tutte le source set Java (main, debug, test, androidTest). Commenti, stringhe, caratteri e text block vengono esclusi dal controllo per evitare falsi positivi. Il package radice legacy è esplicitamente escluso.
- `python3 -m unittest discover -s tests -p 'test_architecture.py'` testa il guardiano architetturale con file di esempio.
- Spotless applica igiene agli Java toccati rispetto a `origin/main`; applica anche google-java-format alle classi nei nuovi package stratificati. Non riformattiamo automaticamente il legacy.
- Il guardiano è una verifica statica di import e riferimenti espliciti, **non un parser Java completo**: non può dimostrare dipendenze tramite reflection, nomi di classe costruiti a runtime, classloader o comportamenti di business mascherati nella UI. Gli adapter legacy restano esenti fino alla migrazione e la review umana resta essenziale.

Per l'estrazione iniziale di `TrackingService` vedi [issue #6](https://github.com/VittorioS/app-running/issues/6). La PR #2 è già integrata. Per il refactoring del service seguire il diagramma e il piano in [architettura](architecture.md#responsabilità-attuali-di-trackingservice--issue-6), partendo dalla policy pura di recupero e da test di equivalenza.
