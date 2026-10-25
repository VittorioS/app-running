# PassoLibero — tracker corsa Android nativo

Sorgenti di una **prima versione funzionante a livello di implementazione** (non ancora verificata su dispositivo reale), in Java + Android SDK, senza librerie runtime di terze parti. Progettata per Android 15 e compatibile a partire da **Android 11 (API 30)**.

## Funzionalità implementate

- **Avvio e fine manuali**: nessuna rilevazione automatica fuori dalle sessioni.
- **Foreground Service** dichiarato `location` (e `health` quando è disponibile il permesso `ACTIVITY_RECOGNITION`) per il GPS durante schermo spento e telefono bloccato.
- **Distanza GPS** calcolata con Haversine; rifiuto dei fix troppo imprecisi, dei salti fisicamente implausibili e dei microspostamenti. Il GPS può comunque sbagliare in galleria, sotto edifici o con segnale debole.
- **Tempo** con `SystemClock.elapsedRealtime()`, velocità GPS filtrata e ritmo medio in min/km.
- **Passi** da `TYPE_STEP_DETECTOR` oppure `TYPE_STEP_COUNTER`, se disponibile e autorizzato. Senza sensore/premesso, passi non disponibili (mostrati come 0).
- **Stato del movimento**: `UNKNOWN`, `STILL`, `WALK`, `RUN`, senza interrompere la sessione.
- **Soglia manuale** 3–12 km/h, default 6,0 km/h, isteresi ±0,5 km/h e conferma di 10 secondi.
- **Micro-modello adattivo offline**: medie mobili di velocità camminata/corsa, con campioni automatici solo molto affidabili e possibilità di etichettare manualmente 30 secondi di camminata/corsa. Nessuna libreria ML e nessun file di modello esterno.
- **Checkpoint vocali** ogni 250 m, 500 m, 1 km, 2 km o 5 km. Usano esclusivamente una voce italiana locale installata nel motore TTS del dispositivo.
- **Riproduzione vocale** con audio focus transient/ducking per attenuare eventualmente la musica.
- **Persistenza locale** in SQLite con punti geolocalizzati e sessioni consultabili. Non occorre un server, account o connessione Internet. L'app NON chiede `INTERNET`.
- **Recupero** del servizio quando il sistema lo riavvia; se non si riavvia automaticamente, dalla schermata principale compare "Riprendi servizio interrotto". I punti persi non vengono inventati.

## Come aprirlo e installarlo

1. Installa **Android Studio**, **Android SDK Platform 35** e **JDK 17** (normalmente incluso con Android Studio).
2. Estrai l'archivio, in Android Studio scegli **File → Open** e seleziona la directory `PassoLibero`.
3. Esegui il **Gradle Sync** con Android Gradle Plugin 8.7.3 e **Gradle 8.9**. La distribuzione di Gradle e il plugin Android sono strumenti di build, NON dipendenze incluse nell'APK. Se Android Studio non configura automaticamente Gradle, imposta la versione 8.9 nelle impostazioni Gradle dell'IDE.
4. Seleziona un dispositivo fisico con Android 15 (preferibile all'emulatore per i sensori), quindi **Run 'app'**.
5. Concedi la **posizione precisa** e, se disponibile, il riconoscimento attività. Le notifiche servono a rendere più visibile il servizio, ma la notifica di servizio viene gestita dal sistema anche se il permesso di notifica è negato.
6. Per gli annunci vocali, nelle impostazioni TTS del telefono verifica che sia disponibile una **voce italiana utilizzabile offline**. Consenti che il volume sia udibile (media o guida vocale), anche con eventuali cuffie Bluetooth.
7. Avvia una sessione e blocca lo schermo. **Solo "Termina e salva" conclude la sessione**. Se l'OS interrompe il servizio, i dati già persistiti restano nello storico.

**Build manuale:** questo pacchetto non include `gradle-wrapper.jar` né il binario Gradle, perché non sono stati scaricabili nell'ambiente di generazione. Android Studio può essere configurato con Gradle 8.9 già installato. Un file `gradle/wrapper/gradle-wrapper.properties` specifica la distribuzione corretta ma, da solo, **non abilita `./gradlew`**. Per la riga di comando puoi installare Gradle 8.9 e usare `gradle assembleDebug`, oppure generare il wrapper con `gradle wrapper`.

## Come usarla

- Premi **Inizia sessione** quando sei all'aperto e il GPS è acceso.
- Le statistiche si aggiornano automaticamente finché il Foreground Service è attivo.
- Durante la sessione lo stato può cambiare `RUN → WALK → STILL → RUN` senza creare sessioni nuove o perdere i dati.
- Le impostazioni del checkpoint e della soglia si possono modificare durante la corsa.
- Attiva l'adattamento, poi durante un tratto stabile premi **Sto camminando**; fai lo stesso durante una corsa stabile con **Sto correndo**. Raccogliendo entrambi i tipi di campioni, la soglia personalizzata viene stimata dal punto medio delle velocità apprese e rimane vincolata a ±1,5 km/h rispetto alla soglia manuale.
- Lo storico comprende anche sessioni interrotte prima del completamento.

## Struttura

- `MainActivity.java`: interfaccia di tracciamento e impostazioni.
- `TrackingService.java`: LocationManager, SensorManager, ticker, coordinate, checkpoint e salvataggio.
- `MovementClassifier.java`: filtro velocità, isteresi, stato camminata/corsa/fermo.
- `AdaptiveProfile.java`: apprendimento incrementale del profilo, senza ML esterno.
- `VoiceCoach.java`: TextToSpeech offline e gestione dell'audio focus.
- `RunMath.java`: Haversine, ritmo, formattazione.
- `TrackerDb.java`: SQLite locale, sessioni e punti GPS.
- `RunState.java`, `AppPrefs.java`: snapshot persistenti e configurazione.
- `HistoryActivity.java`: storico delle sessioni.
- `tests/run-core-tests.sh`: test JVM dei calcoli e delle transizioni di movimento.

## Verifica effettuata / limiti noti

- ✅ I test JVM delle classi senza Android (Haversine, ritmo, stato di movimento, apprendimento) passano usando JDK 21.
- ✅ XML del manifest e delle risorse validato sintatticamente.
- ⚠️ Non è stata eseguita una compilazione Android o un test sul dispositivo: **Android SDK, Gradle e l'emulatore non erano disponibili nell'ambiente**. Il progetto potrebbe richiedere aggiustamenti dopo il primo Gradle Sync e test reali.
- ⚠️ Android 15 e alcuni produttori possono fermare o rallentare i servizi anche con notifica e schermo bloccato. Il test di affidabilità va eseguito sul tuo telefono. Un riavvio del telefono o un arresto forzato creano un'interruzione non ricostruibile del tracciato.
- ⚠️ La registrazione salvata resta in locale; nessun backup remoto automatico (disabilitato anche il backup Android). Se l'app viene disinstallata, i dati possono andare perduti.
- ⚠️ Senza motore TTS con voce italiana offline, i checkpoint vengono registrati ma l'annuncio potrebbe non essere pronunciato; occorre installare la voce tramite le impostazioni di Android.
- ⚠️ Le metriche dipendono dalla posizione del telefono, dal segnale GNSS e dai sensori disponibili. Non equiparare le misure a dispositivi professionali senza calibrazione.
- ⚠️ Non sono ancora presenti mappa su UI, esportazione GPX/CSV, test strumentali o sincronizzazione con Samsung Health.

## Test algoritmi senza Android

```sh
./tests/run-core-tests.sh
```

## Permessi

- `ACCESS_FINE_LOCATION` e `ACCESS_COARSE_LOCATION`: posizioni GNSS.
- `ACTIVITY_RECOGNITION`: passo/pedometria hardware quando il dispositivo la supporta.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `FOREGROUND_SERVICE_HEALTH`: servizio attivo dichiarato secondo il tipo d'uso.
- `POST_NOTIFICATIONS`: visibilità delle notifiche su Android 13+; la notifica di servizio non è sostituita dagli annunci vocali.
- Nessun `ACCESS_BACKGROUND_LOCATION`, perché l'avvio è manuale con attività visibile e non è previsto il riconoscimento fuori sessione.

**Nota privacy:** le tracce GPS sono personali e non cifrate a livello applicativo; sono protette dall'isolamento app di Android e dall'eventuale cifratura del dispositivo. Non condividere `sessions.db` pubblicamente.

## Generare l'APK con GitHub Actions (senza Android Studio locale)

Questo progetto include il workflow `.github/workflows/android-apk.yml`. Il workflow installa gli strumenti di **compilazione** sul runner GitHub e crea un APK **debug firmato**, installabile su Android 15. Questi strumenti non vengono inclusi nell'app finale.

1. Crea un repository GitHub (preferibilmente **privato**).
2. Carica nella **radice del repository** il contenuto della cartella `PassoLibero`, incluso `.github/workflows/android-apk.yml` (non mettere tutti i file dentro una sottocartella `PassoLibero`).
3. Nel repository apri **Actions → Genera APK Android → Run workflow → Run workflow**. Il workflow parte anche automaticamente dopo i push verso `main` o `master`.
4. Quando l'esecuzione risulta riuscita (verde), aprila e scarica l'artifact **PassoLibero-Android15-debug** dalla sezione *Artifacts*.
5. Estrai l'archivio scaricato: contiene `app-debug.apk`. Trasferiscilo sul telefono, aprilo e autorizza, se richiesto, l'installazione da quella sorgente.

L'APK debug è **firmato automaticamente** da Android Gradle Plugin con una chiave di sviluppo, quindi è utilizzabile per i test personali. Non è una build release da pubblicare su Play Store. La firma generata su GitHub non è garantita identica a quella di una build locale successiva: per aggiornare l'app senza disinstallarla bisogna usare sempre la stessa chiave di firma. La compilazione del workflow **non è stata eseguita né verificata in questo ambiente**, perché non erano disponibili SDK e Gradle né la rete necessaria per installarli.
