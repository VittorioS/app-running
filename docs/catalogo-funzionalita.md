# PassoLibero — catalogo delle micro-funzionalità

Questo documento elenca le **singole funzionalità effettivamente presenti** nell'app Android PassoLibero. Ogni voce usa il formato **Nome: descrizione breve**, adatto a inventari funzionali, pianificazione di test e analisi di prodotto.

**Ambito:** codice del ramo main analizzato il 14 novembre 2026. Le voci descrivono il comportamento implementato, non nuove funzionalità richieste. Le disponibilità effettive di GPS, sensori, notifiche e rete dipendono dal dispositivo. Il documento complementare di descrizione generale è proposto nella [PR #88](https://github.com/VittorioS/app-running/pull/88).

## 1. Home e navigazione

- **Avvia sessione:** avvia una nuova sessione di allenamento di corsa o camminata dopo i controlli iniziali.
- **Distanza nella Home:** mostra i chilometri percorsi nella sessione attiva; non somma gli allenamenti passati.
- **Durata nella Home:** mostra il tempo trascorso dall'avvio della sessione corrente.
- **Stato allenamento nella Home:** indica se l'utente sta correndo, camminando, è fermo o se il movimento è in analisi.
- **Stato GPS nella Home:** visualizza il messaggio sul segnale GPS della sessione corrente.
- **Apri sessione corrente:** accede alle statistiche e alla mappa dell'allenamento in corso.
- **Riprendi tracciamento interrotto:** offre il comando di ripartenza quando risulta una sessione attiva ma il servizio non aggiorna il proprio stato da tempo.
- **Apri storico allenamenti:** accede all'elenco delle sessioni registrate sul dispositivo.
- **Apri meteo per correre:** accede alla configurazione delle previsioni e delle notifiche meteo.
- **Apri aggiornamenti:** accede alla verifica e all'installazione di nuove versioni dell'app.
- **Apri diagnostica batteria (solo debug):** accede agli strumenti diagnostici disponibili esclusivamente nelle build di sviluppo.

## 2. Avvio, conclusione e continuità dell'allenamento

- **Controlla posizione attiva:** verifica che il GPS del telefono sia abilitato prima di cominciare.
- **Apri impostazioni posizione:** propone le impostazioni Android quando la localizzazione è spenta.
- **Richiedi posizione precisa:** chiede il permesso necessario per registrare il percorso GPS.
- **Richiedi riconoscimento attività:** richiede il permesso Android usato dai sensori di attività fisica compatibili.
- **Richiedi notifiche:** richiede, dove previsto da Android, il permesso per mostrare notifiche.
- **Verifica qualità GPS iniziale:** attende un segnale attendibile per un massimo di circa 12 secondi prima dell'avvio.
- **Continua con GPS impreciso:** permette di iniziare comunque dopo un avviso sull'accuratezza.
- **Riprova acquisizione GPS:** consente di ripetere il controllo iniziale del segnale.
- **Annulla avvio sessione:** rinuncia all'avvio in presenza dell'avviso GPS.
- **Tracciamento in primo piano Android:** mantiene la registrazione tramite servizio Android e relativa notifica durante una sessione.
- **Torna alla Home durante l'allenamento:** permette di uscire dalla pagina delle statistiche senza terminare la sessione.
- **Termina sessione di allenamento:** conclude la registrazione, disattiva l'ascolto dei sensori/GPS e salva i dati nello storico.
- **Conferma termine sessione:** chiede una conferma prima di chiudere definitivamente l'allenamento.
- **Ripristino conservativo della sessione:** gestisce l'interruzione del servizio senza inventare attività fisica negli intervalli non osservati.

## 3. GPS, movimento e classificazione

- **Acquisizione posizione GPS:** rileva i punti geografici durante una sessione avviata.
- **Monitoraggio qualità GPS:** segnala la condizione del segnale utilizzato per l'allenamento.
- **Avviso per GPS impreciso:** informa l'utente quando la precisione del segnale può compromettere le misure.
- **Filtro delle oscillazioni GPS:** limita l'aumento artificiale della distanza quando il telefono è fermo.
- **Accumulo di piccoli spostamenti:** tiene conto degli spostamenti reali brevi, senza scartarli tutti come rumore.
- **Esclusione dei dati GPS non attendibili:** evita che letture vecchie, future, fuori ordine o molto imprecise falsino le statistiche.
- **Riconoscimento corsa:** classifica come corsa i periodi per cui le prove di movimento sono sufficienti.
- **Riconoscimento camminata:** classifica come camminata i periodi per cui le prove di movimento sono sufficienti.
- **Riconoscimento fermo:** distingue le soste dai periodi di movimento rilevato.
- **Movimento non classificato:** conserva separatamente il movimento confermato ma non attribuibile con certezza a corsa o camminata.
- **Movimento incerto:** distingue i periodi senza prove sufficienti da quelli realmente classificati.
- **Conteggio passi:** raccoglie le informazioni dei sensori di passi compatibili, quando permessi e hardware lo consentono.
- **Rilevamento movimento da accelerometro:** utilizza evidenze inerziali come supporto, senza trasformarle da sole in una corsa certa.

## 4. Dati e statistiche della singola sessione

- **Distanza complessiva della sessione:** mostra i chilometri percorsi nell'allenamento selezionato o in corso.
- **Durata complessiva della sessione:** mostra il tempo totale trascorso nell'allenamento.
- **Distanza di corsa:** mostra i chilometri attribuiti ai tratti classificati come corsa.
- **Distanza di camminata:** mostra i chilometri attribuiti ai tratti classificati come camminata.
- **Tempo di corsa:** mostra quanto tempo è stato attribuito alla corsa.
- **Tempo di camminata:** mostra quanto tempo è stato attribuito alla camminata.
- **Tempo in movimento:** mostra il tempo complessivo riconosciuto come movimento, incluso quello dall'andatura non classificata.
- **Tempo da fermo:** mostra il tempo rilevato come sosta.
- **Tempo di movimento non classificato:** mostra il tempo in cui il movimento è certo ma non è chiaro se sia corsa o camminata.
- **Tempo di movimento incerto:** mostra il tempo non attribuibile in modo attendibile a movimento o sosta.
- **Ritmo medio:** mostra il passo medio in minuti per chilometro per i tratti considerati.
- **Ritmo attuale:** mostra il passo istantaneo, quando la velocità corrente è attendibile.
- **Velocità media:** mostra la velocità media in km/h per i tratti considerati.
- **Velocità attuale:** mostra la velocità istantanea in km/h quando disponibile e coerente con la modalità.
- **Velocità massima di corsa:** mostra la massima velocità di corsa accettata dai controlli di affidabilità.
- **Passi complessivi:** mostra il numero di passi registrati nella singola sessione.
- **Passi attribuiti alla corsa:** mostra i passi associati ai tratti riconosciuti come corsa.
- **Passi attribuiti alla camminata:** mostra i passi associati ai tratti riconosciuti come camminata.
- **Passi non attribuiti:** segnala i passi che non rientrano nelle categorie corsa o camminata.
- **Distanza non classificata:** mostra la porzione di distanza non assegnata con certezza alla corsa o alla camminata.
- **Cadenza media:** calcola, quando possibile, i passi al minuto dei tratti selezionati.
- **Scheda Corsa:** filtra le statistiche sui soli tratti riconosciuti come corsa.
- **Scheda Cammina:** filtra le statistiche sui soli tratti riconosciuti come camminata.
- **Scheda Totali:** somma le statistiche classificate come corsa e camminata nella stessa sessione, separando i dati incerti.
- **Dati non disponibili:** visualizza un'indicazione di indisponibilità invece di presentare come valide velocità o cadenze mancanti.

## 5. Mappa e sezioni del percorso

- **Mappa della sessione corrente:** mostra su OpenStreetMap il tracciato GPS dell'allenamento in corso.
- **Posizione corrente sulla mappa:** mostra la posizione rilevata dal GPS durante la sessione.
- **Inseguimento automatico posizione:** mantiene la mappa live centrata sul percorso o sulla posizione corrente.
- **Colori delle attività sulla mappa:** distingue visivamente camminata, corsa, sprint e intervalli fermi/incerti.
- **Suddivisione del percorso in sezioni:** organizza il tracciato in porzioni riconducibili a diversi tipi di movimento.
- **Elenco delle sezioni:** mostra i diversi tratti che compongono il percorso.
- **Seleziona sezione dall'elenco:** evidenzia sulla mappa il tratto scelto.
- **Seleziona sezione dalla mappa:** permette di aprire i dettagli del tratto toccato.
- **Distanza della sezione:** mostra i metri percorsi nel tratto selezionato.
- **Durata della sezione:** mostra il tempo trascorso nel tratto selezionato.
- **Velocità media della sezione:** mostra la velocità media stimata per il tratto.
- **Velocità massima della sezione:** mostra la velocità massima registrata per il tratto, se disponibile.
- **Campioni GPS della sezione:** mostra quanti punti GPS sono associati al tratto.
- **Mostra tutte le sezioni:** rimuove il filtro di selezione e ripristina il percorso completo.
- **Discontinuità del tracciato:** evita di disegnare falsi collegamenti fra punti separati da una perdita prolungata di segnale.
- **Mappa storica:** mostra il percorso salvato della sessione aperta dallo storico.
- **Esplora mappa storica:** consente di muoversi nella mappa del percorso registrato.
- **Tessere cartografiche in cache:** permette di visualizzare offline le tessere della mappa già disponibili sul dispositivo.

## 6. Checkpoint e obiettivi

- **Prossimo checkpoint:** mostra la distanza del prossimo traguardo intermedio.
- **Distanza rimanente al checkpoint:** mostra quanti metri mancano al traguardo intermedio.
- **Imposta intervallo checkpoint:** permette di scegliere 250 m, 500 m, 1 km, 2 km o 5 km.
- **Disattiva obiettivo distanza:** permette di allenarsi senza un obiettivo chilometrico.
- **Seleziona obiettivo predefinito:** permette di scegliere 1, 2, 3, 5, 10 km o 21,1 km (mezza maratona).
- **Imposta obiettivo personalizzato:** accetta un obiettivo da 0,1 a 200 km.
- **Progresso dell'obiettivo:** mostra quanto è stato raggiunto in percentuale.
- **Distanza rimanente all'obiettivo:** mostra quanti metri o chilometri mancano.
- **Checkpoint nello storico:** riporta l'intervallo impostato e l'ultimo checkpoint raggiunto, se il dato è salvato.
- **Obiettivo nello storico:** mostra l'obiettivo della sessione, il progresso finale e la distanza rimasta al termine.

## 7. Personalizzazione e annunci vocali

- **Imposta soglia corsa/camminata:** modifica la velocità di riferimento per il passaggio alla corsa da 3 a 12 km/h, a passi di 0,5 km/h.
- **Attiva classificazione adattiva:** abilita l'adattamento del rilevamento corsa/camminata sul dispositivo.
- **Disattiva classificazione adattiva:** permette di usare il comportamento senza adattamento locale.
- **Campiona corsa:** comunica all'app, nella scheda Corsa e durante una sessione attiva, che l'utente sta correndo.
- **Campiona camminata:** comunica all'app, nella scheda Cammina e durante una sessione attiva, che l'utente sta camminando.
- **Attiva annunci vocali ai checkpoint:** abilita i messaggi vocali associati ai traguardi di distanza.
- **Disattiva annunci vocali ai checkpoint:** disabilita i messaggi vocali opzionali dei checkpoint.
- **Annuncio di pausa riconosciuta:** avvisa vocalmente quando viene rilevata una sosta dopo il movimento, se la voce è disponibile.
- **Annuncio di ripresa riconosciuta:** avvisa vocalmente al ritorno del movimento dopo una pausa annunciata.
- **Avviso vocale GPS impreciso:** può informare della scarsa affidabilità del segnale.
- **Voce italiana locale:** usa la sintesi vocale del telefono con lingua italiana, quando disponibile.
- **Priorità vocale sprint:** impedisce che gli annunci ordinari interrompano il conto alla rovescia o la prova sprint.

## 8. Prove sprint

- **Apri scheda Sprint:** accede ai controlli dedicati a prove brevi di velocità.
- **Imposta countdown sprint:** sceglie un conto alla rovescia da 5 a 10 secondi.
- **Seleziona sprint a tempo:** imposta una prova che si conclude dopo una durata scelta.
- **Imposta durata sprint:** accetta una durata da 5 a 120 secondi.
- **Seleziona sprint a distanza:** imposta una prova che si conclude al raggiungimento di una distanza scelta.
- **Imposta distanza sprint:** accetta un obiettivo da 10 a 500 metri.
- **Prepara la voce sprint:** verifica la disponibilità della sintesi vocale per il conto alla rovescia.
- **Avvia sprint:** inizia la preparazione e poi la misura della prova all'interno della sessione attiva.
- **Countdown vocale sprint:** comunica il conto alla rovescia prima dell'inizio.
- **Stato dello sprint:** indica preparazione, countdown, prova in corso, conclusione o annullamento.
- **Annulla sprint:** permette di interrompere la prova senza terminare l'intero allenamento.
- **Durata sprint:** mostra la durata effettivamente registrata per la prova.
- **Distanza sprint:** mostra i metri stimati durante la prova.
- **Velocità media sprint:** mostra la velocità media stimata se i campioni GPS validi sono sufficienti.
- **Velocità massima sprint:** mostra la massima velocità stimata dai dati GNSS validi.
- **Campioni GPS validi sprint:** mostra quanti campioni affidabili sono stati usati.
- **Avviso dati sprint insufficienti:** segnala quando non ci sono dati adeguati per una velocità media attendibile.
- **Elenco sprint nella sessione:** mostra le prove già effettuate, completate o annullate.
- **Storico sprint:** permette di rivedere le prove salvate nel dettaglio della sessione.

## 9. Storico e dati salvati

- **Elenco allenamenti:** mostra le sessioni memorizzate sul dispositivo.
- **Data e ora dell'allenamento:** mostra quando è iniziata ogni sessione.
- **Distanza dell'allenamento nello storico:** mostra i chilometri percorsi nella singola sessione elencata.
- **Durata dell'allenamento nello storico:** mostra il tempo trascorso nella singola sessione elencata.
- **Ritmo dell'allenamento nello storico:** mostra il ritmo medio associato alla sessione.
- **Passi dell'allenamento nello storico:** mostra il conteggio passi della sessione.
- **Stato della sessione nello storico:** distingue gli allenamenti completati da quelli in corso o interrotti.
- **Storico vuoto:** mostra un messaggio quando non è ancora stata registrata alcuna sessione.
- **Apri dettaglio allenamento:** visualizza le statistiche complete e il percorso di una sessione selezionata.
- **Statistiche storiche per corsa:** mostra i soli risultati attribuiti alla corsa nella sessione salvata.
- **Statistiche storiche per camminata:** mostra i soli risultati attribuiti alla camminata nella sessione salvata.
- **Statistiche storiche totali:** mostra la somma dei tratti classificati della singola sessione.
- **Dettaglio tempi storici:** mostra durata complessiva, movimento, corsa, camminata, fermo e periodi incerti.
- **Sezioni GPS storiche:** permette di selezionare i tratti del percorso salvato e leggere le loro misure.
- **Esporta sessione in JSON:** crea un file contenente statistiche, prove sprint e punti GPS della sessione selezionata.
- **Scegli destinazione esportazione:** apre il selettore file di Android per scegliere dove salvare il JSON.
- **Esporta coordinate GPS:** include latitudine e longitudine precise nel JSON creato su richiesta.
- **Elimina sessione:** rimuove dal dispositivo una sessione salvata, con i suoi dati collegati.
- **Conferma eliminazione:** mostra un avviso di irreversibilità prima di cancellare la sessione.
- **Annulla eliminazione:** chiude la richiesta di cancellazione senza eliminare i dati.
- **Proteggi sessione attiva:** impedisce di eliminare dallo storico una sessione ancora in registrazione.
- **Aggiorna elenco storico:** ricarica le sessioni quando l'utente torna alla schermata.

## 10. Meteo per correre

- **Apri impostazioni meteo:** mostra la configurazione delle città, dei criteri e degli orari di controllo.
- **Cerca città per il meteo:** ricerca una località tramite il servizio geografico online.
- **Visualizza risultati ricerca città:** mostra le località trovate per il nome inserito.
- **Aggiungi città per il meteo:** aggiunge una località selezionata all'elenco da monitorare.
- **Monitora più città:** permette di tenere in elenco fino a 20 città con gli stessi criteri e orari.
- **Evita città duplicate:** impedisce l'inserimento ripetuto della stessa località geografica.
- **Visualizza città monitorate:** mostra l'elenco delle località già scelte.
- **Rimuovi città dal meteo:** elimina una località dall'elenco da salvare.
- **Imposta temperatura minima:** richiede temperature previste almeno pari al valore scelto.
- **Imposta temperatura massima:** richiede temperature previste non superiori al valore scelto.
- **Imposta vento massimo:** richiede vento previsto non superiore al valore scelto in km/h.
- **Lascia criterio meteo libero:** consente di lasciare vuoto un campo per non applicare quella limitazione.
- **Combina condizioni meteo:** considera favorevole una previsione solo se rispetta tutti i criteri impostati.
- **Aggiungi orario controllo meteo:** definisce un nuovo orario quotidiano di verifica.
- **Modifica orario controllo meteo:** cambia uno degli orari impostati.
- **Rimuovi orario controllo meteo:** elimina un orario di verifica programmata.
- **Attiva notifiche meteo:** abilita i controlli automatici per le città e gli orari salvati.
- **Disattiva notifiche meteo:** sospende i controlli automatici programmati.
- **Salva impostazioni meteo:** memorizza città, soglie, orari e attivazione delle notifiche.
- **Aggiorna previsioni manualmente:** richiede dati meteorologici recenti per le città dell'elenco.
- **Visualizza previsioni orarie:** mostra temperatura e vento delle ore previste.
- **Indica ora meteo favorevole:** segnala le ore in cui sono rispettati i criteri scelti dall'utente.
- **Indica ora meteo sfavorevole:** segnala le ore in cui uno o più criteri non sono rispettati.
- **Riepilogo ore favorevoli:** mostra quante ore delle prossime 24 soddisfano le condizioni.
- **Notifica condizioni meteo favorevoli per la corsa:** invia un avviso locale quando una previsione prossima soddisfa i criteri selezionati.
- **Limita notifiche ripetute:** evita più di una notifica ogni 6 ore per ciascuna città.
- **Apri meteo da notifica:** collega la notifica alla schermata delle impostazioni meteo.
- **Controllo meteo senza GPS di background:** usa le città selezionate senza avviare la registrazione della posizione dell'utente.
- **Gestisci indisponibilità meteo:** mostra un errore o l'assenza di dati quando la connessione o il servizio non rispondono.

## 11. Aggiornamenti dell'app

- **Visualizza versione installata:** mostra versione e numero di build presenti sul dispositivo.
- **Seleziona canale debug:** sceglie il canale di aggiornamenti sperimentali disponibile nel progetto.
- **Seleziona canale stabile:** sceglie il canale delle release stabili, quando pubblicate.
- **Controlla aggiornamenti:** verifica online se è disponibile una build più recente sul canale selezionato.
- **Nessun aggiornamento disponibile:** informa quando non esiste una versione più recente valida.
- **Scarica APK:** avvia il download dell'aggiornamento selezionato tramite Android.
- **Mostra stato del download:** informa del progresso o dell'esito del download.
- **Riprendi verifica download salvato:** può recuperare le informazioni di un download precedentemente avviato.
- **Verifica integrità APK:** controlla l'impronta SHA-256 del file scaricato.
- **Verifica identità APK:** controlla pacchetto, versione e certificato di firma prima dell'installazione.
- **Rifiuta APK non valido:** impedisce l'installazione se i controlli di sicurezza non sono soddisfatti.
- **Richiedi autorizzazione installazione:** indirizza alle impostazioni Android quando occorre consentire l'installazione.
- **Richiedi installazione APK:** apre il flusso di conferma dell'installazione gestito da Android.
- **Blocca aggiornamento durante l'allenamento:** impedisce di scaricare/installare un aggiornamento mentre è attiva una sessione GPS.

## 12. Disponibilità dei dati, privacy e uso offline

- **Salvataggio locale degli allenamenti:** conserva statistiche, sprint e punti GPS nel database del telefono.
- **Salvataggio preferenze:** mantiene sul dispositivo le impostazioni di allenamento e meteo.
- **Allenamento senza Internet:** continua a misurare e registrare la sessione anche senza connessione dati, se posizione e sensori funzionano.
- **Consultazione storico offline:** permette di leggere le sessioni già salvate senza servizi cloud.
- **Annunci vocali offline:** può pronunciare i messaggi senza rete quando il dispositivo dispone di una voce italiana locale.
- **Mappe tramite OpenStreetMap:** carica la cartografia visualizzata da un servizio di tessere online quando serve.
- **Servizio meteo esterno:** usa Open-Meteo per ricerca località e previsioni; questa funzione richiede rete.
- **Nessun account allenamenti:** non richiede un profilo cloud per salvare e consultare sessioni.
- **Esportazione soltanto su richiesta:** non invia automaticamente i percorsi a un server dell'app.

## Funzionalità citate come esempio ma **non riscontrate**

- **Distanza totale di tutti gli allenamenti:** **non presente nell'interfaccia attuale**. La Home mostra la distanza della sessione attiva; lo storico riporta la distanza di ogni singola sessione. Non risulta una schermata o un contatore che sommi i chilometri di tutte le sessioni. Questa voce non va considerata funzionalità implementata.

## Fonti di verifica

- **Home e avvio:** [MainActivity.java](../app/src/main/java/it/example/passolibero/MainActivity.java), [TrackingService.java](../app/src/main/java/it/example/passolibero/TrackingService.java).
- **Statistiche, obiettivi e sprint:** [ActiveSessionActivity.java](../app/src/main/java/it/example/passolibero/ActiveSessionActivity.java), [SessionTabStats.java](../app/src/main/java/it/example/passolibero/SessionTabStats.java), [SprintTrial.java](../app/src/main/java/it/example/passolibero/SprintTrial.java).
- **Mappa e sezioni:** [TrackMap.java](../app/src/main/java/it/example/passolibero/TrackMap.java), [RouteSectionList.java](../app/src/main/java/it/example/passolibero/RouteSectionList.java).
- **Storico ed esportazione:** [HistoryActivity.java](../app/src/main/java/it/example/passolibero/HistoryActivity.java), [SessionDetailActivity.java](../app/src/main/java/it/example/passolibero/SessionDetailActivity.java), [SessionJsonExporter.java](../app/src/main/java/it/example/passolibero/SessionJsonExporter.java), [TrackerDb.java](../app/src/main/java/it/example/passolibero/TrackerDb.java).
- **Meteo:** [WeatherSettingsActivity.java](../app/src/main/java/it/example/passolibero/WeatherSettingsActivity.java), [WeatherMonitor.java](../app/src/main/java/it/example/passolibero/WeatherMonitor.java), [WeatherRules.java](../app/src/main/java/it/example/passolibero/WeatherRules.java).
- **Aggiornamenti:** [UpdateActivity.java](../app/src/main/java/it/example/passolibero/UpdateActivity.java), [UpdateManager.java](../app/src/main/java/it/example/passolibero/UpdateManager.java).

**Nota di verifica:** l'inventario deriva dall'analisi statica delle schermate, dei servizi e delle regole nel repository. Non sono stati eseguiti test su uno smartphone. Le voci che dipendono da sensori, GPS, sistema operativo e rete descrivono possibilità implementate, non prestazioni garantite in ogni contesto.
