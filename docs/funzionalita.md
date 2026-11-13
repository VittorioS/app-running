# PassoLibero — guida funzionale dell'app

> **Scopo del documento.** Descrivere le funzionalità osservabili di PassoLibero dal punto di vista di chi la utilizza, di chi definisce i requisiti e di chi verifica il prodotto. La descrizione è ricavata dal codice presente nel ramo `main` al momento dell'analisi (14 novembre 2026). Non sostituisce una prova su dispositivo reale.

## 1. Cos'è PassoLibero

PassoLibero è un'app Android per registrare **sessioni di corsa e camminata avviate volontariamente dall'utente**, consultare statistiche e percorsi GPS, eseguire prove sprint e rivedere gli allenamenti precedenti. Offre inoltre notifiche facoltative sulle condizioni meteo favorevoli alla corsa e una funzione di aggiornamento dell'APK.

Il tracciamento della sessione è **offline-first**: GPS, cronometro, stime di movimento, passi (se disponibili), salvataggio locale e annunci vocali non richiedono un account né un collegamento continuo a Internet. Il funzionamento concreto dipende dai permessi, dai sensori e dalle condizioni del dispositivo.

**Compatibilità prevista:** Android 11 (API 30) o successivo.

## 2. Percorso principale dell'utente

1. Dalla **Home** si avvia una sessione dopo aver attivato la localizzazione e concesso il permesso di posizione precisa.
2. L'app controlla brevemente la qualità del GPS. Se entro circa 12 secondi non ottiene un fix attendibile, propone di **continuare comunque, riprovare o annullare**, segnalando il possibile impatto sull'accuratezza.
3. Nella **Sessione in corso** si osservano dati, percorso, checkpoint e obiettivi; si può tornare alla Home senza terminare il tracciamento.
4. Con **Termina e salva la sessione** e una conferma esplicita, l'allenamento viene chiuso e conservato sul telefono.
5. Nello **Storico** si aprono mappa e statistiche, si esporta un JSON oppure si elimina definitivamente una sessione dopo conferma.

Non esiste una modalità di registrazione automatica e permanente al di fuori delle sessioni.

## 3. Mappa delle schermate

| Schermata | Scopo e azioni principali |
| --- | --- |
| **Home** | Riepilogo distanza/durata della sessione, avvio, accesso alla sessione corrente e ripresa del tracciamento interrotto, storico, meteo, aggiornamenti, preferenze di classificazione e voce. |
| **Sessione in corso** | Statistiche nelle schede Corsa/Cammina/Totali/Sprint, mappa live, sezioni di percorso, checkpoint, obiettivo di distanza e conclusione dell'allenamento. |
| **Storico allenamenti** | Elenco delle sessioni salvate con data, distanza, durata, ritmo, passi e stato; apertura del dettaglio ed eliminazione con conferma. |
| **Dettaglio sessione** | Statistiche storiche, mappa navigabile, sezioni del percorso, sprint registrati, checkpoint/obiettivo ed esportazione JSON. |
| **Meteo per correre** | Selezione città, condizioni desiderate, orari quotidiani di verifica, anteprima previsioni e notifiche facoltative. |
| **Aggiornamenti PassoLibero** | Scelta canale, ricerca di una versione più recente, download, verifica dell'APK e richiesta di installazione tramite Android. |

**Precisazione rispetto al README storico:** nel codice attuale la **mappa live è nella Sessione in corso**, non nella Home. La Home presenta il riepilogo dell'allenamento e i collegamenti alle altre funzioni.

## 4. Funzionalità di allenamento

### 4.1 Avvio e gestione della sessione

- La registrazione parte soltanto su richiesta dell'utente. L'app verifica che il GPS del telefono sia attivo e richiede i permessi necessari.
- Il monitoraggio è svolto da un servizio Android in primo piano, con notifica di sessione; può continuare mentre l'utente consulta altre schermate o blocca lo schermo, nei limiti consentiti dal sistema operativo.
- La Home mostra **distanza**, **durata** e stato corrente dell'attività/GPS. Durante una sessione sostituisce il pulsante di avvio con l'accesso alla sessione corrente.
- Se lo stato segnala una sessione ancora attiva ma il servizio sembra interrotto, la Home rende disponibile **Riprendi tracciamento interrotto**. Gli intervalli non osservati non vengono inventati o attribuiti retroattivamente a corsa/camminata.
- La chiusura richiede conferma: termina il monitoraggio e conserva il percorso e i dati della sessione nello storico.

### 4.2 Dati in tempo reale e classificazione

La pagina di allenamento mostra lo **stato riconosciuto** (corsa, camminata, fermo oppure in analisi) e la qualità del GPS. Le schede hanno significati diversi:

| Scheda | Informazioni |
| --- | --- |
| **Corsa** | Dati attribuiti ai soli tratti classificati come corsa; include la velocità massima di corsa attendibile. |
| **Cammina** | Dati attribuiti ai soli tratti classificati come camminata. |
| **Totali** | Somma dei tratti classificati come corsa e camminata, con indicazione separata di passi/distanza non attribuiti e tempi fermi, non classificati o incerti. **Non coincide necessariamente con il tempo totale trascorso.** |
| **Sprint** | Configurazione, avvio, annullamento e riepilogo delle prove sprint della sessione. |

Nelle prime tre schede sono disponibili, quando calcolabili: **distanza (km), tempo, ritmo medio e attuale (min/km), velocità media e attuale (km/h), passi attribuiti e cadenza media (passi/min)**. I dati istantanei vengono mostrati solo quando coerenti con la modalità riconosciuta; valori non affidabili o assenti sono indicati come non disponibili anziché come misure certe.

GPS, sensori di passi e accelerometro concorrono al rilevamento del movimento, con controlli su freschezza e affidabilità. Il movimento confermato ma non classificabile rimane distinto da corsa e camminata. Una sosta riconosciuta può produrre un annuncio vocale di pausa/ripresa, ma **non costituisce un comando manuale di pausa del tracciamento**.

### 4.3 Mappa GPS e sezioni del percorso

- La **mappa live** visualizza la posizione attuale e **soltanto il tracciato della sessione corrente**, con inseguimento della posizione.
- Le sezioni sono differenziate per tipologia: **verde camminata, blu corsa, rosso sprint, grigio fermo/incerto**.
- La selezione di un tratto dalla lista o dalla mappa mostra i relativi dettagli (fra cui distanza, durata e velocità) ed evidenzia la sezione; **Mostra tutte** ripristina la visualizzazione completa.
- Le discontinuità del GPS non devono essere rappresentate con collegamenti rettilinei artificiali tra punti lontani.
- Nel dettaglio storico la mappa mostra il **percorso della sessione selezionata** e può essere esplorata manualmente.

La cartografia utilizza OpenStreetMap tramite osmdroid. Le **nuove tessere** richiedono connettività; quelle già presenti nella cache possono essere visibili senza rete. Non è disponibile un download massivo delle mappe per uso offline.

### 4.4 Checkpoint, obiettivi e annunci vocali

- **Checkpoint di distanza** configurabili a **250 m, 500 m, 1 km, 2 km o 5 km**. L'interfaccia presenta il successivo traguardo e i metri ancora mancanti.
- **Obiettivo di distanza** opzionale: nessuno, **1, 2, 3, 5, 10 km**, **mezza maratona (21,1 km)** oppure valore personalizzato **da 0,1 a 200 km**. Sono mostrati avanzamento percentuale e distanza residua.
- Dalla Home si possono attivare/disattivare gli **annunci vocali ai checkpoint**. La sintesi vocale usa il sistema Android in italiano, preferendo una voce utilizzabile senza rete; la disponibilità dipende dal motore TTS installato.
- Checkpoint e obiettivo vengono conservati tra le preferenze e le informazioni utili per il riepilogo della sessione.

### 4.5 Preferenze e apprendimento della corsa/camminata

La Home consente di:
- modificare la **soglia di passaggio alla corsa** fra **3 e 12 km/h**, a incrementi di **0,5 km/h**;
- abilitare o disabilitare la **classificazione adattiva sul telefono**;
- abilitare o disabilitare gli **annunci vocali ai checkpoint**.

Nelle schede **Corsa** e **Cammina**, durante una sessione attiva, i pulsanti **Insegna all'app: sto correndo / sto camminando** consentono di fornire un campione contestuale al profilo locale. L'apprendimento non implica account, sincronizzazione o addestramento su un server.

### 4.6 Prove sprint

La scheda Sprint permette di effettuare più prove **una per volta**, all'interno di una sessione attiva:
- **conto alla rovescia** selezionabile fra **5 e 10 secondi**, con gestione vocale;
- prova **a tempo** da **5 a 120 secondi** oppure **a distanza** da **10 a 500 metri**;
- visualizzazione dello stato, della durata, della distanza, della **velocità media e massima stimate** e del numero di campioni GNSS validi;
- **annullamento** della prova in corso e consultazione delle prove già registrate nella stessa sessione; risultati disponibili anche nel dettaglio storico.

Durante la prova viene richiesta una frequenza GPS più elevata, **se supportata dal telefono**. Velocità e distanze sprint sono stime GNSS e **non costituiscono cronometraggio sportivo certificato**. Con campioni insufficienti la velocità media può non essere disponibile.

## 5. Storico, dettaglio, esportazione ed eliminazione

### 5.1 Elenco e dettaglio

Lo **Storico allenamenti** riporta per ogni sessione data/ora, distanza, durata, ritmo, passi e stato (completata oppure in corso/interrotta). Aprendo il dettaglio è possibile:
- esaminare statistiche **Corsa**, **Cammina** e **Totali** senza attribuire ai tratti incerti dati non registrati;
- visualizzare velocità massima di corsa se presente, percorso GPS a colori, sezioni selezionabili e risultati sprint;
- ritrovare intervallo checkpoint e obiettivo di distanza associati alla sessione, quando disponibili;
- consultare durata complessiva, distanza, passi totali e ripartizione del tempo in movimento, corsa, camminata, fermo, movimento non classificato e movimento incerto.

### 5.2 Esportazione JSON

Dal **dettaglio sessione**, con **Esporta sessione in JSON**, Android chiede all'utente **dove creare il file**. L'esportazione comprende statistiche, informazioni della sessione e **punti GPS con coordinate precise**; non è un caricamento automatico su un server. Il file va trattato come dato personale sensibile perché può rivelare luoghi e abitudini.

### 5.3 Eliminazione

Dallo Storico si può eliminare **una sessione alla volta**, previa finestra di conferma. La cancellazione riguarda anche i punti GPS associati ed è **irreversibile** dall'app; viene suggerito di esportare prima il JSON. **La sessione attiva non è eliminabile** finché il tracciamento non viene terminato.

## 6. Meteo per correre (facoltativo, online)

La schermata meteo consente di:
1. **Cercare località** e aggiungerle manualmente a un elenco, fino a **20 città**; le città possono essere anche rimosse. La ricerca non usa un GPS di background.
2. Definire condizioni facoltative di **temperatura minima**, **temperatura massima** e **vento massimo**. Se più condizioni sono valorizzate, **devono essere tutte rispettate**.
3. Impostare **uno o più orari locali ogni giorno** e attivare/disattivare le notifiche per condizioni favorevoli.
4. Richiedere manualmente **Aggiorna condizioni meteo adesso** per vedere le previsioni orarie delle prossime **24 ore**, con indicazione delle ore favorevoli.

Le notifiche automatiche richiedono **almeno una città e un orario**, la connessione e il permesso Android alle notifiche dove previsto. Si riferiscono a ore previste **nelle successive 6 ore**, con un limite di **un avviso ogni 6 ore per città**. Android può posticipare l'esecuzione dei controlli per risparmio energetico: **l'orario non è garantito**. Le previsioni **Open-Meteo** sono stime del modello, non misure rilevate dal telefono né una garanzia di condizioni sicure.

Senza rete restano consultabili/configurabili le preferenze già salvate, ma **ricerca città, nuove previsioni e verifiche online non possono essere garantite**.

## 7. Aggiornamenti dell'app (facoltativi, online)

La schermata dedicata visualizza la versione installata e consente la scelta tra **canale debug sperimentale** e **canale stabile**, quando per il canale esiste una release compatibile su GitHub.

Flusso: **Controlla aggiornamenti → Scarica APK → Verifica APK → Autorizza l'installazione su Android**. L'app verifica versione più recente, provenienza attesa, identificativo del pacchetto, hash SHA-256 e certificato di firma prima di richiedere l'installazione; **non installa in modo silenzioso**.

Un aggiornamento non deve interrompere l'attività: **download/installazione vengono bloccati quando una sessione GPS è attiva**. La disponibilità di un pacchetto su uno dei due canali non è garantita.

## 8. Permessi, connettività e dati

| Aspetto | Comportamento funzionale |
| --- | --- |
| **Posizione precisa / GPS** | Necessari per il percorso GPS; in caso di localizzazione disattivata o segnale impreciso l'app mostra avvisi e alternative. |
| **Riconoscimento attività e sensori passi** | Passi/cadenza dipendono da permesso e hardware. La loro assenza non deve generare valori fittizi. |
| **Servizio e notifica Android** | Durante la sessione viene usato un servizio in primo piano; i comportamenti effettivi in background dipendono anche dal sistema operativo. |
| **Senza Internet** | Continuano registrazione GPS, cronometro, calcoli, storico locale ed elaborazioni vocali se il motore TTS offline è disponibile. |
| **Con Internet** | Tessere mappa non in cache, ricerca e previsioni meteo, download e verifica disponibilità aggiornamenti. |
| **Salvataggio dei dati** | Sessioni, statistiche, sprint e punti del percorso sono memorizzati localmente in SQLite; lo stato attivo e le impostazioni sono conservati sul dispositivo. |
| **Trasferimento dati** | Nessun account né sincronizzazione cloud per gli allenamenti; l'esportazione JSON richiede un'azione esplicita. Le richieste di tessere OSM rivelano al relativo provider l'area mostrata; i servizi meteo ricevono i nomi cercati e le coordinate delle località selezionate. |

## 9. Limiti e aspettative corrette

- **Precisione non assoluta:** distanza, velocità, classificazione di corsa/camminata e risultati sprint dipendono da qualità GPS e sensori; non sono misure professionali certificate.
- **Dati mancanti:** le fasi non osservate, il movimento senza andatura certa e le misure non affidabili non devono essere presentati come corsa o camminata certe.
- **Sessione esplicita:** nessun tracciamento continuo fuori allenamento; il monitoraggio meteo programmato è una funzione separata e facoltativa.
- **Offline parziale della mappa:** l'allenamento funziona senza rete, ma la cartografia non ancora in cache può non essere visibile.
- **Meteo non garantito in orario preciso:** dipende da rete, previsioni del provider e pianificazione Android.
- **Storico soltanto locale:** non è prevista una funzione di recupero tramite account cloud; prima di eliminare sessioni o sostituire installazioni incompatibili è opportuno esportare i dati.
- **Diagnostica batteria:** presente **solo nelle build debug**, non è una funzionalità standard della versione per utenti finali.

## 10. Tracciabilità nel repository

Questa guida descrive soprattutto il comportamento esposto dai seguenti punti del codice, non i dettagli implementativi dell'architettura.

| Area | Fonti principali |
| --- | --- |
| Home, avvio e preferenze | [MainActivity.java](../app/src/main/java/it/example/passolibero/MainActivity.java), [AppPrefs.java](../app/src/main/java/it/example/passolibero/AppPrefs.java) |
| Sessione, statistiche e sprint | [ActiveSessionActivity.java](../app/src/main/java/it/example/passolibero/ActiveSessionActivity.java), [SessionTabStats.java](../app/src/main/java/it/example/passolibero/SessionTabStats.java), [SprintTrial.java](../app/src/main/java/it/example/passolibero/SprintTrial.java) |
| Tracking, classificazione e voce | [TrackingService.java](../app/src/main/java/it/example/passolibero/TrackingService.java), [VoiceCoach.java](../app/src/main/java/it/example/passolibero/VoiceCoach.java), [docs/invariants.md](invariants.md) |
| Mappa e sezioni | [TrackMap.java](../app/src/main/java/it/example/passolibero/TrackMap.java), [RouteSectionList.java](../app/src/main/java/it/example/passolibero/RouteSectionList.java) |
| Storico e dettaglio | [HistoryActivity.java](../app/src/main/java/it/example/passolibero/HistoryActivity.java), [SessionDetailActivity.java](../app/src/main/java/it/example/passolibero/SessionDetailActivity.java) |
| Esportazione e dati | [SessionJsonExporter.java](../app/src/main/java/it/example/passolibero/SessionJsonExporter.java), [TrackerDb.java](../app/src/main/java/it/example/passolibero/TrackerDb.java) |
| Meteo | [WeatherSettingsActivity.java](../app/src/main/java/it/example/passolibero/WeatherSettingsActivity.java), [WeatherMonitor.java](../app/src/main/java/it/example/passolibero/WeatherMonitor.java), [WeatherRules.java](../app/src/main/java/it/example/passolibero/WeatherRules.java) |
| Aggiornamenti e compatibilità | [UpdateActivity.java](../app/src/main/java/it/example/passolibero/UpdateActivity.java), [UpdateManager.java](../app/src/main/java/it/example/passolibero/UpdateManager.java), [AndroidManifest.xml](../app/src/main/AndroidManifest.xml) |

**Metodo e stato della verifica:** analisi statica di interfacce e flussi applicativi su `main` e confronto con la documentazione esistente. Non sono stati effettuati test manuali sul dispositivo, chiamate reali ai servizi online o prove di accuratezza GPS; la disponibilità e l'affidabilità effettive richiedono verifica in esercizio.
