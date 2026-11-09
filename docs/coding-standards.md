# Standard di codice Java — PassoLibero

## Leggibilità e manutenzione

- Nomi espliciti: `distanceMeters`, `elapsedMs`, `timestampElapsedMs`, non `d` o `time` per variabili condivise. Unità negli identificatori; nel modello metri e millisecondi.
- Una classe deve avere uno scopo coerente; metodi brevi quando una separazione chiarisce davvero il comportamento. Evitare helper, classi astratte e interfacce creati solo per ragioni estetiche.
- Validare gli ingressi al confine (GPS nullo, valori non finiti, fix stale, permesso negato). Non mascherare errori con `catch (Exception)` vuoti o fallback che inventano dati.
- Preferire oggetti immutabili e dipendenze esplicite quando possibile; evitare singleton globali e stato statico mutabile per sessioni.
- Vietati token, coordinate personali e dati sensibili in log e fixture. I test usano coordinate sintetiche.
- Usare costanti con unità per soglie e timeout. Documentare *perché* una soglia è necessaria, non solo il suo valore.
- Non inserire regole GPS/classificazione/contabilità del tempo nelle `Activity`. La UI presenta risultati dei casi d'uso, non scrive direttamente lo stato di dominio.
- Non introdurre nuove dipendenze senza giustificare dimensione, licenza, compatibilità Android 11 e vantaggio rispetto alla libreria standard.

## Formattazione riproducibile

- UTF-8, LF, newline finale, niente spazi finali; indentazione Java di quattro spazi (vedi `.editorconfig`).
- Nei nuovi package `domain/`, `application/`, `data/`, `platform/`, `presentation/` usare google-java-format **AOSP**, fissato in Gradle tramite Spotless.
- Per il codice legacy nel package radice la prima fase applica controlli su whitespace e newline, non una riscrittura massiva con un formatter. Quando una classe viene migrata in un nuovo livello, acquisisce anche la formattazione standard.
- Con il repository Git completo e `origin/main` disponibile: `sh ./gradlew :app:spotlessCheck` per verificare, `sh ./gradlew :app:spotlessApply` per correggere. La CI non applica modifiche automaticamente.
- Spotless usa `ratchetFrom 'origin/main'`: solo i Java modificati rispetto al ramo base vengono controllati, evitando rumore su file legacy non toccati. Per un clone nuovo verificare `git fetch origin main`; le Actions usano `fetch-depth: 0`.

## Test e PR

- Ogni cambiamento di comportamento deve avere test che lo distinguano dal vecchio: caso nominale, limite e regressione. Migrazioni SQLite: test dei dati preesistenti.
- I casi d'uso devono essere testabili senza `Activity`, `Service`, rete o GPS.
- Nella PR indicare esplicitamente livello/i modificati e verso di dipendenza, eventuali eccezioni legacy, test eseguiti e test su telefono Android 11 necessari.
- SOLID è una guida di progettazione, non una scusa per aumentare le astrazioni. Evitare il refactoring non collegato alla feature.
