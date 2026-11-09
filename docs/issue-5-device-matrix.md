# Issue #5 — matrice di collaudo GPS, sensori e lifecycle su telefono reale

Questo protocollo è **manuale**: non revocare automaticamente permessi e non sostituire l'APK dell'utente nella CI. I test automatici sono riproducibili, ma un vero GPS, i sensori hardware e l'ottimizzazione batteria richiedono un telefono fisico.

## Preparazione

- Usa PassoLibero con la stessa chiave di firma già installata. Prima di qualunque aggiornamento verifica di poter conservare/esportare i dati locali; **non disinstallare** l'app che contiene lo storico.
- Registra modello telefono, versione Android/API, build APK/versionCode, permessi concessi, disponibilità di `TYPE_STEP_DETECTOR` e `TYPE_STEP_COUNTER`, stato risparmio energetico e ottimizzazione batteria.
- Prepara un percorso sicuro e misurabile, con tratti camminata/corsa/fermo; non utilizzare il dispositivo durante guida o altre attività pericolose.
- Considera **atteso** `MOVING_UNKNOWN` quando c'è movimento ma non abbastanza evidenza per RUN/WALK. Non attribuire metri al GPS quando la qualità è insufficiente.

## Sequenza di prova

| Caso | Procedura | Risultato atteso |
| --- | --- | --- |
| Avvio esplicito e permessi | Con nessuna sessione attiva apri Home e storico, poi avvia una sessione con il permesso posizione presente; ripeti su un dispositivo/emulatore di prova con posizione negata | Home/storico non avviano il tracking; senza posizione nessun avvio del percorso, nessun crash |
| Sensore passi assente/negato | Su dispositivo senza contapassi o disabilitando attività fisica nelle impostazioni prova una sessione con GPS autorizzato | Cronometro/GPS funzionano; passi o cadenza possono restare indisponibili senza inventare conteggi |
| Camminata e corsa | Percorri 100–300 m camminando, poi correndo; confronta distanza e tab Corsa/Camminata/Totali | Distanza e tempi coerenti, senza conteggi duplicati, andamento realistico e invarianti di somma |
| Fermo e ripartenza | Sosta almeno 15 s, poi riparti camminando | Il tempo fermo non diventa falsamente corsa; la nuova categoria richiede evidenza fresca |
| GPS scarso / dropout | In un luogo sicuro con segnale schermato osserva perdita/recupero GPS | Qualità degradata segnalata; nessun segmento fittizio, salto di distanza o attribuzione retroattiva |
| Schermo spento | Durante la sessione spegni lo schermo per 3–5 min, riaccendi e torna all'app | Tracking resta coerente come foreground service, notifica visibile dove prevista, dati preservati |
| App in background | Torna a Home e riapri la sessione dopo almeno 2 min | Sessione non terminata involontariamente; ripresa UI mostra stato corrente |
| Processo interrotto | Su dispositivo di prova, interrompi il processo in background senza usare Force stop, poi riapri | Lo stato persistito e lo storico restano leggibili; recupero tramite UI se previsto, nessun doppio conteggio |
| Stop volontario | Termina esplicitamente e riapri l'app | Nessun tracciamento fuori sessione; una sola sessione completata e percorso nello storico |
| Migrazione / aggiornamento | Con allenamenti già salvati installa sopra la vecchia build un APK firmato con lo stesso certificato e `versionCode` crescente | L'installazione riesce senza disinstallazione; storico e punti GPS sono preservati; nuova sessione funzionante |

## Esiti già confermati

Il 9 novembre 2026 il maintainer ha verificato sul dispositivo reale l'aggiornamento firmato alla build debug **10092**, la conservazione dello storico e il corretto funzionamento di una nuova sessione dopo l'aggiornamento. Questa conferma **non sostituisce** le prove specifiche di background, schermo spento e permessi negati su hardware diversi.

## Registrazione dei risultati

Per ogni caso annota **PASS / FAIL / NON TESTATO**, ambiente e prerequisiti, passi eseguiti, osservazioni e logcat (senza coordinate private). Non registrare percorsi geografici identificabili nei ticket pubblici. Se un caso fallisce, crea un bug riproducibile separato, senza allargare lo scope della PR dei test.

## Automatizzato in CI

- `verify`: JVM replay deterministic (`TrackingReplayTest`) + regressioni esistenti, migrazioni SQLite con Python, Gradle unit test e lint.
- `android11`: su emulatore API 30 esegue `TrackingUiInstrumentedTest`, `TrackingPersistenceInstrumentedTest` e gli smoke test già presenti.
- Le test suite Android usano un namespace temporaneo per database/preferenze, non avviano il servizio di tracking, e **non** manipolano permessi globali del dispositivo.
