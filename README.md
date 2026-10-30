# PassoLibero 2.0 — Mappa live e statistiche complete

App Android nativa in Java (min SDK 30, target SDK 35). Tracciamento GPS e sensori passi durante sessioni avviate dall'utente; storico interamente sul dispositivo.

## Schermate

- **Home:** mappa OpenStreetMap, posizione GPS corrente e SOLO percorso della sessione attiva, aggiornato ogni 1,5 s. Al termine viene svuotata. Pulsante per aprire la schermata dell'allenamento e accesso allo storico.
- **Sessione corrente:** distanza in km, ritmo attuale e medio in **min/km**, velocità in km/h, durata totale, tempo in movimento/corsa/camminata/sosta, passi, cadenza (se sensore disponibile), km corsi e camminati, precisione GPS, checkpoint successivo e metri mancanti. Intervalli checkpoint modificabili: 250/500/1000/2000/5000 m. Obiettivo chilometrico selezionabile, anche personalizzato 0,1–200 km, con progresso.
- **Storico:** elenco degli allenamenti salvati con data, durata, ritmo e passi. Tocca una sessione per visualizzarne la mappa e le statistiche dettagliate.
- **Apprendimento locale:** pulsanti di campionamento camminata/corsa e soglia di velocità configurabile dalla Home.

## Privacy e funzionamento senza Internet

- GPS, conteggio distanza, cronometro, soglie, rilevazione passi, annunci TTS e registrazione dello storico **funzionano senza rete**.
- Le mappe usano libreria **osmdroid (Apache 2.0) 6.1.20** e dati OpenStreetMap, senza API a pagamento. Per vedere nuove tessere cartografiche serve Internet; le tessere già in cache possono rimanere visibili offline. Non è presente alcuna funzione di scaricamento massivo/offline delle mappe ufficiali OSM (vietato dalla loro policy).
- Il tracciato GPS completo rimane nel database SQLite sul telefono. **Il fornitore delle tessere OSM riceve richieste di rete relative alla zona visualizzata** quando la mappa carica online, ma l'app non carica un file con i punti della sessione e non usa un account cloud.
- Le tessere vengono richieste solo per la vista attuale tramite HTTPS, con un User-Agent specifico `PassoLibero/2.0 (it.example.passolibero)`. Attribuzione OSM mostrata nelle schermate con mappa. Informazioni policy: https://operations.osmfoundation.org/policies/tiles/ .
- Nessun servizio a pagamento, chiave API commerciale, pubblicità o analytics.

## Aggiornamento rispetto alle versioni precedenti

- **Correzione distanza GPS** `DistanceAccumulator`: accumula gli spostamenti minimi anziché scartarli a ogni fix GPS; filtra drift da fermo e salti impropri.
- **Database migrato da versione 1 a 2 con ALTER TABLE** per aggiungere `goal_m` senza cancellare sessioni e punti precedenti.
- **SessionDetailActivity** con mappa di una sessione storica e statistiche salvate.
- **RouteSegmenter** impedisce linee fittizie sulla mappa dopo salti o lunghe perdite di segnale.
- Se non hai autorizzato Attività fisica, GPS e durata funzionano; passi/cadenza richiedono il sensore compatibile e il permesso ACTIVITY_RECOGNITION.

## Test

```sh
sh tests/run-core-tests.sh
```

21 controlli JVM includono 100 m a piccoli incrementi GPS, anti-drift, classificazione RUN/WALK, ritmo in min/km, checkpoint, progressione obiettivo e interruzioni della traccia.

## Compilazione Android / GitHub Actions

Configurazione Gradle: plugin Android `8.7.3`, Gradle `8.9`, Java `17`, compileSdk `35`.

La build richiede accesso a Google Maven/Maven Central (una volta) per Android Gradle Plugin e dipendenza open source osmdroid. La compilazione APK in questo ambiente non è stata verificata, perché l'Android SDK non è disponibile qui. Il workflow usa Java 17 e `android-actions/setup-android@v3` con solo `platform-tools` (non il vecchio pacchetto `tools`, che non esiste più).

## Pull request GitHub

La versione 2.0 è proposta tramite una normale pull request dal ramo `feature/passolibero-osm-v2` verso `main` nel repository `VittorioS/app-running`.
I file sorgente Java, il manifesto, i test e il README sono versionati singolarmente nel repository: non è richiesto caricare ZIP. Il workflow GitHub Actions del repository viene eseguito anche sul ramo della PR, genera `:app:assembleDebug` e pubblica l'artefatto APK se la compilazione riesce.

**Attenzione alla firma:** un APK debug generato da un altro runner può usare una chiave diversa e rifiutare l'aggiornamento. Non disinstallare l'app esistente se vuoi conservarne le sessioni senza aver prima esportato i dati.

## Verifiche pratiche consigliate

1. Se Android consente un aggiornamento con firma compatibile, verificare che la migrazione del DB mantenga gli allenamenti precedenti; non disinstallare la versione vecchia se contiene dati da preservare.
2. Avviare un allenamento all'aperto con GPS preciso; percorrere 100 m misurati e confrontare la distanza (~0,10 km, secondo accuratezza GPS reale).
3. Controllare l'aggiornamento linea/marker in Home. Tornare alla schermata statistiche, cambiare checkpoint e obiettivo chilometrico.
4. Terminare: il percorso deve sparire dalla Home ed essere consultabile soltanto nello Storico, senza segmenti fittizi sui tratti con GPS perso.
5. Senza Internet: GPS, conteggio e percorso continuano; tiles OSM non già in cache potrebbero non apparire.
