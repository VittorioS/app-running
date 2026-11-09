# Contratti funzionali / invarianti

Ogni modifica che tocca il tracking deve confermare esplicitamente questi vincoli nella PR, con test dove possibile.

1. **Non inventare attività.** L'accelerometro da solo e i GPS inaffidabili non sono prove sufficienti per attribuire arbitrariamente RUN o WALK. Il movimento confermato con andatura sconosciuta resta `MOVING_UNKNOWN`.
2. **Freschezza.** Le misurazioni GPS vecchie, future o fuori ordine non aggiornano distanza/andatura corrente. Il timestamp della misura conta più del timestamp della callback.
3. **Niente retroattività falsa.** Gli intervalli non osservati (worker sospeso, sensore assente) non vengono ricostruiti come movimento o sosta. I batch di step counter ritardati non inventano i timestamp dei passi intermedi.
4. **Contabilità del tempo.** `movingMs = runMs + walkMs + movingUnknownMs`. Per una sessione conclusa, `elapsedMs` deve essere coerente con `runMs + walkMs + movingUnknownMs + stillMs + unknownMs`; le frazioni pending sono contabilizzate esplicitamente fino alla chiusura. Nessun intervallo accreditato due volte.
5. **Distanza e qualità.** Jitter da fermo e salti GPS non aumentano indebitamente la distanza. I piccoli spostamenti reali si accumulano, ma le interruzioni non devono creare linee fittizie nella mappa.
6. **Persistenza conservativa.** Le migrazioni SQLite incrementano la versione, conservano sessioni, punti e stati storici, e includono test per tutte le versioni precedenti supportate. Un aggiornamento APK non deve richiedere cancellazione dei dati.
7. **Privacy.** Coordinate e tracciati delle sessioni non sono caricati su server dell'app. Le richieste alle tile OSM possono rivelare l'area visualizzata; mostrare attribuzione e rispettare le policy del provider.
8. **Offline e permessi.** Sessione, GPS, cronometro, algoritmi, voce e storico funzionano senza rete; GPS o contapassi mancanti/negati degradano correttamente le statistiche, senza crash.
9. **Lifecycle.** Tracciamento solo dopo avvio esplicito; foreground service coerente con Android; chiusura e recupero non perdono dati e non proseguono il tracking fuori sessione.
10. **Unità.** Distanza in metri nel modello, km nell'UI; andatura in min/km; durata con tempo monotono quando possibile.

Test utili: `tests/CoreAlgorithmTest.java`, `tests/MotionRegressionTest.java`, `tests/check-db-migration.py`. I test devono includere sia comportamento nominale sia regressioni su input incerti; la CI non sostituisce una prova su telefono reale.
