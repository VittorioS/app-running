# PassoLibero 2.0 — Mappa live e statistiche complete

App Android nativa in Java (**Android 11/API 30 obbligatorio**, minSdk 30, compile/target SDK 35). CI con test strumentali API 30. Tracciamento GPS e sensori passi durante sessioni avviate dall'utente; storico interamente sul dispositivo.

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
- **Sezioni del percorso:** nella sessione corrente e nello storico i tratti WALK/RUN/SPRINT hanno colori distinti e statistiche dedicate. La lista sotto la mappa permette di selezionare un tratto: solo quello mantiene il proprio colore, gli altri diventano grigi. Toccando un tratto sulla mappa si seleziona la riga corrispondente e si apre il dettaglio GPS. "Mostra tutte" ripristina i colori e, nella sessione live, il seguito della posizione.
- Se non hai autorizzato Attività fisica, GPS e durata funzionano; passi/cadenza richiedono il sensore compatibile e il permesso ACTIVITY_RECOGNITION.

## Test

```sh
sh tests/run-core-tests.sh
```

I test JVM includono 100 m a piccoli incrementi GPS, anti-drift, classificazione RUN/WALK, ritmo in min/km, checkpoint, progressione obiettivo e interruzioni della traccia. `MotionRegressionTest` verifica inoltre scuotimenti irregolari, conflitti GPS/accelerometro, dati vecchi o fuori ordine, sosta/ripartenza, contapassi ritardato e conservazione dei tempi con callback irregolari.

`python3 tests/check-db-migration.py` esegue su SQLite reale lo schema e le migrazioni dal sorgente: nuove installazioni e aggiornamenti dalle versioni 1, 2 e 3 alla 4, mantenendo sessioni e punti.

## Rilevamento fermo e movimento

- GPS recente, passi hardware e accelerometro forniscono prove separate. Il solo accelerometro richiede almeno quattro picchi con intervalli regolari e altri 2 secondi di evidenza continua. Se contraddice un GPS affidabile vicino a zero, lo stato resta incerto e la distanza GPS non viene accumulata da questa sola prova.
- Velocità, andatura e campioni del profilo usano il timestamp reale del fix. Dati GPS oltre 2,5 secondi non aggiornano l'andatura né la distanza corrente. Una sosta confermata o la perdita prolungata di prove invalida la vecchia categoria corsa/camminata.
- `MOVING_UNKNOWN` significa movimento certo con andatura ignota: il suo tempo è incluso nel totale in movimento ed è salvato separatamente da quello con movimento incerto. Il database passa alla versione 4 senza cancellare dati; gli intervalli storici già sconosciuti non vengono riclassificati retroattivamente.
- Con Step Detector la conferma della sosta resta di 4/8 secondi. Con il solo Step Counter registrato diventa di almeno 12 secondi, per tollerare fino a 10 secondi di consegna ritardata. Un batch tardivo può invalidare una sosta ancora pendente se l'ultimo passo cade al suo interno; non ricostruisce i timestamp degli altri passi e non trasforma l'intero intervallo in camminata.
- Se la registrazione del detector fallisce, vengono tentati il contatore e il fallback accelerometrico. Le soglie restano da calibrare su telefoni reali, nelle diverse posizioni del dispositivo e a schermo bloccato.

## Compilazione Android / GitHub Actions

Configurazione Gradle: plugin Android `8.7.3`, Gradle `8.9`, Java `17`, compileSdk `35`.

La build richiede accesso a Google Maven/Maven Central (una volta) per Android Gradle Plugin e dipendenza open source osmdroid. Le pull request sono verificate dal workflow di sola lettura con compilazione, test, lint e test Android 11. Il workflow non pubblica release e non accede a credenziali di firma.

## Pull request GitHub

Consulta [AGENTS.md](AGENTS.md), [CONTRIBUTING.md](CONTRIBUTING.md) e [docs/clean-architecture.md](docs/clean-architecture.md). Le PR da fork sono verificate automaticamente; i check `verify` e `android11`, la review umana e il ruleset `protect-main` sono obbligatori.

**Firma APK:** un APK debug generato senza la firma persistente può non aggiornare un'installazione già esistente. Conserva un backup dello storico prima di sostituire un'installazione con firma diversa.

## Verifiche pratiche consigliate

1. Se Android consente un aggiornamento con firma compatibile, verificare che la migrazione del DB mantenga gli allenamenti precedenti; non disinstallare la versione vecchia se contiene dati da preservare.
2. Avviare un allenamento all'aperto con GPS preciso; percorrere 100 m misurati e confrontare la distanza (~0,10 km, secondo accuratezza GPS reale).
3. Controllare l'aggiornamento linea/marker in Home. Tornare alla schermata statistiche, cambiare checkpoint e obiettivo chilometrico.
4. Terminare: il percorso deve sparire dalla Home ed essere consultabile soltanto nello Storico, senza segmenti fittizi sui tratti con GPS perso.
5. Senza Internet: GPS, conteggio e percorso continuano; tiles OSM non già in cache potrebbero non apparire.

## Firma debug e aggiornamenti APK (V2)

La V2 include la schermata **Aggiornamenti dell'app** e un verificatore di APK che controlla hash, certificato, package e versione prima di richiedere l'installazione. È disponibile il canale preesistente `debug-pr-2`; non è garantito che contenga una versione successiva.

La vecchia pipeline `bootstrap.yml`, che poteva pubblicare release ed eseguire push con permessi di scrittura, **non viene ripristinata** dopo l'integrazione delle regole di contribuzione. La CI delle PR verifica soltanto codice e APK, senza token di pubblicazione o segreti. La nuova pipeline di rilascio separata `.github/workflows/release-debug.yml` viene avviata manualmente esclusivamente da `main`, con ambiente protetto e firma debug originale; consulta [il runbook di rilascio](docs/release.md). La configurazione amministrativa dell'ambiente `apk-release` è indispensabile prima di usarla. Non allargare i permessi del workflow che esegue codice di PR esterne.

È possibile generare una build debug locale con `sh ./gradlew :app:assembleDebug`. Per aggiornare senza perdere dati occorre usare la stessa chiave di firma dell'APK già installato; se non coincide, esportare e salvare i dati prima di prendere decisioni sull'installazione. L'aggiornamento in-app non deve interrompere una sessione attiva.
