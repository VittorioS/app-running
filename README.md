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

## Installazione APK debug senza disinstallare a ogni aggiornamento

GitHub Actions usa una firma debug **persistente** e un `versionCode` crescente.
La chiave privata non viene mai committata in chiaro: il repository pubblico
contiene solo un keystore cifrato e l'impronta pubblica del certificato.

### Attivazione (una sola volta, anche da telefono)

1. In GitHub apri **Settings → Secrets and variables → Actions**
   e scegli **New repository secret**.
2. Crea il secret `PASSOLIBERO_SIGNING_PASSPHRASE` con una password
   casuale robusta di almeno 32 caratteri, da custodire nel tuo password manager.
   Non inserirla nel repository, né condividerla in chat.
3. Avvia nuovamente il workflow **PassoLibero - Genera APK Android**
   sul ramo `feature/v2-session-stats-tabs` dalla scheda **Actions**.
4. La prima esecuzione crea una chiave debug e registra SOLO
   `.github/signing/debug-keystore.p12.enc` (cifrato con
   AES-256-CBC/PBKDF2) e `debug-cert.sha256` nel ramo.
   Tutte le build seguenti decifrano e riutilizzano la medesima chiave.
5. Scarica l'APK soltanto dalle esecuzioni in cui l'artefatto
   `PassoLibero-Android15-debug` viene pubblicato e la firma è verificata.

Se il secret manca, la CI esegue comunque test e compilazione, ma
**salta la pubblicazione dell'APK debug**, per evitare firme variabili.

**Attenzione:** la prima build con questa firma non può aggiornare
la vecchia installazione firmata diversamente. Prima esporta tutte
le sessioni importanti in JSON, poi esegui una sola disinstallazione
e installa il nuovo APK. Le build debug successive si possono
installare direttamente sopra la precedente senza cancellare dati.
Non alterare la passphrase né eliminare il file cifrato: ciò
renderebbe impossibile riutilizzare quella chiave.

## Aggiornamenti APK dall'app (V2)

La Home include **Aggiornamenti dell'app** con due canali: **Debug PR #2**
e **Stabile**. La PR #2 pubblica gli APK debug firmati su una GitHub
pre-release dedicata \`debug-pr-2\` dopo i test. Non si accede agli artifact
temporanei di GitHub Actions e non ci sono token di accesso nell'APK.

Per ogni compilazione riuscita, il workflow pubblica un file
\`passolibero-debug-v<VERSION_CODE>.apk\` e \`update.json\` (versione, SHA-256
del file e del certificato). Il JSON viene caricato *dopo* l'APK. L'app
controlla la versione installata, scarica tramite DownloadManager e verifica
il file completo in locale: SHA-256, identificativo applicazione, versione
superiore, firma del pacchetto già installato e certificato dichiarato.
**Non si aggiorna mentre è attiva una sessione GPS.** Android chiede sempre
la conferma dell'installazione e, quando necessario, il consenso a
«Installare app sconosciute» da PassoLibero.

Il canale **Stabile** può essere attivato in futuro pubblicando una release
GitHub tag \`stable\` con file \`update.json\` e APK
\`passolibero-stable-v<VERSION_CODE>.apk\` coerente e firmato con lo
**stesso certificato** dell'app già installata. Altrimenti la verifica
blocca correttamente l'aggiornamento incrociato.

La pubblicazione GitHub di release e allegati richiede \`contents: write\`
del token GitHub Actions. Se il workflow fallisce al passaggio «Pubblica
aggiornamento debug PR 2», verificare in **Settings → Actions → General →
Workflow permissions** che siano consentite le autorizzazioni di scrittura
e che il repository non abbia policy di protezione che impediscano il
rilascio. Non abilitare autorizzazioni più ampie del necessario.
