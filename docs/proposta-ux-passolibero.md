# PassoLibero — proposta UX (bozza collaborativa v2.20 — nuovo allenamento senza obiettivo)

> **Stato:** bozza collaborativa v2.20. Le decisioni UX su Home, allenamento, GPS, Mappa, Storico, Meteo, Sprint, obiettivi e checkpoint sono riportate nei capitoli dedicati e riassunte in §15. **Ultima decisione confermata:** **ogni nuova sessione di allenamento parte senza obiettivo di distanza**, salvo scelta esplicita per quella sessione; il target della sessione precedente non viene riapplicato (§7.23). Se invece si **riprende una sessione già in corso**, conservarne l’obiettivo. Resta confermata la pagina unica «Opzioni allenamento» (§7.22) e la configurazione Sprint persistente. Le proposte documentate non sono ancora implementazioni; restano verifiche tecniche e su dispositivo.
> **Ambito:** esclusivamente UX (architettura informativa, navigazione, flussi, contenuti, stati, accessibilità), senza modifiche al codice.  
> **Base documentale:** [`docs/catalogo-funzionalita.md`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/docs/catalogo-funzionalita.md), revisione `cd27fc6b7656bef80bb6479484425c754a676448`, e analisi statica delle Activity Android allo stesso commit.  
> **Convalida:** non sono stati condotti test d'uso o su dispositivo; problemi e benefici qui descritti sono ipotesi UX da verificare.

## 1. Obiettivo

Rendere PassoLibero facile da avviare, consultare durante il movimento e rileggere dopo una sessione, mantenendo l'accuratezza dei dati e la trasparenza sui periodi non affidabili. L'app deve comunicare prima di tutto **cosa sta succedendo nel mio allenamento** e solo in secondo piano **come viene calcolato**.

## 2. Vincoli e carattere del prodotto

- Android nativo; uso in movimento, spesso con interazioni brevi e attenzione limitata.
- La registrazione parte dall'utente; localizzazione, sensori e TTS hanno permessi/disponibilità variabili.
- Corsa e camminata sono riconosciute automaticamente: esistono stati *fermo*, *movimento non classificato* e *movimento incerto*.
- Registrazione e storico locali, senza account; l'allenamento funziona offline. Nuove tessere mappa e previsioni meteo dipendono dalla rete.
- Obiettivi, checkpoint, sprint, apprendimento del classificatore, esportazione JSON, meteo e aggiornamenti esistono già a livello funzionale.
- **Non inventare una metrica cumulativa**: il totale chilometri di tutte le sessioni non risulta implementato. Non presentarlo come elemento attuale.
- **Non introdurre implicitamente un pulsante di pausa manuale**: la rilevazione delle soste è automatica; una pausa manuale costituirebbe una nuova funzione da valutare separatamente.

## 3. Audit UX dello stato attuale (da codice, non da test utenti)

| Area | Evidenza nell'implementazione analizzata | Ipotesi di attrito UX |
|---|---|---|
| Home | Riepilogo della sessione, avvio, accessi a storico/meteo/aggiornamenti, soglia del classificatore e interruttori nella stessa vista scrollabile | L'avvio compete visivamente con opzioni tecniche e servizi secondari |
| Sessione attiva | Quattro tab Corsa/Cammina/Totali/Sprint; numerose metriche in colonna, mappa, sezioni, checkpoint, obiettivo e azione di fine sessione dopo lo scroll | Troppe informazioni da elaborare durante il movimento; azione di fine sessione lontana dall'area principale |
| Dati «Totali» | I dati classificati come corsa e camminata restano distinti da quelli incerti | Etichette troppo generiche possono far sembrare identiche grandezze diverse |
| Sprint | Configurazione e prova in una delle quattro tab delle statistiche | L'avvio di un'attività è presentato allo stesso livello di un filtro dati |
| Storico | Ogni card espone sia il dettaglio sia «Elimina sessione» | L'azione distruttiva è prominente rispetto all'azione principale |
| Meteo | Ricerca città, soglie, orari, attivazione notifiche e previsioni nello stesso lungo form | L'utente deve configurare molti parametri prima di comprendere il risultato |
| Impostazioni avanzate | Soglia e adattamento della classificazione in Home | Concetti tecnici mostrati anche a chi vuole solo avviare una camminata |

## 4. Principi UX proposti

1. **Prima l'azione, poi il dettaglio:** un solo compito principale per schermata.
2. **Progettare per il movimento:** pochissimi elementi da leggere e toccare durante l'allenamento; comandi grandi, accessibili, sempre riconoscibili.
3. **Dati onesti:** rappresentare GPS impreciso, classificazione incerta e campioni mancanti senza costruire una falsa precisione.
4. **Progressive disclosure:** impostazioni e diagnostica disponibili su richiesta, non obbligatorie nel percorso principale.
5. **Continuità:** distinguere sessione attiva, servizio interrotto e sessione salvata; non far credere che la registrazione continui quando non è così.
6. **Privacy comprensibile:** esportazioni volontarie, dati locali e dipendenze di rete spiegati nei punti in cui sono pertinenti.

## 5. Architettura informativa target

- **Home:** avvio al centro quando non si registra, stato della sessione e azione di rientro quando si registra; accessi a Storico, Meteo e Impostazioni.
- **Allenamento attivo:** tempo, distanza, andatura/stato, qualità GPS, obiettivo/checkpoint, **annuncio vocale automatico del checkpoint con distanza raggiunta e tempo totale dall’inizio; se coincide con l’obiettivo, pronunciare entrambi (obiettivo prima, checkpoint dopo); se più checkpoint si accumulano durante Sprint, dopo il messaggio finale si legge solo l’ultimo checkpoint (tutti gli eventi restano registrati)**, accessi a mappa e statistiche; **comando Sprint grande e persistente nello stesso punto sia dalla vista Statistiche sia dalla Mappa live: tap per avviare, pressione di 2 s per annullare**, nella medesima zona inferiore; previa configurazione facoltativa, memorizzata anche tra sessioni e modificabile dalle opzioni secondarie durante l’allenamento solo se nessuna prova è in corso; termina l’allenamento con conferma.
- **Riepilogo finale:** al termine della registrazione, una vista di risultati essenziali **solo dopo salvataggio confermato**, con accesso alla mappa e ai dettagli; riutilizzabile anche dallo Storico.
- **Storico:** elenco cronologico inverso di schede spaziose con statistiche essenziali e **anteprima cartografica statica, con tracciato monocolore** del percorso; un tocco sulla scheda apre il riepilogo, dal quale si accede alla mappa navigabile, ai tratti, alle statistiche e all’esportazione.
- **Meteo:** consultazione di temperatura e vento per **una sola città alla volta**, anche senza notifiche; un selettore rapido consente di passare alle altre città salvate (fino a 20). Scelta iniziale della città se mancante; **due schermate separate «Le mie condizioni» e «Gestisci avvisi»** accessibili dalla consultazione. Senza soglie, non giudicare le ore come «favorevoli» e **non permettere l’attivazione degli avvisi**.
- **Opzioni allenamento (pagina unica):** da Statistiche e Mappa live un solo accesso secondario a «Obiettivo di distanza», «Checkpoint vocali» e «Configurazione Sprint». Accessi distinti, con riepiloghi e stati; modifiche non obbligatorie per iniziare a correre. I comandi immediati Sprint restano nelle viste operative, non nella pagina di configurazione (§7.22).
- **Impostazioni:** riconoscimento attività, feedback vocali, gestione aggiornamenti, informazioni su dati/privacy.

La navigazione può essere realizzata tramite tab bar o semplice navigazione gerarchica; la scelta del pattern visuale richiede mockup e verifica su device Android. La proposta definisce la struttura, non impone una libreria UI.

## 6. Home — specifica UX proposta (principi e avvio approvati)

### 6.1 Decisione di prodotto condivisa

PassoLibero serve sia a chi vuole iniziare una corsa o camminata senza configurazioni, sia a chi usa obiettivi, checkpoint, analisi e adattamento dell'attività. Non introdurre una scelta «principiante/esperto»: adottare la **rivelazione progressiva** delle funzionalità e uno stato della Home dipendente dalla sessione.

### 6.2 Obiettivi della schermata

1. Con sessione inattiva, permettere di identificare subito l'azione «Inizia allenamento».
2. Con sessione attiva, mostrare che la registrazione è in corso e permettere di rientrarvi subito.
3. Con servizio non aggiornato, evitare un falso stato «registrazione in corso» e rendere evidente il recupero.
4. Offrire accesso stabile a Storico e Meteo senza promuovere impostazioni tecniche nella zona principale.
5. Consentire personalizzazione mirata **prima** dell'allenamento senza imporre un setup obbligatorio.

### 6.3 Gerarchia — Home senza sessione

1. **Intestazione:** nome PassoLibero + accesso a «Impostazioni» mediante comando con etichetta accessibile.
2. **Messaggio di contesto:** «Pronto a muoverti?» / «Registra la tua prossima corsa o camminata». Evitare «GPS pronto» finché non è stato verificato.
3. **Azione primaria:** grande CTA «Inizia allenamento», senza obbligo di selezionare corsa o camminata, già riconosciute automaticamente.
4. **Personalizzazione secondaria:** per **ogni nuova sessione** la riga parte da **«Obiettivo: nessuno · Allenamento libero»**, senza riportare automaticamente il target di una sessione precedente. L’azione «Modifica» apre il pannello con valori già supportati: nessuno, 1/2/3/5/10 km, 21,1 km, personalizzato 0,1–200 km. Un target selezionato esplicitamente prima dell’avvio si applica a **quella** sessione; la scelta non deve intralciare chi vuole partire senza obiettivi. In caso di tentativo di avvio annullato per GPS/permessi, conservare la selezione pre-avvio per poter riprovare senza reinserirla (§7.23).
5. **Accessi secondari:** «Storico» e «Meteo» con peso visivo inferiore alla CTA primaria.
6. **Impostazioni:** spostare dalla Home soglia di classificazione, classificazione adattiva, annunci checkpoint, aggiornamenti e, nelle build debug, diagnostica batteria. Conservare la reperibilità, non necessariamente lo stesso posizionamento attuale.
7. **Checkpoint:** pagina dedicata «Impostazioni → Allenamento → Checkpoint vocali» con annunci attivi di default ogni 1 km, scelte rapide (250 m, 500 m, 1 km, 2 km, 5 km) e **distanza personalizzata**. Le opzioni rapide corrispondono agli intervalli già previsti nel catalogo; il valore libero è una novità UX da implementare e validare. Possibile accesso secondario anche durante la sessione, senza interrompere la registrazione.

**Decisione approvata — opzione A:** «Inizia allenamento» attiva direttamente i controlli di prerequisiti/GPS già esistenti, usando le preferenze correnti. La personalizzazione dell’obiettivo è facoltativa e si effettua prima del tap; nessuna schermata preparatoria obbligatoria. L’avvio non bypassa permessi, GPS o messaggi di sicurezza.

### 6.4 Gerarchia — Home con sessione in corso

Sostituire integralmente il blocco inattivo con un riepilogo **solo della sessione corrente**:

- Testo ben visibile «Allenamento in corso»; distanza e durata in primo piano.
- Stato attività in linguaggio quotidiano: «Stai correndo», «Stai camminando», «Sei fermo» oppure un testo neutro per stato non classificabile.
- Stato GPS esplicito, non segnalato solo con colore; quando è debole, spiegare che le misure possono essere meno attendibili.
- **Azione primaria:** «Torna all'allenamento»; nessuna seconda CTA «Inizia allenamento» contemporanea.
- «Storico» e «Meteo» rimangono accessibili come collegamenti secondari; il ritorno alla Home o l'apertura di altre sezioni **non** devono terminare il tracking.
- Il comando «Termina e salva» si trova nella vista della sessione e richiede conferma, non è un pulsante concorrente sulla Home.
- Per sessioni con un obiettivo, la visualizzazione del progresso in Home è **opzionale** per non duplicare troppo la pagina di allenamento.

### 6.5 Gerarchia — Home con tracciamento apparentemente interrotto

Quando la sessione è ancora segnata come attiva ma il servizio ha smesso di aggiornarsi:

- Banner prioritario «Il tracciamento sembra interrotto» con spiegazione breve e contestuale.
- **Azione primaria di recupero:** «Riprendi tracciamento», secondo la funzionalità esistente.
- Mostrare eventuali distanza e durata come **ultimi valori disponibili**, non come registrazione in tempo reale se non lo sono.
- Nessun conteggio fittizio del tempo in movimento o della distanza non osservati; evitare la parola «Pausa» perché non esiste una funzione di pausa manuale.
- Consentire accesso al dettaglio della sessione per vedere il contesto, senza mascherare l'interruzione.

### 6.6 Comportamento dell'azione «Inizia allenamento»

```text
Home inattiva
    ├─ «Modifica obiettivo» → scegli → ritorno alla Home
    └─ «Inizia allenamento»
         ├─ Posizione disattivata → spiegazione + Apri impostazioni
         ├─ Permesso mancante → richiesta contestuale Android
         └─ Verifica qualità GPS (fino a circa 12 s)
              ├─ Segnale affidabile → sessione attiva
              └─ Segnale debole → Riprova / Inizia comunque / Annulla
```

I controlli e le alternative riprendono funzionalità esistenti; la sequenza di presentazione potrebbe richiedere un riordino dell'interfaccia, senza cambiare il comportamento del GPS.

### 6.7 Microcopy e stati vuoti

| Stato | Testo principale | Azione primaria |
|---|---|---|
| Nessuna sessione | «Pronto a muoverti?» | «Inizia allenamento» |
| Verifica GPS | «Controllo la precisione del GPS…» | Attendere; offerta di alternative se la precisione resta scarsa |
| Sessione attiva | «Allenamento in corso» | «Torna all'allenamento» |
| GPS impreciso durante sessione | «GPS poco preciso: alcune misure potrebbero essere meno attendibili» | «Torna all'allenamento» |
| Movimento non attribuibile | «Tipo di movimento non determinato» | «Torna all'allenamento» |
| Servizio non aggiornato | «Il tracciamento sembra interrotto» | «Riprendi tracciamento» |

**Nota sulla durata:** mentre il tracking è attivo, la durata rappresenta il tempo trascorso dall'avvio; in caso di recupero non presentare intervalli non osservati come attività fisica classificata.

### 6.8 Wireframe testuale — organizzazione

```text
HOME INATTIVA                 HOME ATTIVA
┌──────────────────────┐      ┌──────────────────────┐
│ PassoLibero       ⚙  │      │ PassoLibero       ⚙  │
│                      │      │ ALLENAMENTO IN CORSO │
│ Pronto a muoverti?   │      │                      │
│                      │      │ 2,40 km     18:42    │
│ [ Inizia allenamento]│      │ Stai camminando      │
│                      │      │ GPS: buono           │
│ Obiettivo: nessuno > │      │                      │
│                      │      │ [Torna allenamento]  │
│ [ Storico ] [ Meteo ]│      │                      │
└──────────────────────┘      │ [ Storico ] [ Meteo ]│
                              └──────────────────────┘
```

I numeri sono *esemplificativi*; l'interfaccia deve essere testata con caratteri grandi e schermi piccoli. Il wireframe non prescrive componenti o palette.

### 6.9 Verifiche UX per la Home

- L'utente che apre l'app senza sessione individua l'avvio senza dover scorrere né scegliere «Corsa»/«Cammina».
- Si può avviare con le preferenze correnti senza passare da un form obbligatorio.
- La scelta dell'obiettivo è reperibile prima dell'avvio e non blocca il percorso semplice.
- Con sessione attiva non è presente un pulsante ambiguo che potrebbe iniziarne una nuova.
- Il riepilogo non confonde chilometri correnti e somma degli allenamenti storici, metrica non implementata.
- Lo stato di tracciamento apparentemente interrotto è distinto chiaramente da quello attivo.
- I controlli critici hanno dimensioni adeguate (minimo consigliato 48 × 48 dp) e non dipendono dal colore.
- Storico, Meteo e Impostazioni rimangono reperibili senza competere con l'azione primaria.

### 6.10 Decisione confermata: avvio immediato (A)

**Concordato nella progettazione UX:** il pulsante principale della Home avvia il *flusso* di registrazione con un solo tocco, non una configurazione obbligatoria. Questo **non** implica che il GPS inizi a registrare immediatamente: se servono permessi, abilitazione della posizione o verifica di precisione, la schermata segue i controlli necessari.

**Percorso desiderato:**

1. Dalla Home inattiva, l'utente può ignorare l'obiettivo oppure toccare «Modifica», scegliere un obiettivo e tornare alla Home.
2. Toccando «Inizia allenamento», si usa la configurazione corrente senza aprire una pagina di preparazione.
3. Durante i controlli, mostrare uno stato di avanzamento chiaro («Controllo GPS…») e impedire tocchi multipli che avviino flussi concorrenti.
4. Se posizione e permessi necessari sono disponibili e il GPS è attendibile, avviare la registrazione e aprire la schermata dell'allenamento.
5. Se la posizione è disattivata, spiegare come abilitarla; se manca il permesso di posizione precisa, presentare la richiesta Android pertinente.
6. Se entro circa 12 secondi il segnale non è affidabile, proporre esattamente tre alternative: «Riprova», «Inizia comunque» e «Annulla». L'opzione per continuare specifica il possibile impatto su percorso e velocità.
7. In caso di errore di avvio, non comunicare che la sessione è attiva; offrire un messaggio comprensibile e la possibilità di riprovare.
8. Quando la sessione è già attiva, l'azione principale diventa «Torna all'allenamento», mai un secondo avvio.

**Distinzione di prodotto:** «avvio immediato» significa *nessun setup superfluo*, non *nessun controllo preventivo*. La registrazione parte solo quando le condizioni del flusso lo consentono o quando l'utente sceglie esplicitamente di procedere con GPS debole.

### 6.11 Criteri di verifica mirati all'avvio

- **Happy path:** dalla Home a una sessione attiva con una sola intenzione esplicita di avvio, senza schermata di setup obbligatoria.
- **Obiettivo opzionale:** l'utente può configurarlo prima di iniziare, ma può anche non farlo senza essere bloccato.
- **Permessi / localizzazione:** ogni richiesta presenta il motivo e una via di ritorno comprensibile.
- **Controllo GPS:** compare feedback visibile; mentre è in corso non si creano avvii duplicati.
- **GPS debole:** decisione informata su riprova, continuazione o annullamento, senza forzare alcuna opzione.
- **Avvio fallito:** non mostra falsamente «Allenamento in corso».
- **Sessione già attiva:** l'azione primaria riapre l'allenamento esistente.

## 7. Allenamento attivo — specifica UX proposta (statistiche in primo piano approvate)

### 7.1 Decisione condivisa e obiettivo

**Decisione approvata — opzione A:** durante la registrazione, la schermata si apre con le **statistiche principali**, non con la mappa. La mappa live rimane raggiungibile con un tocco. Le funzioni avanzate usano esposizione progressiva, senza distinguere formalmente utenti «base» ed «esperti».

Il compito primario è consentire, con un'occhiata breve, di capire **quanto ho percorso, da quanto tempo mi sto allenando, a che ritmo sto procedendo e se la sessione sta davvero registrando**. Non richiedere di scorrere per trovare le informazioni fondamentali o il comando per terminare.

### 7.2 Gerarchia della prima vista

1. **Testata compatta:** «Allenamento» e stato testuale «Registrazione in corso». L'azione di navigazione «Home» riporta alla Home senza terminare il tracking. In presenza di errori, non mostrare impropriamente «Registrazione in corso».
2. **Stato contestuale:** «Stai correndo», «Stai camminando», «Sei fermo» oppure «Movimento in analisi» / «Movimento rilevato, tipo non determinato», secondo l'evidenza disponibile. Non trasformare intervalli incerti in corsa o camminata.
3. **Qualità GPS:** stato con testo e, se utile, icona. Il verde non basta da solo; un segnale impreciso deve avere una spiegazione sintetica collegata all'affidabilità delle misure.
4. **Metrica primaria grande:** **Distanza complessiva della sessione**, in km, esplicitamente diversa dalla somma dei soli tratti classificati nelle schede Corsa e Cammina. Non introdurre il totale chilometri di tutte le sessioni.
5. **Due metriche immediate:** **Durata complessiva trascorsa** e **ritmo attuale (min/km)**, come terza metrica principale concordata. La dicitura «attuale» deve essere visibile: non confonderla con il ritmo medio. Se la misura non è affidabile, mostrare un valore indisponibile, mai un numero inventato.
6. **Progresso facoltativo:** quando un obiettivo di distanza è impostato, barra di progresso e distanza residua; in assenza di obiettivo, non occupare spazio con una barra a zero. Prossimo checkpoint in forma compatta, evitando di scambiare checkpoint e obiettivo.
7. **Mappa e Dettagli** restano accessi secondari. **Sprint è un’azione rapida di primo piano**, non una scheda di approfondimento: quando esiste una configurazione pronta, mostrare un grande pulsante «Avvia sprint» sempre raggiungibile con un dito **sia in Allenamento sia nella Mappa live, nello stesso slot inferiore**. Una volta avviata la preparazione, **lo stesso pulsante, senza spostarsi, richiede una pressione di circa 2 secondi per annullare**. La vista «Mappa» apre una destinazione dedicata quasi a schermo intero, con statistiche essenziali sempre visibili.
8. **Aree d’azione persistenti e nettamente distinte:** «Avvia sprint» è grande e prioritario per il runner con configurazione pronta; «Termina allenamento» resta accessibile senza scroll, ma visivamente subordinato e separato, con conferma «Continua allenamento» / «Termina e salva». Non sovrapporre i due controlli né sacrificarne l’accessibilità su display piccoli.

**Nota di ergonomia:** su display piccoli o con font ingranditi, evitare che testata e footer fissi sottraggano tutta l'area utile; lo spazio delle metriche essenziali ha priorità. Valutare il layout su dispositivi Android reali.

### 7.3 Contenuti su richiesta

**Mappa:** apre una vista quasi a schermo intero (decisione approvata) con una barra compatta di distanza, durata e ritmo attuale sempre visibili. Percorso GPS live, posizione, tratti colorati e selezione bidirezionale mappa/elenco restano disponibili. Pan/zoom, modalità di seguimento ed esplorazione sono specificati nel §8. Le tessere offline non sono garantite; il tracking prosegue indipendentemente dalla visualizzazione cartografica.

**Opzioni allenamento (decisione confermata):** pagina unica, raggiungibile in modo secondario dalle viste Statistiche e Mappa live, con tre voci: **Obiettivo di distanza → Modifica obiettivo**, **Checkpoint vocali → pagina dedicata**, **Configurazione Sprint → Modifica sprint**. La pagina non contiene il grande comando d’avvio dello Sprint, che resta nel dock fisso delle viste operative. Durante countdown/prova il solo editor Sprint è indisponibile con motivo visibile. Al rientro nella sessione il contesto della vista originaria rimane invariato. Specifica in §7.22.

**Dettagli:** raggruppa statistiche per **sessione complessiva**, **corsa** e **camminata**, mantenendo esplicite le porzioni non classificate e incerte. Non usare «Totali» in modo ambiguo se il filtro somma soltanto i tratti classificati. Comprende ritmo medio, velocità media/attuale dove attendibili, passi, cadenza, tempi in movimento e fermo e informazioni sui tratti. Le funzioni «Insegna all'app: sto correndo/camminando» rimangono reperibili nei rispettivi contesti come strumenti avanzati.

**Sprint:** è un’**azione immediata** nelle due viste operative **Allenamento** e **Mappa live**, non una tab accanto alle statistiche. Le opzioni della prova (countdown 5–10 s; durata 5–120 s o distanza 10–500 m) si configurano **una volta** fuori dal momento di corsa e sono riutilizzate per gli sprint successivi. La Home può offrire «Opzioni allenamento → Sprint» prima di iniziare, senza renderlo un passaggio obbligatorio all’avvio della sessione. **Decisione approvata:** la stessa configurazione si può modificare anche durante un allenamento, tramite comando secondario «Modifica sprint», esclusivamente quando nessuna prova né countdown è in corso. Con configurazione pronta, un solo tocco su «Avvia sprint» lancia la preparazione/countdown esistenti, senza pagina intermedia né conferma. Durante preparazione/countdown/prova **la stessa grande superficie** cambia etichetta in «Tieni premuto 2 s per annullare sprint», senza cambiare posizione o dimensione; il singolo tap non annulla mai (e non avvia una seconda prova); alla conclusione il comando torna disponibile con gli stessi parametri. Misure e dettagli restano negli approfondimenti. Specifica completa in §7.10.

**Checkpoint:** **decisione condivisa**: ogni checkpoint effettivamente raggiunto genera normalmente un **annuncio vocale automatico breve con distanza raggiunta e tempo totale trascorso dall’inizio**, senza toccare lo schermo; **se più checkpoint vengono raggiunti durante uno Sprint si legge solo il più recente dopo la prova, ma si registrano tutti**; l'intervallo di checkpoint già configurabile resta in «Opzioni allenamento». Non si sovrappone mai alla voce dello Sprint: il checkpoint effettivamente raggiunto durante la prova è un evento di progresso prioritario da **recuperare dopo l'annuncio finale Sprint**, senza leggere anche gli altri messaggi ordinari saltati. Il prossimo checkpoint resta mostrato in forma compatta. Regole e casi limite in §7.11.

### 7.4 Stati e disponibilità dei dati

| Stato | Comunicazione UX | Comportamento atteso |
|---|---|---|
| Registrazione affidabile | «Registrazione in corso» + stato movimento e GPS | Metriche leggibili e accesso agli approfondimenti |
| GPS impreciso | «GPS poco preciso» e indicazione che ritmo/percorso possono essere meno attendibili | Non nascondere l'avviso dietro una semplice icona |
| Ritmo attuale indisponibile | «--:-- min/km» (oppure «Ritmo non disponibile» in una visualizzazione compatta) | Non mostrare 0:00, il valore precedente come se fosse corrente o il ritmo medio sotto l’etichetta «attuale» |
| Movimento non classificato | «Ti stai muovendo · andatura non determinata» | Non imputare arbitrariamente il tratto a corsa o camminata |
| Movimento incerto | «Movimento in analisi» | Distinguere dall'effettiva sosta |
| Sosta riconosciuta | «Sei fermo» | Non presentare una pausa manuale: non è una funzione attualmente implementata |
| Tracciamento non aggiornato | «Il tracciamento sembra interrotto» + possibilità di ripristino | Segnalare che i valori sono gli ultimi noti; non inventare distanza o attività per il tempo non osservato |
| Dati GPS o sensori mancanti | Valori non disponibili con spiegazione contestuale | Conservare informazioni affidabili e non bloccare la lettura delle altre metriche |
| Sprint in countdown o in corso | Stato di prova dedicato e **stessa superficie grande** «Tieni premuto 2 s per annullare sprint» | Solo una pressione continua di ~2 s annulla la prova; nessun tap singolo annulla, nessun cambio di posizione |

### 7.5 Wireframe testuale — prima vista, valori fittizi

```text
┌──────────────────────────────────┐
│ ← Home               ● Registra  │
│                                  │
│  🏃 Stai correndo     GPS buono   │
│                                  │
│     DISTANZA COMPLESSIVA         │
│             3,42 km              │
│                                  │
│    DURATA       RITMO ATTUALE    │
│    24:18        6:12 min/km      │
│                                  │
│  Obiettivo 5 km — 68%            │
│  █████████████░░░░░░             │
│  Mancano 1,58 km                 │
│  Checkpoint tra 580 m            │
│                                  │
│  [ Mappa ] [ Dettagli ]          │
│                                  │
│     [ ⚡ AVVIA SPRINT ]           │
│       50 m · Countdown 5 s      │
│                                  │
│ ──────────────────────────────── │
│    [ Termina allenamento ]       │
└──────────────────────────────────┘
```

Il wireframe mostra la **gerarchia**, non stabilisce pixel, colori, font, disposizione definitiva o una funzionalità di pausa. La voce «Registra» significa uno **stato di registrazione**, non un nuovo pulsante. Dati e progressi sono dimostrativi.

### 7.6 Interazioni principali

- **Da Home dopo il controllo GPS:** apertura diretta della vista statistiche.
- **Home durante la sessione:** ritorno alla Home con sessione ancora attiva e percorso di rientro chiaramente esposto.
- **Tocco su Mappa:** apre/esplora il percorso; la registrazione continua.
- **Tocco su Dettagli:** mostra filtri e dati avanzati, senza modificare implicitamente la classificazione registrata.
- **Tocco su «Avvia sprint» con configurazione pronta (da Allenamento o Mappa live):** avvia direttamente la preparazione del countdown; non apre una pagina di configurazione o una richiesta di conferma, non interrompe il tracking principale e non obbliga a cambiare vista. **Durante la prova, sulla stessa superficie un tap breve non ha effetto; tenere premuto continuativamente per circa 2 secondi annulla solo lo sprint, con avanzamento visivo della pressione.** Se la configurazione non esiste, compare «Configura sprint» come percorso esplicito da completare **prima** dell’uso rapido.
- **Tocco su Termina allenamento:** apre un dialogo con conseguenza esplicita («La sessione verrà salvata nello storico»), «Continua allenamento» come uscita sicura e «Termina e salva» come conferma.
- **Esito del termine:** schermata/riepilogo post-sessione da progettare separatamente; non dichiarare salvata una sessione prima dell'esito effettivo.

### 7.7 Accessibilità e criteri di verifica

- Distanza complessiva, durata e **ritmo attuale (min/km)** leggibili nella prima vista senza scroll; il ritmo ha un fallback testuale per dato assente o non affidabile.
- Nessuna azione di termine definitiva con un singolo tocco e nessuna necessità di percorrere l'intera schermata per trovarla.
- Touch target per azioni essenziali almeno 48 × 48 dp; per **l’unica superficie Sprint, sia avvio sia annullamento a pressione**, puntare a un’area ben più grande (proposta iniziale: almeno 72 dp di altezza e larghezza pressoché piena), contrasto alto e posizione **assolutamente stabile** fra viste e stati. Validare con telefono fissato al braccio, uso con un solo dito, movimento e scala font elevata.
- Stato GPS, movimento e registrazione leggibili anche senza percezione dei colori; icone con etichette accessibili.
- L'aggiornamento frequente delle metriche non genera annunci continui indesiderati per TalkBack. La priorità degli annunci vocali dello sprint rimane rispettata; la progressione dei 2 s deve essere accessibile anche senza affidarsi a un cambiamento di colore.
- La consultazione di mappa, dettagli o sprint non interrompe la sessione principale; cambiare tra Allenamento e Mappa non ripristina né duplica lo sprint.
- Nessuna nuova funzione di pausa manuale o navigazione pericolosa viene implicitamente introdotta.

### 7.8 Decisione confermata: ritmo attuale come terza metrica principale

**Concordato:** nella vista principale di allenamento, i tre dati prioritari sono **distanza complessiva**, **durata complessiva** e **ritmo attuale** espresso in **min/km**. Il ritmo medio e la velocità (km/h) rimangono nei «Dettagli» per chi vuole approfondire; non aggiungere un selettore obbligatorio di unità o modalità per l’utente principiante.

**Specifiche di visualizzazione UX:**

- Etichetta esplicita **«Ritmo attuale»** e unità **«min/km»** sempre associata al valore, ad esempio `6:12 min/km`. Valori dimostrativi, non prestazioni attese.
- Se il dato diventa temporaneamente non affidabile (GPS debole, velocità non valida, assenza di misura corrente), usare `--:-- min/km` e un messaggio di supporto contestuale, come «Ritmo non disponibile: GPS poco preciso». Evitare di usare `0:00` per «dato assente».
- Se il telefono riconosce una sosta, lo stato «Sei fermo» prevale sull’idea di andatura corrente; non mostrare un vecchio ritmo come se descrivesse l’istante presente. Il modo esatto di rendere l’indisponibilità deve essere verificato nel prototipo con utenti e dispositivi reali.
- Se il movimento è incerto o non classificato, non dedurre automaticamente «corsa» o «camminata» dal ritmo. La classificazione e il ritmo misurano aspetti differenti.
- Il **ritmo medio** non sostituisce automaticamente quello attuale nella posizione principale. Può comparire nei dettagli con la sua etichetta distinta, insieme alla velocità attuale o media.
- Testare comprensibilità di «min/km» con chi cammina: l’unità resta confermata come scelta principale, mentre eventuali spiegazioni contestuali («minuti per chilometro») sono un dettaglio UX da validare.

**Criteri di accettazione:** in registrazione affidabile il ritmo attuale è leggibile senza scorrimento; con dato assente non compare un valore ingannevole; con GPS impreciso è comprensibile il motivo dell’indisponibilità; le altre statistiche restano accessibili nei dettagli.

### 7.9 Decisione confermata: mappa dedicata quasi a schermo intero

**Concordato — opzione A:** «Mappa» apre una vista dedicata che privilegia il percorso GPS, mantenendo una barra compatta di **distanza complessiva, durata complessiva e ritmo attuale (min/km)**, stato GPS e ritorno esplicito ad «Allenamento». La sessione continua a essere registrata. Per modello delle interazioni, tratti, stato offline e criteri di verifica, vedere **§8 — Mappa GPS live**.

### 7.10 Decisione confermata: Sprint «pronto, grande, un tocco»

**Scenario d’uso vincolante:** l’utente sta correndo con il telefono fissato al braccio e lo schermo acceso. Vuole avviare una prova Sprint premendo **un solo pulsante con un solo dito**, senza leggere un modulo, scegliere una modalità o attendere una conferma prima che parta la preparazione.

**Decisione UX condivisa:** separare nettamente *configurare* (raramente) da *avviare* (durante la corsa). L’ipotesi precedente di una schermata Sprint dedicata da aprire per l’avvio è **superata**. Può esistere una pagina di **configurazione Sprint**, ma non è il percorso dell’azione rapida.

#### 7.10.1 Configurazione una sola volta, riutilizzabile

- Presentare «Configura sprint» in **Home → Opzioni allenamento → Sprint**, senza rendere obbligatorio il setup prima di «Inizia allenamento». Mantenere quindi **l’avvio immediato dell’allenamento già approvato**.
- La configurazione offre **Sprint a tempo** (da 5 a 120 secondi), **Sprint a distanza** (da 10 a 500 metri) e **countdown** da 5 a 10 secondi; valori già supportati dalla logica attuale. Non aggiungere nuovi modi di prova in questa PR UX.
- Dopo «Salva configurazione sprint» mostrare il riepilogo sintetico «Sprint pronto · 50 m · Countdown 5 s» (valori di esempio). La prova non parte in questo momento.
- **Decisione approvata:** una volta salvata sul telefono, la configurazione Sprint resta disponibile **anche tra allenamenti diversi**. Tutte le prove della stessa sessione e delle successive riusano l’ultimo preset valido, salvo modifica esplicita tramite «Modifica configurazione Sprint». Nell’implementazione analizzata i parametri sono invece scelti nella tab Sprint; **la persistenza del preset tra sessioni è un comportamento futuro richiesto dalla UX**, non una funzione già verificata.
- Se una sessione inizia senza una configurazione Sprint, le statistiche e il tracking funzionano normalmente. Mostrare un accesso «Configura sprint» con testo esplicito, **non** un «Avvia sprint» apparentemente pronto ma inattivo senza spiegazione. La configurazione fatta in sessione richiede una situazione sicura e non è progettata per l’uso durante la corsa.

#### 7.10.2 Comando in Allenamento e Mappa live: massima facilità di tocco

- **CTA primaria contestuale:** pulsante **«⚡ AVVIA SPRINT»** a contrasto elevato, piena larghezza utile o quasi, altezza indicativa **≥72 dp**, ben distanziato dalle altre azioni e sempre nello **stesso punto della vista Allenamento e della vista Mappa live** (dock condiviso e persistente nella zona inferiore, rispettando l’area di navigazione Android). La dimensione è una proposta da testare su dispositivi, non una misura validata.
- La CTA è visibile senza scorrere, distinguibile per **testo + forma + contrasto** e con una breve riga di configurazione («50 m · Countdown 5 s»). Non basarsi su una piccola icona, su una tab, su un menu o su un gesto nascosto.
- **Un tap = richiesta di avvio Sprint**: nessuna seconda pagina, finestra modale, doppio tap o pressione prolungata. Il comando attiva la preparazione del countdown prevista dal sistema; l’inizio della *misurazione* resta alla fine del countdown, non al momento del tap.
- Feedback immediato e chiaramente percepibile al tocco (stato «Preparazione countdown…», poi voce/countdown). **Feedback aptico facoltativo proposto**, da verificare come miglioramento UI non garantito dall’implementazione corrente; non sostituire mai lo stato testuale/sonoro.
- **«Termina allenamento» resta accessibile senza scroll**, ma visivamente secondario e fisicamente separato da «Avvia sprint», con la conferma di fine sessione già decisa. Evitare vicinanza che favorisca tocchi errati.
- **Decisione confermata — Sprint nella Mappa live:** esporre il medesimo comando grande anche nella vista Mappa, senza entrare nelle tab e senza tornare alla vista statistiche. Mantenerne **posizione, dimensione, etichetta, preset e stato** coerenti con Allenamento; riservargli spazio nel layout affinché non copra la traccia, i controlli mappa o la gesture di sistema. La scelta di presenza è approvata; misure finali e disposizione concreta restano da testare sul braccio con device reali.

#### 7.10.3 Macchina a stati della CTA: **tap per avviare, pressione continua di ~2 s per annullare**

**Decisione condivisa — variante B modificata:** non esiste un secondo pulsante di annullamento durante la prova. **La stessa superficie grande resta esattamente nello stesso punto dello schermo e cambia soltanto funzione e contenuto**, con una distinzione gestuale che riduce gli annullamenti involontari: un tap quando lo sprint è pronto avvia; durante preparazione, countdown e prova attiva, solo una **pressione continua di circa 2 secondi** annulla.

| Stato | Stessa superficie Sprint fissa | Gesto ed esito |
|---|---|---|
| Non configurato | «Configura sprint» + spiegazione | Un tap apre la configurazione (non avvia una prova) |
| Pronto | **«⚡ AVVIA SPRINT»** + preset corrente | **Un tap** avvia la preparazione, senza ulteriore conferma |
| Preparazione voce | **«Tieni premuto 2 s per annullare»**, stato «Preparo countdown…» fuori dal pulsante | Un tap non fa nulla; pressione continua completa annulla la prova/preparazione |
| Countdown | **«Tieni premuto 2 s per annullare»**, stato «Sprint tra 5…» sopra al dock | Un tap non fa nulla; tenuta completa annulla solo lo sprint |
| Sprint in corso | **«Tieni premuto 2 s per annullare»**, stato «Sprint in corso» sopra al dock | Un tap non fa nulla; tenuta completa annulla solo lo sprint |
| Prova conclusa | «Sprint completato» + riepilogo visivo temporaneo fuori dal dock e voce breve, poi **«⚡ AVVIA SPRINT»** nello stesso punto | Nessuna nuova schermata; nuovo sprint con il medesimo preset e nuovo tap; vedere §7.10.9 |
| Prova annullata | «Sprint annullato · allenamento continua», poi **«⚡ AVVIA SPRINT»** nello stesso punto | Nuovo sprint possibile solo con un **nuovo** tap |
| Tracciamento non operativo | Stato del servizio/GPS esplicito | Non promettere avvio di prove quando la sessione non è attiva |
| Voce indisponibile/errore | Stato comprensibile e recupero appropriato | Non confermare falsamente l'avvio; comportamento alternativo da verificare su device |

**Semantica precisa della pressione di annullamento:**

1. Quando il dito tocca il pulsante in fase annullabile, la superficie mostra subito un **progresso di tenuta da 0 a 2 secondi**, ad esempio un bordo progressivo o una barra integrata. Il layout non cambia dimensione né sposta gli altri controlli.
2. **Rilascio anticipato, scorrimento fuori dall'area utile o gesto interrotto:** il progresso torna a zero e lo sprint **prosegue**. Il semplice tap non annulla, non mette in pausa e non riavvia nulla.
3. **Due secondi continuativi raggiunti:** la richiesta «Annulla sprint» viene inviata una sola volta, senza ulteriori finestre. Feedback visivo «Sprint annullato», eventuale breve segnale aptico/audio, quindi ripristino dello stato «Avvia sprint» nello stesso spazio.
4. Non trasferire mai automaticamente il gesto di avvio al gesto di annullamento: **l'avvio avviene al rilascio del tap** e l'annullamento richiede una nuova pressione iniziata dopo il cambio di stato. Dopo una tenuta conclusa, il rilascio non deve diventare un nuovo tap «Avvia sprint».
5. Se la prova termina naturalmente prima della soglia di pressione, **non annullare uno sprint già concluso**; interrompere la progressione e aggiornare la superficie al nuovo stato senza generare un nuovo avvio.
6. Il conteggio dei 2 secondi non dipende dal refresh delle metriche, dalla navigazione né dal countdown. Il tempo è misurato in modo coerente; **cambiando vista durante una pressione in corso, il gesto viene interrotto senza annullamento**, mentre lo stato dello sprint continua correttamente.

**Continuità dell’allenamento:** l’annullamento agisce soltanto sulla prova Sprint; il tracking generale prosegue e «Termina allenamento» rimane un comando separato e protetto da conferma. Nessuna «Pausa» manuale viene introdotta.

**Affidabilità:** ritmo/velocità sprint sono stime GNSS; quando mancano campioni affidabili, mostrare «Dati insufficienti». Il sistema deve evitare doppi comandi anche quando l'utente tocca ripetutamente il pulsante o passa dalla mappa alle statistiche.

#### 7.10.4 Wireframe testuale — modalità «Pronto» (valori inventati)

```text
┌────────────────────────────────────┐
│ ← Home                   GPS buono │
│        ALLENAMENTO IN CORSO       │
│                                    │
│             3,42 km                │
│      24:18      6:12 min/km       │
│                                    │
│ [ Mappa ]         [ Dettagli ]     │
│                                    │
│  ┌──────────────────────────────┐  │
│  │      ⚡ AVVIA SPRINT          │  │  ← sempre visibile
│  │       50 m · 5 s             │  │  ← preset, non un form
│  └──────────────────────────────┘  │
│                                    │
│        Termina allenamento         │  ← separato, conferma
└────────────────────────────────────┘
```

**Wireframe della stessa posizione durante countdown e prova — non una seconda CTA:**

```text
┌────────────────────────────────────┐
│ 3,42 km · 24:18 · 6:12 min/km      │
│                                    │
│         SPRINT IN CORSO            │
│                                    │
│ ┌────────────────────────────────┐ │
│ │    TIENI PREMUTO PER ANNULLARE │ │ ← identico dock, 72 dp o più
│ │    Tieni 2 s · █████░░░░░       │ │ ← avanzamento della pressione
│ └────────────────────────────────┘ │
└────────────────────────────────────┘
```

La barra rappresenta la **durata della tenuta**, non il progresso della prova. Se il dito viene rilasciato prima del completamento, si azzera senza annullare. La posizione della superficie Sprint è la stessa della modalità «Avvia sprint» e resta uguale in **Allenamento** e **Mappa live**.

#### 7.10.5 Criteri di accettazione e prove UX

1. Con Sprint configurato, sia da **Allenamento** sia da **Mappa live** parte il countdown con **un solo tap**, **senza cambio schermata** o conferme intermedie.
2. La CTA rimane individuabile e raggiungibile **con il telefono fissato al braccio** e un solo dito, senza scroll o lettura di testo minuto. Verificare con schermi diversi, orientamenti/montaggi reali e impostazioni di accessibilità.
3. Il feedback dopo il tap arriva subito **in entrambe le viste**; il pulsante non permette di avviare due sprint per tocchi ripetuti o cambi di vista.
4. Durante preparazione, countdown e misurazione la **stessa superficie grande** diventa «Tieni premuto 2 s per annullare»; un tap breve non annulla; una tenuta continua di circa 2 s annulla **solo** lo sprint, senza conferma aggiuntiva. Feedback di avanzamento e rilascio anticipato senza effetto sono verificabili.
5. Dopo completamento/annullamento è possibile avviare un’altra prova con **gli stessi parametri**, senza configurare nuovamente; **anche iniziando una nuova sessione**, se esiste un preset salvato, il pulsante deve risultare pronto con gli stessi valori.
6. «Termina allenamento» è sempre raggiungibile ma distinto e protetto da conferma.
7. Mancanza voce/GPS/permessi o insufficienza dei campioni non generano falsa precisione o falsa conferma di avvio.
8. L’avvio della **sessione generale** rimane immediato anche per chi non usa Sprint.
9. Il pulsante Sprint rimane **nello stesso identico slot, con dimensioni e allineamento invariati** tra vista statistiche/Mappa e tra «Avvia»/«Tieni premuto 2 s per annullare»; non scorre con il contenuto, non salta al cambio di stato e non occlude percorso, controlli o navigazione Android.
10. Il passaggio Allenamento ↔ Mappa durante countdown/prova mantiene fase, durata, preset e controlli; il tap ripetuto o il rientro dopo il cambio vista non può generare un secondo avvio. Una tenuta interrotta dal cambio vista non annulla la prova.
11. Sulle pagine non operative (Storico, riepilogo concluso, Dettaglio tratto GPS) non compare una CTA che possa avviare uno Sprint, anche se la sessione è attiva in background.
12. Con TalkBack e tecnologie assistive, la richiesta di annullamento deve avere **etichetta e azione accessibili**, con un’alternativa utilizzabile alla tenuta continua da specificare e provare; non presumere che tutti possano eseguire una pressione prolungata precisa.
13. Una tenuta di annullamento completata non genera un nuovo avvio al rilascio, e una tenuta in corso al termine naturale dello sprint non cambia retroattivamente un esito già salvato.

**Tracciabilità:** `ActiveSessionActivity.java` offre oggi countdown, modalità/durata/distanza e «Sprint»/«Annulla sprint» nella tab dedicata; `SprintTrial.java` gestisce `IDLE/COUNTDOWN/ACTIVE/FINISHED/CANCELLED`, impedisce l’avvio mentre la prova è occupata e conclude le prove a target; `TrackingService.java` gestisce la prova mantenendo il tracking generale. **La CTA persistente, la pressione continua di ~2 s per annullare e la conservazione automatica del preset tra allenamenti sono modifiche UX proposte, non caratteristiche già implementate.**


#### 7.10.6 Decisioni confermate: stesso dock nelle due viste (A) e pressione lunga per annullare (B)

**Decisione approvata:** la superficie **«⚡ AVVIA SPRINT» è sempre visibile senza scroll**, nella **stessa posizione della parte inferiore**, tanto nella vista **Allenamento / statistiche** quanto nella **Mappa GPS live** durante una sessione attiva. Il comando non richiede mai di aprire la tab Sprint, passare da Mappa a Statistiche o richiamare un menu. La **configurazione della prova resta separata**, come già stabilito in §7.10.1.

**Regole di layout e comportamento:**

1. **Componente condiviso:** etichetta, dimensione, preset e stato provengono dalla **stessa sessione**; non usare due pulsanti con logiche indipendenti o due contatori locali. La CTA pronta rimane larga e ad alto contrasto in entrambe le viste.
2. **Posizione invariabile (decisione confermata):** riservare un **dock inferiore** fisso rispetto alla stessa area utile sopra la barra di navigazione/gesti di Android. **Stesse coordinate logiche, larghezza, altezza e margini** in Statistiche e Mappa, in stato pronto, countdown, prova, annullata o completata. Le etichette e la barra della tenuta si adattano **dentro** la superficie, senza cambiare geometria. Il contenuto principale scorre o si ridimensiona sopra: la CTA non copre polilinee, «Seguimi», «Mostra percorso», elenco tratti o statistiche essenziali.
3. **Una sola azione:** quando pronta, un singolo tocco invia la richiesta di avvio della prova configurata, quindi la CTA passa subito allo stato «Tieni premuto 2 s per annullare» e mostra «Preparazione countdown…» come stato contestuale; non consentire doppi avvii da tocchi ravvicinati o da cambio di vista. Il countdown di 5–10 secondi precede l'avvio misurato.
4. **Fase condivisa e stabile:** preparazione, countdown, prova attiva, completata/annullata sono mostrati anche quando l'utente naviga tra Mappa e Statistiche. Una vista che torna in primo piano legge lo stato effettivo della sessione; non azzera il conto alla rovescia.
5. **Stessa area, gesto diverso (decisione confermata B):** durante preparazione/countdown/prova **non compare un secondo pulsante**; il grande comando già usato per «Avvia sprint» cambia etichetta in **«Tieni premuto 2 s per annullare»**. Il tap breve non fa nulla. Il progresso visivo della tenuta dà riscontro immediato; il rilascio prematuro annulla solo la tenuta, non la prova. Al raggiungimento dei ~2 s viene chiesto l’annullamento una sola volta, senza ulteriore conferma. **Dimensione, posizione e allineamento del dock non cambiano.**
6. **Fine allenamento separata:** l'azione «Termina allenamento» non condivide il dock Sprint ed è protetta dalla conferma già approvata. Dalla Mappa resta possibile tornare ad Allenamento per terminarlo; non sacrificare uno spazio vicino alla CTA Sprint a un comando distruttivo.
7. **Quando non è configurato:** non mostrare un finto «Avvia sprint» disabilitato; usare il testo «Configura sprint», ben distinto dall'avvio rapido. L'utente può farlo in un contesto sicuro, ma non si richiede configurazione al momento di correre.
8. **Perimetro:** il dock Sprint si applica **solo ad Allenamento e Mappa live**; non è presente su riepilogo concluso, Storico, mini-mappe dello Storico o pagina Dettaglio tratto GPS.
9. **Accessibilità e prova sul campo:** verificare con telefono sul braccio, un dito, schermo acceso, diversi orientamenti e dimensioni, font aumentati, luce elevata simulata senza rischi e vicinanza ai gesti di sistema. L'altezza **≥72 dp** e la tenuta **~2 s** sono **ipotesi di partenza da validare**. Progettare anche un percorso accessibile per annullare senza richiedere necessariamente la tenuta a chi usa tecnologie assistive; non aggiungere un secondo pulsante standard in corsa.

**Wireframe testuale — stessa zona operativa, numeri fittizi:**

```text
  ALLENAMENTO                        MAPPA LIVE
┌──────────────────────┐        ┌──────────────────────┐
│ Distanza: 3,42 km    │        │ 3,42 km · 24:18      │
│ Durata: 24:18        │        │ Ritmo: 6:12 min/km   │
│ Ritmo: 6:12 min/km   │        ├──────────────────────┤
│ Obiettivo / dettagli │        │      MAPPA GPS       │
│                      │        │ Segui · Tratti       │
├──────────────────────┤        ├──────────────────────┤
│  ⚡ AVVIA SPRINT      │        │  ⚡ AVVIA SPRINT      │
│  50 m · 5 s          │        │  50 m · 5 s          │
└──────────────────────┘        └──────────────────────┘
        STESSO DOCK INFERIORE, STESSA AZIONE
```

**Impatto sulla proposta:** serve un intervento di UI/navigazione futuro; l'implementazione analizzata mostra oggi i controlli Sprint nella tab di `ActiveSessionActivity.java` e la mappa nello scorrimento della stessa Activity. **La persistenza del dock tra due viste non è una funzione già realizzata.**


#### 7.10.7 Decisione confermata: conservare il preset Sprint fra sessioni diverse

**Decisione approvata:** il preset Sprint è una **preferenza dell’utente memorizzata localmente sul dispositivo**, non una scelta da ripetere a ogni sessione. La modifica rimane facoltativa e separata dall’azione rapida. Questa modifica è **da implementare**, non è attestata nel comportamento corrente del codice Android analizzato.

**Contenuto del preset:**

- Modalità «A tempo» (5–120 secondi) oppure «A distanza» (10–500 metri).
- Durata o distanza target in base alla modalità scelta.
- Countdown vocale configurato fra 5 e 10 secondi.

**Flusso UX richiesto:**

1. **Prima configurazione:** da «Home → Opzioni allenamento → Sprint» l’utente definisce i tre parametri e preme «Salva configurazione Sprint». La sessione generale può essere avviata senza usare questa schermata.
2. **Conferma:** mostrare «Sprint configurato: 50 m · countdown 5 s» (esempio) e tornare al contesto di provenienza. Il salvataggio della configurazione **non avvia una prova**.
3. **Sessioni successive:** se è presente un preset completo e valido, in Allenamento e nella Mappa live lo stesso pulsante fisso mostra **«⚡ AVVIA SPRINT»** e il breve riepilogo del preset, senza chiedere nuovamente le impostazioni.
4. **Più prove:** dopo la conclusione o l’annullamento di uno sprint, il preset rimane invariato; il pulsante torna nella posizione usuale, pronto per una nuova prova con un tap.
5. **Modifica esplicita:** il comando secondario «Modifica configurazione Sprint» è disponibile nelle opzioni; la modifica è accessibile anche durante la sessione da «Opzioni → Modifica sprint», senza interferire con il gesto principale, ed è disabilitata durante preparazione, countdown e sprint attivo. Dopo il salvataggio il nuovo preset sarà usato per le prove successive, inclusi gli allenamenti futuri.
6. **Prima configurazione assente:** non inventare un default approvato. Nell’area fissa operativa indicare **«Configura sprint»** con spiegazione sintetica; non mostrare un «Avvia sprint» non funzionante. Chi non usa Sprint continua a registrare normalmente.
7. **Preset non leggibile/non valido:** non avviare una prova con parametri inventati né mostrare «Pronto». Mostrare «Configura sprint» e invitare a correggere o ripristinare esplicitamente le impostazioni. Un nuovo preset valido sostituisce quello precedente.

**Criteri di accettazione:**

- Salvando una configurazione, chiudendo la sessione e avviandone un’altra, **modalità, target e countdown** sono ancora quelli scelti e la CTA Sprint è già pronta in entrambe le viste operative.
- La configurazione rimane sul dispositivo dopo la chiusura e riapertura dell’app, salvo eliminazione dei dati dell’app o modifica esplicita della preferenza; non serve account o connettività.
- Avvio della sessione di allenamento e consultazione delle statistiche restano possibili anche senza preset Sprint.
- Nuova configurazione valida sovrascrive quella precedente **solo dopo il salvataggio**; annullare l’editing lascia invariato il preset attuale.
- Una volta avviata una prova, il gesto di annullamento tramite pressione continua di ~2 s e la posizione fissa del dock restano invariati, indipendentemente da quando il preset è stato creato.

**Nota implementativa di tracciabilità:** il catalogo funzionale riporta oggi i controlli Sprint durante la sessione e non conferma il riuso automatico di una configurazione tra sessioni. La PR è una **specifica UX senza modifiche a preferenze, database o UI**: la persistenza dovrà essere sviluppata e verificata separatamente.

#### 7.10.8 Decisione confermata: modifica del preset anche durante la sessione (B)

**Decisione condivisa:** la configurazione Sprint si può modificare **prima o durante un allenamento**, ma **soltanto quando nessuna prova è in corso**. La modifica non deve occupare, sostituire, spostare o ridimensionare il grande comando Sprint, che resta nel suo dock inferiore fisso anche nella Mappa live.

**Accesso e comportamento:**

1. **Fuori dall'allenamento:** «Home → Opzioni allenamento → Sprint» conserva l'accesso alla configurazione e al preset locale persistente.
2. **Durante l'allenamento, Sprint pronto o concluso/annullato:** comando secondario «Opzioni → Modifica sprint», raggiungibile dalla vista Allenamento e, tramite le opzioni della sessione, anche dalla Mappa live. L'azione apre la stessa pagina di editing (modalità, target, countdown) **senza fermare né terminare il tracciamento generale**. La pagina è pensata per un momento in cui l'utente può interagire in sicurezza; non va proposta come azione primaria mentre corre.
3. **Durante preparazione TTS, countdown o prova attiva:** il comando «Modifica sprint» è indisponibile con motivo visibile («Puoi modificare lo sprint al termine della prova»). Non interrompere né annullare automaticamente la prova per consentire l'editing, e non aprire un editor che prometta cambiamenti immediati.
4. **Editor:** mostrare i parametri **salvati** all'apertura, consentire la modifica e validare gli intervalli già supportati: countdown 5–10 s; tempo 5–120 s; distanza 10–500 m. I campi non ancora salvati sono una **bozza**.
5. **«Salva configurazione»** sostituisce in modo coerente il preset persistente **solo dopo una validazione riuscita e se la prova è ancora inattiva**, con breve conferma («Sprint aggiornato: 50 m · countdown 5 s»; valori d'esempio). Il dock resta nella medesima posizione, aggiorna il riepilogo e il **prossimo** sprint usa i nuovi parametri. Ritornare alla vista di provenienza (Allenamento oppure Mappa) con il tracking ancora in corso.
6. **«Annulla» / Indietro:** senza salvataggio, preservare il preset precedente; se ci sono modifiche non salvate, prevedere una conferma di abbandono delle modifiche (non della sessione). Nessun «Salva» deve avviare uno sprint.
7. **Concorrenza/aggiornamento di stato:** se una prova viene avviata da un altro contesto mentre l'editor è aperto, rifiutare il salvataggio in stato occupato spiegando di riprovare dopo la conclusione; non mutare a metà prova modalità, target o countdown.
8. **Preset assente:** durante una sessione priva di preset, il dock mostra «Configura sprint» e porta allo stesso editor. Questo non obbliga a configurare per usare il tracking generale e, dopo un salvataggio riuscito, il dock diventa «Avvia sprint» senza cambiare slot o geometria.

**Criteri di accettazione UX:**

- Da Allenamento o Mappa live, con nessuno sprint in corso, «Modifica sprint» è rintracciabile tramite un comando secondario senza scorrere fino a un form nascosto; la grande CTA Sprint rimane la sola azione immediata di avvio.
- Modificare il preset mentre la sessione registra **non interrompe il tracking**. Dopo il salvataggio, la nuova configurazione è usata nella prossima prova e persiste anche nell'allenamento successivo.
- Durante preparazione/countdown/attivo l'editor non è accessibile; la schermata spiega perché e non modifica silenziosamente la prova corrente.
- Uscire senza salvare preserva il preset precedentemente attivo; una bozza non influenza il pulsante «Avvia sprint».
- Aprire/chiudere l'editor dalla Mappa mantiene il contesto della sessione e il dock Sprint riappare nella stessa posizione e dimensione. «Termina allenamento» rimane separato e protetto da conferma.

**Nota di tracciabilità:** l'app analizzata consente già di cambiare i campi Sprint nella tab dedicata quando lo stato non è occupato, ma **non** possiede ancora la UX di preset persistente, pagina secondaria condivisa e dock fisso qui richiesta. Questa specifica descrive cambiamenti futuri, senza modificare codice o salvataggi nella PR documentale.

#### 7.10.9 Decisione confermata: fine Sprint con voce + riepilogo breve, senza cambiare schermata (A)

**Decisione condivisa:** al raggiungimento dell'obiettivo di tempo o distanza, la prova termina automaticamente e l'utente **rimane nella vista corrente**: Statistiche o Mappa live. Non si apre una pagina di riepilogo e non è richiesta alcuna interazione per continuare a correre. La registrazione dell'allenamento generale continua indipendentemente dalla fine Sprint.

**Sequenza UX:**

1. Quando il sistema **conferma** che la prova è conclusa, lo stato cambia da «Sprint in corso» a «Sprint completato», una sola volta; il gesto di annullamento da 2 secondi non può più annullare retroattivamente la prova. Il passaggio alla fine naturale non si confonde con un annullamento volontario.
2. **Voce:** breve annuncio «Sprint completato» (o «Sprint terminato» coerentemente con il TTS implementato), senza lunghe letture delle statistiche per impostazione predefinita. Non sovrapporre messaggi del countdown alla comunicazione di fine. Se TTS è disattivato o non disponibile, testo/stato rimangono completi; non bloccare il tracciamento né inventare un annuncio avvenuto.
3. **Feedback visivo breve e non modale:** comparsa di un riepilogo compatto nell'area di stato della vista attiva, **fuori dalla superficie del dock Sprint** e senza occultare le tre metriche fondamentali o i comandi di navigazione. Esempio: «Sprint completato · 50 m in 12 s» e, quando affidabile e lo spazio lo consente, «Media 15,0 km/h». Il testo è di esempio, non un risultato misurato.
4. **Durata indicativa:** il riepilogo rimane leggibile per pochi secondi (ipotesi UX da validare: circa 4–6 s), poi si riduce a un messaggio discreto o scompare. **Nessuna chiusura obbligatoria.** Evitare un banner persistente che copra la mappa o spinga il pulsante fuori posizione.
5. Il **grande pulsante** torna automaticamente a «⚡ AVVIA SPRINT» sullo **stesso identico rettangolo** (coordinate, dimensioni e area di tocco) in entrambe le viste, con il preset corrente invariato. Non richiedere un tap per confermare il risultato. Il riavvio richiede un **nuovo tocco intenzionale**; un dito rimasto appoggiato durante il passaggio di stato non deve attivare automaticamente una seconda prova.
6. Per vedere i dati completi e l'elenco delle prove della sessione è disponibile un accesso **secondario** («Dettagli sprint») dalle statistiche/dettagli, senza costringere chi corre ad aprirlo. Lo storico degli sprint non viene eliminato dal fatto che il riepilogo breve scompare.

**Informazione affidabile e casi limite:**

| Esito | Risposta UX target |
|---|---|
| Sprint completato con campioni attendibili | Voce breve di conclusione; riepilogo di distanza/tempo realmente misurati e, se attendibile, velocità media; ritorno a CTA pronta |
| Sprint completato con campioni insufficienti | «Sprint completato · velocità non disponibile: dati GPS insufficienti». Non sostituire velocità o distanza mancanti con stime inventate; riportare i valori realmente disponibili e il motivo dell'assenza |
| Sprint a distanza concluso per limite di tempo o altro motivo | Non etichettare «Obiettivo raggiunto» se il target non è stato raggiunto; distinguere «Sprint terminato: limite di tempo» dal completamento dell'obiettivo |
| Sprint annullato con tenuta di 2 s | Feedback distinto «Sprint annullato», senza presentarlo come completato; mantenere l'allenamento generale e riportare la CTA pronta senza conferme aggiuntive |
| GPS temporaneamente debole / TTS indisponibile | Mostrare stato e affidabilità in testo, non bloccare il flusso né garantire una sintesi vocale impossibile |
| Cambio vista durante la conclusione | Stato e risultati coerenti in Statistiche e Mappa; nessuna duplicazione di messaggi o cambio di geometria del dock |
| Più prove ravvicinate | Il feedback della prova precedente non oscura lo stato della nuova e non modifica il preset; i comandi vengono emessi una volta sola |

**Indicazione visuale:**

```text
STATISTICHE O MAPPA — STESSO LAYOUT OPERATIVO
┌──────────────────────────────────────┐
│  Distanza       Durata        Ritmo   │  ← metriche sessione
│                                      │
│  [ Sprint completato · 50 m in 12 s ]│  ← banner breve, non modale
│                                      │
│  [       ⚡ AVVIA SPRINT           ]  │  ← dock immobile
│                 50 m · 5 s           │
└──────────────────────────────────────┘
```

**Criteri di accettazione UX aggiuntivi:**

- Completare uno Sprint **non apre pagine**, non mette in pausa la sessione e non richiede di toccare il telefono per continuare a registrare.
- Il feedback vocale è breve, evita ripetizioni ed è complementare al testo visibile; in assenza TTS il completamento è comunque comprensibile.
- Il riepilogo breve appare nella vista corrente senza coprire la CTA, non cambia la geometria del dock e non oscura permanentemente distanza, durata e ritmo attuale.
- Dopo la conclusione la CTA torna automaticamente a «Avvia sprint», nella stessa posizione in entrambe le viste; serve un nuovo tap per una nuova prova, senza riaprire la configurazione.
- Campioni GNSS insufficienti e conclusione per limite di tempo hanno testi di esito veritieri; un annullamento volontario non è conteggiato o annunciato come obiettivo raggiunto.
- L'elenco/dettaglio degli Sprint resta disponibile per una consultazione successiva, anche dopo la scomparsa del feedback temporaneo.

**Stato rispetto al codice:** `SprintTrial` conosce fasi `FINISHED`/`CANCELLED`, risultati GNSS e cause di fine; `TrackingService` può annunciare via TTS «Sprint terminato», mentre `ActiveSessionActivity` presenta uno stato e uno storico nella tab Sprint. **Il nuovo riepilogo compatto nelle due viste e il ritorno automatico del dock persistente sono UX target da implementare**, non funzioni UI già verificate. Non si modifica il codice in questa proposta.

#### 7.10.10 Decisione confermata — opzione B: voce prioritaria durante la prova, nessun contatore gigante

**Contesto espresso dall'utente:** il telefono è fissato al braccio durante lo sprint; non può essere tenuto in mano e non deve essere necessario fissare lo schermo per seguire l'avanzamento. L'opzione A (conto alla rovescia grande nella prova a tempo e contatore metri gigante nella prova a distanza) è **respinta**. Si approva l'opzione **B: stato essenziale + guida vocale**.

**Gerarchia durante countdown e prova:**

- Restano visibili **distanza totale della sessione, durata totale e ritmo attuale**, qualità del GPS e stato sintetico «Countdown Sprint» / «Sprint in corso»; non introdurre una schermata speciale o grandi indicatori della progressione della prova.
- Il **grande comando Sprint rimane fisso e identico** in Statistiche e Mappa; quando la prova è occupata comunica «Tieni premuto 2 s per annullare», con progressione visiva **solo della pressione di annullamento**, non della distanza/tempo residui dello Sprint.
- All'avvio: feedback immediato, **countdown vocale** e segnale di partenza distinto; non obbligare l'utente a leggere cifre sullo schermo. Il countdown testuale può restare compatto come ridondanza accessibile, senza diventare il focus della pagina.
- Durante la prova: **gli annunci intermedi su quanto manca sono richiesti dall’utente**, non un optional da omettere per scelta di UI. Per prove a tempo comunicare **secondi rimanenti** calcolati sul tempo effettivo; per prove a distanza comunicare **metri rimanenti**, soltanto da progresso GNSS attendibile. Evitare un annuncio per ogni secondo/campione: le soglie di avanzamento devono adattarsi alla durata e alla distanza della prova, con un ritmo comprensibile e senza messaggi sovrapposti (specifica in §7.10.11). Nelle prove estremamente brevi, dove un annuncio diventerebbe tardivo, la voce deve dare priorità a partenza e fine; questa eccezione va verificata su dispositivo, non va presentata come assenza della funzione.
- Alla conclusione: mantenere la decisione §7.10.9 — **annuncio vocale breve e riepilogo visivo temporaneo**, senza spostare l'utente da Statistiche/Mappa e senza tocco per chiudere. Annunciare un esito veritiero («Sprint terminato», «Sprint annullato», «Tempo limite raggiunto»); non dire «obiettivo raggiunto» se non lo è.
- In caso di qualità GNSS inadeguata, **non pronunciare progressi, distanze o velocità non affidabili**. Il servizio può continuare a registrare con le limitazioni previste; la mancanza di TTS deve essere esplicitata con un fallback visivo non bloccante dove appropriato, senza fingere che la voce sia disponibile. Gestione di audio silenzioso, cuffie e interruzioni da altre app: da verificare su dispositivo.
- Dopo l'eventuale annuncio di fine, la CTA ritorna automaticamente a «⚡ AVVIA SPRINT» nello **stesso punto** e lo Sprint resta ripetibile con il preset salvato.

**Criteri di accettazione:**

1. L'utente può iniziare uno sprint, ascoltarne la partenza e riconoscerne la fine **senza consultare lo schermo**; l'annullamento resta un gesto distinto e deliberato di ~2 s.
2. In entrambe le viste, durante countdown/prova non compare alcun contatore gigante dedicato ai secondi/metri residui; il layout principale e il dock non saltano tra stati.
3. Messaggi vocali pertinenti, brevi, non sovrapposti; un ritardo dell'audio non deve rendere erronea la temporizzazione effettiva dello Sprint.
4. GPS insufficiente e TTS indisponibile producono stati sinceri: nessun progresso inventato e nessuna falsa promessa di annuncio vocale.
5. I dettagli cronometrici e statistici della singola prova restano reperibili **dopo** lo Sprint, senza imporsi durante il movimento.

**Distinzione fra stato attuale e target UX:** countdown e alcune comunicazioni TTS di Sprint sono già presenti nella logica Android, inclusi annunci di avanzamento per prove a distanza in condizioni ammesse; la **gerarchia UI a stato essenziale uguale nelle due viste**, la cadenza degli annunci e i fallback audio vanno progettati/verificati. Non sono modifiche al codice in questo documento.

#### 7.10.11 Decisione confermata — annunci vocali intermedi per sapere quanto manca

**Richiesta esplicita dell'utente:** la voce deve coprire **quattro momenti**: **countdown**, **partenza**, **avanzamento durante la prova con indicazione di quanto resta**, **fine**. Non è sufficiente limitarsi a partenza e conclusione: l'utente corre con il telefono al braccio e deve capire il residuo **senza guardare lo schermo**. Questa decisione specifica e completa §7.10.10; non modifica la scelta di mantenere la UI visiva essenziale.

**Sequenza e contenuto da progettare:**

1. **Countdown:** scandire i secondi preparatori previsti dal preset (intervallo attuale 5–10 s). L'utente deve poter distinguere la preparazione dalla misurazione effettiva.
2. **Partenza:** un comando vocale breve e inequivocabile, ad esempio **«Via!»**, emesso al momento di avvio della prova. Il tempo ufficiale della prova è governato dal clock della sessione, non dalla velocità del parlato.
3. **Annunci intermedi obbligatori come comportamento target, compatibilmente con la durata utile della prova:**
   - **Sprint a tempo:** usare il **tempo residuo reale**, non soltanto percentuali astratte. Microcopy esemplificativo per 30 s: **«Mancano 15 secondi»**, poi **«Mancano 5 secondi»**. Per 60 s: **«Mancano 30 secondi»**, poi **«Mancano 10 secondi»**. Le soglie precise sono proposte da testare, non valori approvati né un obbligo di riprodurle tutte.
   - **Sprint a distanza:** usare la **distanza mancante** quando la misura è attendibile. Microcopy esemplificativo per 100 m: **«Mancano 50 metri»**, poi **«Mancano 20 metri»**. Se il GPS non consente un residuo affidabile, evitare numeri inventati: un messaggio sintetico «Segnale GPS insufficiente per stimare i metri rimanenti» è preferibile; non ripeterlo a ogni rilevazione.
   - **Adattamento alla brevità:** il messaggio intermedio va pronunciato solo se può essere **completato prima della fine** e senza coprire il segnale di conclusione. Con sprint molto brevi (es. 5 secondi o 10 metri) non promettere una quantità impossibile di parlato: conservare countdown, partenza e fine, valutare un avviso brevissimo solo quando effettivamente utile. Non riprodurre in ritardo annunci già superati.
4. **Fine:** comunicare tempestivamente un esito corretto, distinto tra obiettivo completato, sprint annullato e fine per limite di tempo; fermare o scartare gli annunci intermedi ancora in coda. Restano il riepilogo non modale e il ripristino automatico del pulsante nello stesso dock (§7.10.9).

**Priorità audio e robustezza:** **fine e partenza > annunci intermedi**. Un nuovo evento non deve far parlare la voce di una fase ormai conclusa; gli annunci intermedi si deduplicano e non si accodano se non più pertinenti. Il cronometro/progresso di sprint non attende il TTS. Per la distanza il conteggio non estrapola attraverso lacune GPS; per il tempo non dipende dal GPS. Un blocco del TTS, audio muto o interruzioni audio richiedono un fallback visivo conciso senza falsi annunci di successo. La convivenza con gli annunci ordinari dell’allenamento è definita in §7.10.13: durante lo Sprint la voce della prova ha priorità esclusiva e gli annunci ordinari non si sovrappongono.

**Test di accettazione UX:**

- Uno sprint a tempo sufficientemente lungo fornisce almeno un avviso di **secondi rimanenti**, oltre a countdown, via e fine, senza richiedere di guardare lo schermo.
- Uno sprint a distanza sufficientemente lungo con GPS attendibile fornisce almeno un avviso di **metri rimanenti**, non soltanto «metà percorso», senza introdurre progressi inesatti.
- Nessun annuncio «mancano X» viene riprodotto **dopo** la conclusione, l'annullamento o quando il valore è ormai superato.
- In prove molto brevi, il numero di messaggi si riduce per non sovrapporre «Via!» e «Sprint terminato», preservando la priorità all'informazione temporalmente corretta.
- Gli stati e il dock restano stabili e identici in Allenamento/Mappa. La cadenza concreta si verifica provando Sprint brevi e lunghi con telefono fissato al braccio e audio effettivo.

**Codice vs obiettivo UX:** l'app già contiene TTS di countdown/terminazione e un percorso di progressi per sprint a distanza; questo documento non presume che **gli annunci di tempo residuo e la politica completa di priorità/de-duplicazione** siano già implementati. Sono comportamenti da sviluppare e verificare nella futura implementazione, non modifiche apportate da questa PR documentale.

#### 7.10.12 Decisione confermata — frequenza automatica degli annunci intermedi Sprint (A)

**Decisione dell'utente:** la frequenza dei messaggi vocali che indicano **quanto manca** alla fine dello Sprint deve essere **determinata automaticamente dall'app**, in base alla **durata** o alla **distanza** configurata. Non aggiungere scelte «essenziali / normali / frequenti», slider, intervalli manuali o altri passaggi nella preparazione della prova.

**Obiettivo ergonomico:** fornire una percezione utile del residuo senza costringere l'utente a guardare il telefono fissato al braccio; evitare una voce prolissa, frammentata o in ritardo, specialmente per Sprint molto brevi.

**Regole di comportamento:**

1. **Pianificazione per Sprint a tempo:** partire dalla durata nominale e dal tempo ancora disponibile sul clock monotono; selezionare automaticamente pochi punti di riferimento distribuiti nella prova e annunciare **il tempo residuo effettivo** (es. «Mancano 15 secondi»), non il solo progresso percentuale. Non trasformare gli esempi vocali di §7.10.11 in soglie rigide per tutte le durate.
2. **Pianificazione per Sprint a distanza:** partire dalla distanza obiettivo e dalla distanza **GNSS valida già acquisita**; selezionare automaticamente opportuni punti di riferimento e annunciare **i metri che mancano** (es. «Mancano 50 metri») solo con misura affidabile. Non dedurre il residuo da una velocità stimata o da coordinate non valide.
3. **Densità adattiva:** prove più lunghe possono prevedere più riferimenti, prove brevi meno. Per durate così brevi da non consentire un messaggio intermedio intellegibile prima della fine, **omettere quel messaggio**, senza sacrificare countdown, «Via!» o annuncio finale. Non promettere un numero minimo per sprint di 5 s o 10 m.
4. **Distanza fra messaggi:** impedire annunci consecutivi troppo ravvicinati o la lettura di un residuo ormai obsoleto. Valutare durata reale della sintesi, ritardi TTS e distanza dalla conclusione; soglie precise e algoritmo di scheduling saranno definiti/testati in implementazione, non arbitrariamente fissati nella UX.
5. **Priorità e cancellazione:** countdown / segnale «Via!» e conclusione/annullamento prevalgono sul residuo; appena una fase cambia, le frasi intermedie già accodate diventate obsolete devono essere eliminate. Un messaggio già emesso per un punto di riferimento non viene ripetuto a causa dei refresh di UI, cambi vista Statistiche/Mappa o aggiornamenti GPS.
6. **Incertezza GPS:** durante uno Sprint a distanza, se non è possibile calcolare con attendibilità i metri mancanti, **non** annunciare un numero. Riprendere gli aggiornamenti soltanto con misure nuovamente affidabili; non riprodurre in ritardo tutte le tappe superate nel frattempo.
7. **Nessuna interazione richiesta:** il preset Sprint resta persistente, l'annuncio avviene automaticamente dopo l'avvio a un tocco; il grande comando conserva la posizione fissa e la tenuta di circa 2 s per annullare. Le statistiche e la Mappa mantengono uno stato visivo essenziale, senza grandi contatori aggiuntivi.

**Esempi illustrativi, non prescrizioni di cadenza:**

- 30 s: «Via!», poi «Mancano 15 secondi» e, se tempestivo, «Mancano 5 secondi», infine «Sprint terminato».
- 100 m con GPS affidabile: «Via!», «Mancano 50 metri», eventualmente «Mancano 20 metri», infine «Sprint terminato».
- 5 s o 10 m: countdown e «Via!», con eventuale annuncio intermedio solo quando realmente pronunciabile senza creare sovrapposizioni; fine annunciata immediatamente.

**Criteri di accettazione UX:**

- L'utente non deve aprire alcuna impostazione sulla frequenza: annunci automatici per Sprint a tempo **e** a distanza.
- Provando preset diversi, gli aggiornamenti sono distribuiti in modo utile e non identico a prescindere dalla lunghezza; nessuna raffica di messaggi su prove brevi.
- Il contenuto è sempre **quanto manca** (secondi o metri), non un generico «sei al 50%» privo di riferimento concreto.
- Nessun annuncio residuo viene emesso dopo fine/annullamento o con misura GNSS inaffidabile; gli avvisi della stessa soglia non si duplicano.
- Il cambio fra Statistiche e Mappa non riavvia la sequenza vocale e non sposta o ridimensiona il pulsante Sprint.
- Test ergonomici con dispositivo al braccio, ascolto reale del TTS e sprint corti/lunghi verificano intelligibilità, latenza, qualità del feedback e comportamento al degrado GPS.

**Stato del codice vs proposta:** la logica corrente prevede già countdown, fine e alcune comunicazioni di avanzamento delle prove a distanza. La politica **automatica e coerente per entrambe le modalità**, inclusa la deduplicazione e l'eliminazione degli annunci obsoleti, è un obiettivo UX da implementare/verificare: **non è attestata come comportamento già esistente**.

#### 7.10.13 Decisione confermata — annunci ordinari dell'allenamento mai sovrapposti alla voce Sprint

**Decisione esplicita dell’utente:** le comunicazioni vocali ordinarie dell’allenamento **non devono sovrapporsi** a countdown, partenza, annunci dei secondi/metri mancanti e fine Sprint. Il dispositivo è fissato al braccio: la chiarezza della voce ha priorità sulla continuità degli aggiornamenti secondari.

**Politica UX target (non assunta come già implementata):**

1. **Precedenza immediata alla prova:** quando l'utente tocca «Avvia sprint» e comincia la preparazione, entra in vigore una modalità di **priorità vocale Sprint**. Se un aggiornamento ordinario è in coda, viene scartato; se è già in riproduzione, lo si interrompe quando tecnicamente possibile prima di pronunciare il countdown, per non mescolare le due voci.
2. **Countdown e Sprint:** per tutta la preparazione, il conto alla rovescia e la misurazione, sospendere gli aggiornamenti ordinari non urgenti (es. letture periodiche di distanza complessiva, ritmo e incoraggiamenti). **Obiettivo raggiunto e checkpoint effettivamente raggiunto sono eventi significativi distinti**: registrarli come annunci in attesa, senza pronunciarli durante la prova. Gli unici annunci emessi durante Sprint sono countdown, «Via!», quantità residua adattiva, conclusione o annullamento.
3. **Conclusione e recupero selettivo:** lasciare terminare l’annuncio finale Sprint; **poi**, se sono stati raggiunti durante la prova, pronunciare in coda gli eventi di progresso **obiettivo → checkpoint**. Quando entrambi coincidono, **recuperarli entrambi e nell’ordine indicato**. Scartare invece letture ordinarie periodiche e altri messaggi ormai superati: **non riprodurre in blocco** gli aggiornamenti saltati. Solo dopo gli eventuali eventi significativi riprendono i nuovi annunci ordinari. Se parte subito un altro Sprint, sospendere la coda dei progressi rimasti in attesa senza interrompere la nuova prova; evitare annunci duplicati e, quando più checkpoint sono maturati durante uno Sprint, recuperare soltanto il più recente (§7.15).
4. **Un solo flusso TTS coordinato:** evitare sintetizzatori o code concorrenti indipendenti per Sprint e sessione; la futura implementazione dovrà arbitrare la voce e stabilire la precedenza senza presumere che basti silenziare un singolo componente. Cambio fra Statistiche/Mappa, refresh di UI o rientro nell’app **non** devono generare annunci duplicati.
5. **Eccezioni e robustezza:** distinguere gli aggiornamenti ordinari da eventuali avvisi **di sicurezza o di funzionamento critico**: questi non devono essere persi senza valutazione. Se una comunicazione davvero urgente deve prevalere, farla gestire in modo sequenziale e comprensibile, **mai contemporaneamente** alla voce Sprint; definire in implementazione la precedenza esatta in base alla gravità. In caso di TTS disabilitato/non disponibile, mostrare stati visivi sintetici senza presentare come pronunciato un messaggio mai emesso.
6. **Nessun impatto sui dati:** questa priorità riguarda **solo il canale delle comunicazioni vocali**. Registrazione GPS, classificazione, obiettivi, checkpoint e misurazione Sprint proseguono con la loro logica normale; non si fermano solo perché le notifiche audio ordinarie sono sospese.

**Microsequenza d’esempio:** «Distanza totale: 3 km» programmato nello stesso istante del tap Sprint → messaggio periodico scartato → «5, 4, 3, 2, 1», «Via!», «Mancano 50 metri», «Sprint terminato» → **se nel frattempo sono stati raggiunti sia obiettivo sia checkpoint di 5 km**, «Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi» → «Checkpoint: cinque chilometri. Tempo trascorso: trenta minuti e quaranta secondi» → ritorno ai nuovi annunci ordinari. Non deve seguire una raffica di letture periodiche arretrate.

**Criteri di accettazione UX:**

- Facendo partire lo Sprint mentre è previsto un messaggio periodico della sessione, **non si ascoltano due voci sovrapposte** né viene ritardato «Via!» da una frase ordinaria rimasta in coda.
- Un avviso **periodico** soppresso durante countdown/prova **non viene riprodotto in ritardo** dopo la fine; sono invece **recuperati gli eventi di traguardo attendibilmente raggiunti** (obiettivo/checkpoint), solo dopo la comunicazione finale Sprint e in ordine obiettivo → checkpoint.
- Un secondo Sprint avviato subito dopo il primo non scatena la lettura degli aggiornamenti arretrati.
- Il cambio fra Mappa e Statistiche non interrompe o duplica countdown, annunci intermedi e messaggio finale.
- Con GPS degradato, annullamento, audio indisponibile e segnali importanti del servizio, resta valida la regola **mai due annunci simultanei**, con gestione esplicita delle urgenze e feedback non ingannevoli.

**Distinzione codice/proposta:** una politica centrale di serializzazione e precedenza TTS è un **requisito target da implementare e testare**; questo documento non afferma che le componenti vocali correnti la garantiscano già. Nessun codice viene modificato dalla PR UX.

### 7.11 Decisione confermata — checkpoint raggiunto con breve annuncio vocale automatico

**Decisione esplicita dell’utente:** al raggiungimento di un checkpoint dell'allenamento, PassoLibero deve pronunciare **automaticamente un messaggio breve**. Lo scopo è tenere informata una persona che corre con il telefono fissato al braccio, senza richiedere tap, letture o cambi di pagina.

**Intervallo iniziale e pagina di configurazione:** per i nuovi allenamenti gli annunci sono **attivi ogni 1 km per impostazione predefinita**, se non esiste una preferenza esplicita differente; l’utente li gestisce nella pagina «Checkpoint vocali» (§7.16).

**Flusso UX desiderato:**

1. Quando i dati registrati confermano il superamento di un checkpoint configurato, produrre **un solo annuncio** sintetico, ad esempio «Due chilometri raggiunti. Tempo trascorso: dodici minuti e trenta secondi». Il valore deve corrispondere alla soglia realmente raggiunta, non a un numero stimato o arrotondato in modo ingannevole.
2. Mantenere visibili statistiche e piccolo indicatore del prossimo checkpoint; **non aprire modal, schede o riepiloghi** e non spostare il grande pulsante Sprint. Un breve feedback visivo non invasivo può accompagnare la voce.
3. L’annuncio del checkpoint è un evento vocale di **progresso significativo**. Se inizia il countdown Sprint o una prova è in corso, prevalgono countdown, «Via!», annunci intermedi e fine prova (§7.10.13); il checkpoint attendibilmente raggiunto resta **in attesa**, e il suo annuncio viene pronunciato **dopo** l’annuncio finale Sprint, senza sovrapposizione. **Se più checkpoint diversi maturano durante lo stesso Sprint, soltanto l’ultimo viene pronunciato a fine prova; tutti restano registrati (§7.15)**. Se coincide con l’obiettivo, pronunciare **prima l’obiettivo, poi il checkpoint** (§7.13).
4. Se più checkpoint risultano superati a causa di un aggiornamento tardivo, **non leggere in sequenza una raffica di annunci**: evitare duplicati e segnalare, se attendibile e ancora utile, soltanto lo stato aggiornato compatibile con la normale cadenza vocale; altrimenti attendere il prossimo checkpoint futuro.
5. Con GPS o distanza non attendibili, **non annunciare come raggiunto un traguardo non verificato**. Dopo un'interruzione dell'app, ripristino della schermata, cambio fra Statistiche/Mappa o ripartenza del servizio, non rileggere checkpoint già notificati come nuovi.
6. In assenza di audio/TTS o quando il feedback vocale generale è disattivato, la sessione continua e il checkpoint resta consultabile visivamente; non promettere che un annuncio sia stato pronunciato. Gli annunci restano distinti dal raggiungimento di un obiettivo complessivo, che è un evento diverso. **Se i due eventi coincidono quando la voce ordinaria è disponibile, annunciare entrambi in sequenza: prima l’obiettivo, poi il checkpoint (§7.12).**

**Microcopy indicativo aggiornato (§7.14):** «Due chilometri raggiunti. Tempo trascorso: dodici minuti e trenta secondi». Includere sempre **distanza e durata totale dall’avvio**, se disponibili e attendibili, con due proposizioni brevi e senza aggiungere altre statistiche. La formulazione definitiva e la pronuncia delle unità vanno verificate su Android TTS.

**Criteri di accettazione UX:**

- Durante una sessione ordinaria con voce disponibile, al superamento attendibile di un checkpoint viene emesso un annuncio automatico breve **una sola volta**, senza tocco e senza cambiare schermata.
- Mentre è in corso countdown/Sprint, il checkpoint non produce audio sovrapposto; una soglia effettivamente raggiunta **viene recuperata dopo la voce finale Sprint**, senza duplicati e senza trascinare annunci periodici obsoleti.
- Cambio di vista, refresh, ripresa del servizio, GPS incerto o più soglie superate insieme non producono notifiche vocali duplicate o false.
- Il grande comando Sprint mantiene posizione e dimensioni; un checkpoint non interrompe la registrazione, la misurazione della prova o l'obiettivo generale.
- Se il raggiungimento di un obiettivo coincide con un checkpoint nella sessione ordinaria, i **due annunci restano distinti** e la voce li pronuncia in sequenza **obiettivo → checkpoint**, senza sovrapporsi.

**Distinzione implementazione/proposta:** la presenza di checkpoint e di feedback vocali è nota dal catalogo e dal codice analizzato, ma **la garanzia UX di singolo annuncio, deduplicazione e arbitraggio completo con la voce Sprint** richiede implementazione/verifica specifica. Questo documento è solo una proposta UX: nessun comportamento viene qui dichiarato già realizzato.

### 7.12 Decisione confermata — coda vocale obiettivo prima del checkpoint

**Decisione esplicita dell'utente:** l'annuncio del checkpoint serve a capire i progressi dell'allenamento **senza guardare il telefono**. Se viene raggiunto nello stesso momento dell'obiettivo complessivo, **non eliminare né sovrapporre i due messaggi**: prima annunciare l'**obiettivo raggiunto**, poi il **checkpoint raggiunto**, in coda.

**Comportamento UX della sessione ordinaria:**

1. Al raggiungimento attendibile dell'obiettivo facoltativo della sessione, comunicare **una sola volta** una frase breve e distinta, ad esempio «Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi». **L’annuncio riporta il tempo al raggiungimento, non il tempo di riproduzione (§7.19).** Non terminare automaticamente la registrazione: l'utente sceglie quando concludere l'allenamento.
2. Al raggiungimento attendibile di un checkpoint, comunicare una sola volta una frase altrettanto breve, ad esempio «Checkpoint: cinque chilometri. Tempo trascorso: trenta minuti e quaranta secondi»; questo messaggio è un aggiornamento di **progresso periodico**, utile durante la corsa senza consultare lo schermo, non un duplicato concettuale dell'obiettivo.
3. Se i due eventi sono emessi insieme, o il secondo arriva mentre il primo è in riproduzione, utilizzare **una coda vocale seriale** nell'ordine fisso **obiettivo → checkpoint**. Il secondo annuncio parte solo dopo la fine del primo, con eventuale breve pausa, **mai in contemporanea**. La coda vale per questi due eventi pertinenti alla stessa soglia, non per accumulare tutte le comunicazioni periodiche.
4. Non pronunciare per la seconda volta l'obiettivo o lo stesso checkpoint a causa di oscillazioni GPS, refresh della schermata, cambio vista, servizio riavviato o trigger ripetuti. Il raggiungimento deve derivare dai dati realmente confermati; se un valore è incerto, non dichiararlo conseguito.
5. Se i checkpoint hanno intervalli regolari (es. ogni 1 km) e l'obiettivo è 5 km, a 5 km dire in sequenza «Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi» e «Checkpoint: cinque chilometri. Tempo trascorso: trenta minuti e quaranta secondi». **Non unire le due frasi in un singolo annuncio:** ciascuna ha una funzione per l'utente.
6. **Coesistenza con Sprint — decisione successiva confermata:** durante countdown/prova nessun annuncio ordinario interrompe la voce Sprint. Se obiettivo e checkpoint sono raggiunti durante lo Sprint, **accodarli entrambi e pronunciarli dopo il messaggio finale della prova, nell’ordine obiettivo → checkpoint** (§7.13). Questa è un’eccezione esplicita alla regola generale di scartare le letture periodiche arretrate: gli eventi di progresso non sono letture periodiche. La coda resta seriale e non produce voci contemporanee.

**Microcopy e accessibilità:** frasi sintetiche, specifiche e comprensibili; evitare lunghi riepiloghi o formulazioni ambigue. Non forzare una lettura visuale. Un feedback visivo discreto è secondario alla voce, mentre il dock Sprint mantiene posizione e dimensioni. Se il TTS non è disponibile, il raggiungimento resta salvato e visibile ma non va finto un annuncio.

**Criteri di accettazione UX:**

- In una sessione senza Sprint attivo, quando obiettivo e checkpoint coincidono, il TTS completa prima l'annuncio dell'obiettivo e soltanto dopo quello del checkpoint; **nessuna sovrapposizione, omissione o inversione**.
- I due eventi non vengono fusi, duplicati o confusi; l'annuncio dell'obiettivo non termina automaticamente l'allenamento.
- La ripresa della sessione, un fix GPS ripetuto, un passaggio fra Statistiche e Mappa e l'apertura di altre pagine non riannunciano soglie già notificate.
- Con voce disattivata o indisponibile e con distanza inattendibile, i dati restano onesti e non si promettono annunci né traguardi non verificati.
- La coda obiettivo/checkpoint non introduce ritardi o sovrapposizioni con la voce Sprint; se i traguardi cadono durante la prova, vengono emessi **dopo** il suo annuncio finale e prima degli aggiornamenti ordinari successivi.

**Distinzione codice/proposta:** la coda seriale e le priorità fra obiettivo, checkpoint e Sprint sono requisiti UX da implementare e verificare. Questo documento non dichiara che siano già garantiti dal codice corrente.

### 7.13 Decisione confermata — recuperare obiettivo e checkpoint dopo lo Sprint (A)

**Decisione esplicita dell’utente:** se l’obiettivo complessivo e un checkpoint vengono raggiunti mentre è in corso uno Sprint, entrambi devono essere comunicati a voce **dopo** l’annuncio finale dello Sprint, **prima l’obiettivo e poi il checkpoint**. È un’eccezione intenzionale alla soppressione degli aggiornamenti ordinari: i traguardi significativi non vanno persi, perché permettono di monitorare i progressi senza guardare il telefono.

**Flusso target:**

1. Durante countdown e Sprint, mantenere il canale vocale riservato alla guida della prova (countdown, «Via!», residuo, fine/annullamento); **nessun annuncio di obiettivo/checkpoint si sovrappone**.
2. Quando i dati attendibili confermano un obiettivo o checkpoint, registrarne l’evento e lo stato di annuncio in attesa, senza confondere il tempo di raggiungimento con quello di riproduzione. Non inserire due volte il medesimo evento per rumore GPS, restart, refresh o cambio vista.
3. Attendere **la fine effettiva della comunicazione vocale conclusiva Sprint**. Solo allora svuotare in sequenza la coda degli eventi significativi maturati durante la prova, dando **sempre precedenza all’obiettivo** rispetto al checkpoint quando entrambi sono in attesa: «Sprint terminato» → «Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi» → «Checkpoint: cinque chilometri. Tempo trascorso: trenta minuti e quaranta secondi».
4. Non accodare o recuperare indiscriminatamente ritmo, messaggi periodici, incoraggiamenti e avvisi non prioritari saltati durante la prova: restano scartati come deciso in §7.10.13. La coda selettiva riguarda esclusivamente traguardi attendibili dell’allenamento.
5. Anche in caso di Sprint annullato, dopo il relativo annuncio finale, recuperare i traguardi realmente raggiunti durante la prova; **non annunciare obiettivi non conseguiti**. Se l'audio non è disponibile, conservare l'evento nel registro della sessione senza dichiarare un annuncio eseguito.
6. Se una nuova prova Sprint parte prima che la coda sia stata letta completamente, sospendere la lettura degli eventi di progresso senza sovrapporla al nuovo countdown; mantenere l’obiettivo ancora da annunciare e **soltanto il checkpoint più recente** ancora in sospeso, evitando duplicati e code crescenti (§7.15). Gli eventi restano nel registro della sessione anche se non pronunciati singolarmente.

**Criteri di accettazione:**

- Con obiettivo e checkpoint di 5 km raggiunti durante uno Sprint, si ascolta esattamente **«Sprint terminato» → «Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi» → «Checkpoint: cinque chilometri. Tempo trascorso: trenta minuti e quaranta secondi»**, mai contemporaneamente o in ordine invertito.
- La registrazione della corsa continua; il grande comando Sprint resta fisso nella stessa posizione. Nessuna finestra o tap è necessario per ascoltare i traguardi.
- Lo stesso checkpoint/obiettivo non viene pronunciato due volte a seguito di oscillazioni GPS, cambio tra Mappa e Statistiche o ripartenza dell'app.
- Messaggi periodici ordinari scartati durante lo Sprint non vengono recuperati a cascata; **i traguardi significativi sì**.
- Con GPS inattendibile, assenza TTS o Sprint annullato, non si comunica una distanza inventata; il comportamento è verificabile anche dopo riavvio del servizio.

**Tracciabilità:** è una specifica UX **da implementare e testare** nell’arbitro vocale. Prevale sui precedenti testi di bozza che suggerivano di non recuperare i checkpoint dopo lo Sprint. Non sono state modificate le classi Java né le logiche di tracking.


### 7.14 Decisione confermata — checkpoint vocale con distanza raggiunta e tempo trascorso dall’inizio (B)

**Decisione esplicita dell’utente:** ciascun checkpoint deve comunicare **due informazioni**: la **distanza totale raggiunta** e il **tempo trascorso dall'inizio dell'allenamento**. La finalità è seguire i progressi senza consultare il telefono mentre si corre. Non annunciare semplicemente «Checkpoint raggiunto»; non confondere il tempo totale con il tempo parziale dell’ultimo chilometro o del singolo intervallo.

**Formato UX e microcopy:**

- Esempio checkpoint ordinario: «Tre chilometri raggiunti. Tempo trascorso: diciotto minuti e venti secondi».
- Esempio con obiettivo coincidente: «Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi» → «Checkpoint: cinque chilometri. Tempo trascorso: trenta minuti e quaranta secondi». L'obiettivo resta un annuncio distinto e precede il checkpoint (§7.12).
- Esempio con Sprint nel mezzo: «Sprint terminato» → «Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi» → «Checkpoint: cinque chilometri. Tempo trascorso: trenta minuti e quaranta secondi». La seconda frase usa **il tempo riferito al raggiungimento del checkpoint**, **non** quello in cui il messaggio viene letto dopo lo Sprint (§7.13).

**Comportamento e integrità del dato:**

1. Ottenere la distanza raggiunta dalla progressione **attendibile** della sessione; associare al checkpoint il tempo totale trascorso **al momento in cui l’evento viene rilevato e confermato**, mantenendolo nel suo record/evento in attesa. Non ricalcolare il tempo totale al momento del playback vocale: il ritardo di una coda, in particolare durante Sprint, non deve alterare l'informazione.
2. Il tempo è quello **complessivo dall’avvio della sessione** secondo la stessa metrica temporale esposta in Allenamento, non uno split sul singolo checkpoint; pronunciarlo in minuti e secondi, oppure ore/minuti/secondi quando serve. Non inventare la precisione di un attraversamento di soglia se il GPS è discontinuo; usare la misura attendibile disponibile e distinguere eventuale ritardo di rilevazione.
3. Rendere l’annuncio conciso: due informazioni, nessuna lettura obbligatoria di ritmo medio, velocità, calorie o dettagli non richiesti. Parlare con unità complete, non abbreviazioni ambigue come «km» o «18:20».
4. Se il tempo totale è temporaneamente non disponibile/inaffidabile, **non leggerne un valore inventato**; come fallback, pronunciare solo la distanza confermata e lasciare il valore temporale come non disponibile nella diagnostica/visualizzazione. Se nemmeno la distanza è attendibile, non dichiarare raggiunto il checkpoint.
5. Conservare deduplicazione, continuità del tracciamento e assenza di UI modali. Durante Sprint, differire l'annuncio completo e conservarne **anche il tempo originale**, nell'ordine Sprint concluso → obiettivo → checkpoint. Una volta pronunciato, non rileggerlo per oscillazioni GPS o cambio vista.
6. Se più checkpoint diversi si accumulano durante un singolo Sprint, **annunciare esclusivamente il più recente** al termine della prova e conservarne distanza e tempo originari; gli altri checkpoint restano registrati, senza essere letti in coda (§7.15). Se molte soglie emergono da un salto GPS, non attribuire tempi di attraversamento inventati; richiedere conferma attendibile del progresso.

**Criteri di accettazione UX:**

- Con checkpoint a 3 km raggiunto a 00:18:20, la voce comunica in sequenza **3 km** e **18 minuti e 20 secondi**, senza richiedere interazioni e senza cambiare schermata.
- Se il checkpoint a 5 km coincide con l’obiettivo, si pronuncia prima l'obiettivo e poi il checkpoint completo **distanza + tempo totale**, senza frasi sovrapposte o duplicate.
- Se il checkpoint a 5 km è raggiunto al tempo T durante Sprint ma il messaggio viene emesso più tardi, il valore letto corrisponde a **T**, non al tempo trascorso alla fine della coda vocale.
- Le ore sono pronunciate correttamente per sessioni lunghe; dopo refresh, variazioni GPS, rientro dalla Mappa o riavvio del servizio non si creano secondi falsi o duplicazioni vocali.
- La voce Sprint resta prioritaria; il pulsante grande è fisso; l'allenamento non viene terminato automaticamente quando si raggiunge il checkpoint o l'obiettivo.

**Tracciabilità:** comportamento desiderato da progettare, implementare e verificare su dispositivo Android. La documentazione descrive l'esperienza target, **non** una funzionalità già garantita in tutte le build.


### 7.15 Decisione confermata — dopo lo Sprint annunciare solo l'ultimo checkpoint raggiunto (B)

**Decisione esplicita dell’utente:** se nel corso di uno stesso Sprint si raggiungono **più checkpoint**, dopo la prova annunciare a voce **soltanto quello più recente**, perché il messaggio deve aiutare a conoscere il progresso attuale senza produrre una lunga coda di aggiornamenti già superati. **Tutti i checkpoint raggiunti restano comunque registrati** nella sessione; la selezione riguarda solo il playback vocale.

**Comportamento UX target:**

1. Durante countdown e Sprint, registrare ogni checkpoint **effettivamente raggiunto e validato** con soglia di distanza e tempo totale di sessione associato al raggiungimento. Non dichiarare superate soglie non confermate dal tracciamento attendibile.
2. Per la voce, conservare **solo il checkpoint valido più recente** ancora da comunicare; un nuovo checkpoint sostituisce il precedente nella coda TTS, **non** nei dati storici della sessione. Non duplicare il medesimo checkpoint per oscillazioni GPS, ritorno in app o cambio di vista.
3. Attendere che sia terminato l'**annuncio conclusivo Sprint**. Se è maturato anche un obiettivo complessivo, pronunciarlo **prima** del checkpoint selezionato: **fine Sprint → obiettivo (se raggiunto) → ultimo checkpoint**. È possibile che l'obiettivo coincida con una soglia precedente: in tal caso conservarne l'annuncio dedicato e pronunciare comunque il checkpoint più recente, senza perdere il traguardo-obiettivo.
4. Il checkpoint recuperato deve pronunciare **distanza e tempo totale della sessione al momento di quel raggiungimento**, non il tempo trascorso al momento in cui il TTS finalmente legge il messaggio (§7.14). Un checkpoint precedente non deve essere pronunciato successivamente come messaggio arretrato.
5. Se parte un altro Sprint prima della fine della coda vocale, la nuova prova mantiene la priorità: non parlare sopra il countdown. Conservare soltanto l'ultimo checkpoint ancora in attesa e l'eventuale obiettivo pendente, da recuperare al successivo intervallo vocale disponibile, senza far crescere la coda indefinitamente.
6. La registrazione dei checkpoint resta indipendente dalla disponibilità della voce. Eventuali problemi TTS o GPS non cancellano gli eventi attendibili già salvati né autorizzano a inventare misure; nessun messaggio scartato dalla coda audio deve essere falsamente presentato come pronunciato.

**Esempio di accettazione:** checkpoint ogni 250 m, durante uno Sprint vengono raggiunti 3,0 km al tempo 00:18:20 e 3,25 km al tempo 00:18:55. Dopo «Sprint terminato» si ascolta **solo** «Tre chilometri e duecentocinquanta metri raggiunti. Tempo trascorso: diciotto minuti e cinquantacinque secondi». Il checkpoint di 3,0 km rimane registrato, ma non viene letto in ritardo. Se durante la stessa prova era stato raggiunto anche l'obiettivo di 3 km, l'ordine è **Sprint terminato → Obiettivo di 3 km raggiunto, in 18 minuti e 20 secondi → Checkpoint: tre chilometri e duecentocinquanta metri, tempo 18:55**. In nessun caso due voci parlano simultaneamente o viene mostrata una nuova schermata.

**Tracciabilità:** decisione di UX target da implementare/verificare nell'arbitro vocale e nella persistenza degli eventi checkpoint, non una garanzia del codice Android corrente. Non modifica SprintTrial, GPS o codice applicativo in questa bozza Markdown.

### 7.16 Decisione confermata — checkpoint vocali abilitati di default ogni 1 km e pagina dedicata

**Decisione esplicita dell’utente:** nei **nuovi allenamenti**, i checkpoint vocali sono **attivi automaticamente ogni 1 km**, senza chiedere una configurazione prima di iniziare. L’utente può modificare queste preferenze in una **pagina dedicata «Checkpoint vocali»**. Il checkpoint comunica distanza raggiunta e tempo totale dall'inizio della sessione, come deciso in §7.11–§7.15.

**Comportamento UX target:**

1. **Valori iniziali per chi non ha mai personalizzato i checkpoint:** interruttore «Annunci vocali dei checkpoint» **attivo** e intervallo **1 km**. Non aggiungere passaggi obbligatori alla Home o alla procedura «Inizia allenamento»; il primo checkpoint di una sessione ordinaria viene annunciato a 1 km, se raggiunto attendibilmente e se il canale vocale è disponibile.
2. **Rispetto delle scelte precedenti:** il default vale **solo in assenza di una preferenza esplicita**. Se l'utente ha già modificato l'intervallo o disattivato gli annunci, non riattivarli né riportarli a 1 km all'avvio successivo, dopo un aggiornamento dell'app o al ritorno dalla pagina. Per installazioni con preferenze pregresse, distinguere in fase di migrazione un'impostazione assente da una scelta salvata: non interpretare automaticamente un precedente «disattivato» intenzionale come «mai configurato». Il comportamento per eventuali formati legacy va verificato nel codice prima dell'implementazione.
3. **Pagina autonoma, non un popup:** percorso principale proposto **Impostazioni → Allenamento → Checkpoint vocali**. Presentare un titolo chiaro, una breve spiegazione («Ti annunciamo distanza e tempo mentre corri»), un interruttore «Annunci vocali dei checkpoint», il controllo «Ogni quanto» e un riepilogo («Attivi · Ogni 1 km»). Mostrare il valore già salvato; non far perdere configurazioni cambiando vista.
4. **Modifica dell'intervallo — scelta confermata:** mostrare scelte rapide e un'opzione **«Personalizzato»** per inserire la distanza desiderata in un campo dedicato con unità chiare; il comportamento completo è in §7.17. Esempi di intervalli rapidi: **250 m, 500 m, 1 km, 2 km, 5 km**. Il valore personalizzato è una modifica UX/funzionale rispetto alle opzioni attuali e richiede validazione e supporto nella logica dei checkpoint. Non introdurre un controllo della *frequenza degli annunci Sprint*: i due sistemi sono separati.
5. **Interruttore spento:** nascondere/disabilitare in modo comprensibile la modifica dell'intervallo, oppure lasciarla visibile come preferenza conservata con indicazione «Gli annunci sono disattivati». Riaccendendo, ripristinare l'ultimo intervallo scelto, non forzare 1 km. I checkpoint continuano a essere **misurati/registrati** come eventi della sessione anche se non vengono pronunciati, ove supportato dalla logica applicativa; la scelta qui riguarda il feedback vocale.
6. **Salvataggio comprensibile:** azione «Salva impostazioni» con conferma dell'esito, oppure salvataggio immediato con feedback esplicito, da uniformare al pattern delle altre impostazioni. Uscita con modifiche non salvate non deve applicarle silenziosamente. Preferenze conservate sul dispositivo tra allenamenti; non richiedere account o rete.
7. **Sessione in corso — decisione confermata (§7.18):** accesso secondario da **Allenamento → Opzioni → Checkpoint vocali**, senza spostare il grande pulsante Sprint o fermare il tracking. Quando un nuovo intervallo valido viene **salvato**, si applica **subito** ai checkpoint futuri della sessione, calcolati dalla distanza totale già percorsa; nessun recupero, duplicato o ricalcolo retroattivo. Esempio: a 3,2 km, scegliendo 750 m, la soglia successiva è **3,75 km**. Nessuna riconfigurazione è necessaria durante la corsa.
8. **Coordinamento della voce:** se gli annunci vocali generali/TTS sono disabilitati o non disponibili, non simulare una comunicazione avvenuta: mostrare un messaggio informativo e mantenere la preferenza checkpoint, così da poterla usare quando l'audio tornerà disponibile. Gli annunci dell'obiettivo mantengono la precedenza sui checkpoint (§7.12); gli annunci Sprint hanno priorità e, dopo la prova, si recupera soltanto il checkpoint più recente quando ne sono stati raggiunti più di uno (§7.15).
9. **Qualità e accessibilità:** non anticipare checkpoint con dati GPS incerti; un checkpoint già annunciato non si ripete per correzioni della distanza. Interruttore e selezione intervallo devono avere etichette leggibili con TalkBack; evitare unità implicite, controlli minuti e configurazioni obbligatorie prima dell'avvio.

**Wireframe testuale proposto, valori iniziali:**

```text
CHECKPOINT VOCALI
← Impostazioni

Ricevi aggiornamenti automatici su distanza
raggiunta e tempo trascorso mentre ti alleni.

[●] Annunci vocali dei checkpoint   Attivi

OGNI QUANTO
[ 1 km                              ▾ ]

Esempio di annuncio:
«Un chilometro raggiunto.
 Tempo trascorso: sei minuti e dieci secondi.»

[ Salva impostazioni ]
```

L'esempio vocale è illustrativo. **Pagina dedicata, default 1 km, scelta tra intervalli rapidi e valore personalizzato, accesso in-sessione e applicazione immediata dopo il salvataggio sono decisioni confermate (§7.18).** Restano da validare le soglie ammesse per la distanza libera e i dettagli tecnici del controllo.

**Criteri di accettazione:**

- Una nuova configurazione senza preferenze checkpoint esplicite mostra **annunci attivi / ogni 1 km** e non richiede setup all'avvio della prima sessione.
- Alla distanza affidabile di 1 km viene prodotto un solo annuncio con **distanza e tempo totale al raggiungimento**; nessun annuncio anticipato o duplicato a causa del GPS o della ricreazione della schermata.
- Una preferenza salvata «disattivati» rimane disattivata alla sessione successiva; una preferenza salvata «ogni 500 m» rimane 500 m, senza tornare a 1 km.
- La pagina dedicata permette di vedere e cambiare lo stato degli annunci e l'intervallo, scegliendo fra pulsanti rapidi e valore personalizzato, con unità e validazione chiare; le modifiche sono persistenti e il comando Sprint resta al suo posto. Si applicano inoltre i criteri di §7.17.
- Eventuali checkpoint e obiettivi coincidenti mantengono l'ordine **obiettivo → checkpoint**; se avvengono durante lo Sprint, la voce recupera dopo la fine della prova l'**ultimo checkpoint**, senza perdere gli altri dati registrati.
- Con TTS indisponibile, gli allenamenti proseguono e l'app non comunica un falso successo vocale; una preferenza checkpoint non è cancellata soltanto per un problema temporaneo di audio.

**Tracciabilità:** si tratta di una **specifica UX target**, non di un'affermazione che il default 1 km o la pagina autonoma esistano già nell'app. La futura implementazione deve verificare e migrare correttamente i valori attuali, senza apportare alcuna modifica al codice in questa fase.


### 7.17 Decisione confermata — intervalli checkpoint predefiniti più distanza personalizzata (B)

**Decisione esplicita dell'utente:** la pagina «Checkpoint vocali» deve offrire sia una selezione immediata di intervalli comuni sia la possibilità di inserire una **distanza personalizzata**. Non costringere chi desidera checkpoint ogni 750 m o 1,5 km ad adattarsi a una lista chiusa. Restano invariati gli annunci attivi di default ogni 1 km in assenza di preferenze esplicite, la persistenza tra allenamenti e il contenuto vocale «distanza raggiunta + tempo totale» (§7.16).

**Architettura e interazione proposta:**

1. **Scelte rapide in primo piano:** opzioni chiaramente selezionabili **250 m / 500 m / 1 km / 2 km / 5 km** e ultima opzione «Personalizzato». La selezione corrente è leggibile e riconoscibile senza basarsi solo sul colore; una sola opzione alla volta.
2. **Inserimento libero solo se scelto:** selezionando «Personalizzato», mostrare un campo numerico etichettato **«Intervallo personalizzato (metri)»**, con tastiera numerica e unità sempre visibile. Esempi: **750** = 750 m, **1500** = 1,5 km. Non serve conoscere un formato `0,75 km`; l'unità rimane esplicita. Evitare di mostrare un campo vuoto nella vista iniziale a 1 km.
3. **Validazione prima del salvataggio:** accettare soltanto un numero positivo finito e una distanza concretamente gestibile dal motore; rifiutare valori vuoti, zero, negativi, non numerici o fuori dall'intervallo tecnico ammesso. **Le soglie minime/massime specifiche non sono fissate qui** e vanno confermate verificando limiti del codice e impatto su GPS, frequenza dei messaggi e prestazioni. Mostrare un messaggio vicino al campo che espliciti l'intervallo ammesso, non fallire silenziosamente.
4. **Conferma e memorizzazione:** con «Salva impostazioni», mostrare «Checkpoint vocali: ogni 750 m» (oppure l'intervallo selezionato); conservare il valore esatto e la modalità personalizzata tra sessioni e riaperture della pagina. Se il valore coincide con uno dei preset, il risultato può essere normalizzato graficamente senza perdere la distanza scelta. Uscire senza salvare non deve alterare il valore precedente.
5. **Modifica senza perdita di valori:** passare temporaneamente da «Personalizzato» a una scelta rapida non cancella la bozza personalizzata fino all'uscita dalla schermata; tornando all'opzione libera, il numero inserito è nuovamente disponibile. Se gli annunci sono disattivati, l'ultimo intervallo resta memorizzato per la riattivazione.
6. **Progressi e applicazione immediata — deciso (§7.18):** un intervallo salvato durante la sessione modifica **subito** le soglie dei checkpoint futuri in base alla distanza complessiva; non riannuncia né ricrea checkpoint passati e non cambia l'ordine Sprint → obiettivo → checkpoint. Esempio: a 3,2 km, cambiando da 1 km a 750 m, il prossimo checkpoint è 3,75 km, non 4 km né 3,95 km.
7. **Limitazione dell'ambito:** questa schermata configura i checkpoint dell'allenamento, non la cadenza degli annunci intermedi Sprint. Soglie ultrabrevi che genererebbero un flusso vocale continuo non devono essere abilitate senza protezione esplicita.

**Wireframe testuale (valori dimostrativi):**

```text
CHECKPOINT VOCALI
[●] Annunci vocali dei checkpoint

OGNI QUANTO?
[250 m] [500 m] [1 km ✓] [2 km] [5 km]
[ Personalizzato ]

Se selezioni «Personalizzato»:
Intervallo personalizzato (metri)
[ 750 ] m
«Ti aggiorneremo ogni 750 metri.»

[ Salva impostazioni ]
```

**Criteri di accettazione specifici:**

- Per una nuova installazione senza preferenze la pagina mostra 1 km già selezionato e annunci attivi: nessun campo personalizzato obbligatorio.
- Con un solo tocco si può scegliere un intervallo rapido; con «Personalizzato» si può salvare per esempio **750 m** e ritrovarlo invariato all'allenamento successivo.
- Un valore personalizzato non valido impedisce il salvataggio, con motivazione vicino al campo; non altera la configurazione già salvata.
- Una distanza personalizzata valida viene presentata con un'unità leggibile anche nei riepiloghi; l'utente sa quanto spesso sentirà i checkpoint.
- Disattivare e riattivare gli annunci non reimposta l'intervallo; il pulsante Sprint e la guida vocale Sprint non cambiano.
- La realizzazione non presume già implementata l'accettazione di qualsiasi intervallo nel motore: verificare limiti e compatibilità, senza toccare codice nella PR documentale.
- Una modifica durante la sessione si applica immediatamente **solo dopo salvataggio valido** e non genera checkpoint retroattivi; test in §7.18.

### 7.18 Decisione confermata — nuovo intervallo checkpoint applicato subito durante l’allenamento (A)

**Decisione esplicita dell’utente:** se l'intervallo dei checkpoint viene modificato e salvato **durante un allenamento attivo**, il nuovo valore vale **immediatamente per i checkpoint futuri di quella sessione**. Le soglie restano ancorate alla **distanza totale dall'inizio dell'allenamento**, non alla distanza percorsa dal momento della modifica; **non recuperare** checkpoint precedenti né emettere annunci arretrati. Il nuovo intervallo rimane memorizzato per le sessioni successive, come già deciso in §7.16–§7.17.

**Esempio canonico di comportamento:** a **3,2 km** di distanza totale, l'utente cambia l'intervallo **da 1 km a 750 m** e preme «Salva impostazioni». Le soglie del nuovo schema sono 0,75 / 1,50 / 2,25 / 3,00 / **3,75** / 4,50 km. La prima soglia **strettamente successiva** alla distanza registrata è quindi **3,75 km**. Non annunciare retroattivamente 750 m, 1,5 km, 2,25 km o 3 km; non aspettare il precedente checkpoint di 4 km; non creare un checkpoint a **3,95 km** come se l'intervallo ripartisse dai 3,2 km.

**Flusso e regole UX target:**

1. **Accesso durante la sessione:** da **Allenamento → Opzioni → Checkpoint vocali** si apre la stessa pagina dedicata; il tracciamento prosegue. Non spostare, ridimensionare o assegnare nuove funzioni al grande comando Sprint nelle viste Statistiche e Mappa.
2. **Solo al salvataggio:** la selezione dell'intervallo è una bozza finché non viene confermata. Un valore errato, «Annulla» o l'uscita senza salvataggio non cambiano alcuna soglia o preferenza in uso. Dopo un salvataggio valido, mostrare una conferma breve («Checkpoint ogni 750 m. Prossimo a 3,75 km») quando la distanza è nota e attendibile.
3. **Base di calcolo fissa:** scegliere il **primo multiplo positivo del nuovo intervallo strettamente maggiore della distanza totale attendibile** al momento del salvataggio. Una soglia esattamente uguale alla distanza già registrata è considerata trascorsa, non un nuovo evento. Da lì proseguire con i successivi multipli del nuovo intervallo.
4. **Nessuna retroattività:** i checkpoint già registrati e i rispettivi tempi restano nello storico della sessione, senza modifica né duplicazione; il cambio di intervallo non genera eventi né annunci per soglie del nuovo schema che risultano già superate.
5. **GPS e qualità del dato:** con distanza temporaneamente incerta, non dedurre soglie da un dato non attendibile né pronunciare progressi inventati. Applicare l'intervallo salvato e calcolare/validare le soglie solo quando torna disponibile una progressione affidabile, evitando ricostruzioni di attraversamenti non misurati.
6. **Coda vocale:** checkpoint, obiettivo e Sprint conservano le priorità già stabilite: nessuna sovrapposizione; durante Sprint differire i traguardi e recuperare dopo il messaggio finale l'obiettivo e **solo l'ultimo checkpoint** pertinente (§7.13–§7.15). Un cambio d'intervallo non riproduce eventi precedenti; un evento già effettivamente registrato prima del cambio non viene falsificato o cancellato, e la gestione di un eventuale messaggio ancora pendente rispetta le regole di coda e deduplicazione.
7. **Persistenza:** la stessa configurazione resta attiva fino a ulteriore modifica, per il resto della sessione e per i futuri allenamenti; non riapplicare automaticamente il default di 1 km a chi ha salvato 750 m. Disattivare/riattivare gli annunci non cancella l'ultimo intervallo scelto.

**Criteri di accettazione:**

- A 3,2 km, cambiare da 1 km a 750 m e salvare: nessun annuncio al salvataggio; la prossima soglia valida è **3,75 km**, quindi 4,50 km e così via.
- Se la distanza è esattamente 3,00 km e si salva 750 m, la prossima soglia è **3,75 km**, senza ripetere quella di 3 km.
- Se si modifica soltanto un campo senza salvare, il motore mantiene l'intervallo precedente; lo stesso accade con valori non validi.
- Il cambio non cancella checkpoint già registrati, non annuncia eventi retroattivi e non interrompe GPS, cronometro, statistiche, Mappa o Sprint.
- Se un checkpoint e l'obiettivo vengono raggiunti insieme dopo il cambio, la voce annuncia **obiettivo prima, checkpoint poi** con tempo totale misurato al raggiungimento; durante uno Sprint gli annunci restano differiti e non sovrapposti.
- Chiudere la sessione e iniziarne un'altra mantiene la nuova preferenza checkpoint, salvo nuova modifica esplicita.

**Nota di tracciabilità:** applicazione in tempo reale e ricalcolo delle soglie sono **comportamenti UX target da implementare/verificare**. Questa PR resta documentale e non modifica il motore dei checkpoint. I limiti ammessi per gli intervalli personalizzati e la riconciliazione della posizione GPS sono aspetti tecnici da testare.

### 7.19 Decisione confermata — annuncio obiettivo con distanza e tempo di raggiungimento

**Decisione esplicita dell’utente:** al raggiungimento attendibile dell’obiettivo di distanza, pronunciare una frase che contenga **la distanza dell’obiettivo e il tempo totale trascorso al momento del raggiungimento**. La formulazione confermata, da usare come riferimento di microcopy, è: **«Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi»**. Questo annuncio è distinto da quello del checkpoint e non interrompe automaticamente la sessione.

**Comportamento UX target:**

1. Per un obiettivo **non ancora raggiunto al momento dell’impostazione**, rilevare e confermare il raggiungimento attraverso la distanza complessiva attendibile della sessione. Associare all’evento il **tempo totale dall’avvio** rilevato in quell’istante e conservarlo con l’evento: non ricavare il tempo dal momento in cui il TTS riesce a parlare.
2. Comporre la frase usando il **target reale** impostato dall’utente e una **durata leggibile in italiano**; includere ore quando la durata totale lo richiede. Esempio letterale approvato per target 5 km e tempo 00:30:40: «Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi». Per un target diverso cambiano il numero e la durata, non il senso della frase. Evitare arrotondamenti della distanza che modifichino l'obiettivo effettivamente scelto.
3. Se l’obiettivo coincide con un checkpoint, emettere **prima** questo annuncio completo e **poi** il checkpoint, che mantiene il proprio formato «distanza raggiunta + tempo trascorso». La possibile ripetizione della durata è **intenzionale**: l’utente ha confermato due eventi distinti e ordinati, non una fusione delle frasi.
4. Se il target viene raggiunto durante countdown o Sprint, mantenere in attesa il messaggio con **il tempo originale di raggiungimento** e pronunciarlo solo **dopo** l’annuncio conclusivo della prova; poi leggere soltanto il checkpoint più recente raggiunto durante quello Sprint, se presente. Nessuna sovrapposizione o interruzione del feedback Sprint.
5. Deduplicare l’evento: oscillazioni GPS, riavvio servizio, passaggio tra Statistiche e Mappa e riproduzioni differite non devono generare un secondo annuncio per lo stesso obiettivo. Con GPS inattendibile non dichiarare raggiunto un obiettivo non verificato. Con TTS non disponibile il dato resta nello stato di sessione senza fingere un annuncio vocale.
6. L’allenamento **continua dopo l’obiettivo**, fino alla richiesta esplicita «Termina e salva» già protetta da conferma; non usare la conclusione dell’annuncio come comando per interrompere il tracking.

**Casi di accettazione:**

- Obiettivo 5 km, tempo totale al superamento 00:30:40, voce disponibile: il TTS comunica l’equivalente naturale di **«Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi»**, una sola volta.
- Con checkpoint di 5 km coincidente: «Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi» → «Checkpoint: cinque chilometri. Tempo trascorso: trenta minuti e quaranta secondi», in sequenza, senza annullare uno dei due eventi.
- Se il traguardo è raggiunto durante uno Sprint e la voce viene riprodotta più tardi, **30:40 resta 30:40**; prima viene pronunciato il messaggio finale Sprint, poi l’obiettivo completo e infine l’eventuale ultimo checkpoint raggiunto.
- Dati GPS deboli, evento duplicato, TTS indisponibile o cambi di vista non creano falsi traguardi o arresti automatici.

**Tracciabilità:** formato vocale e memorizzazione dell’istante di raggiungimento sono requisiti della **proposta UX**, non dichiarazioni sull’implementazione attuale; il codice rimane fuori dall’ambito della PR documentale.

### 7.20 Decisione confermata — modifica dell’obiettivo anche durante l’allenamento (A)

**Decisione esplicita dell’utente:** l’obiettivo di distanza, facoltativo prima della partenza, può essere **modificato durante una sessione attiva** tramite un comando secondario, **senza fermare la registrazione**. Il grande pulsante Sprint resta nella sua posizione fissa sia nelle Statistiche sia nella Mappa. Non trasformare la modifica in un passaggio necessario per iniziare o continuare ad allenarsi.

**Ingresso e navigazione UX target:**

- Dalla schermata **Allenamento** o dalla **Mappa**: **«Opzioni allenamento» → «Modifica obiettivo»**, accesso secondario riconoscibile ma non concorrente con il comando Sprint.
- Mostrare l’eventuale distanza target già salvata e permettere di **modificarla, rimuoverla o lasciarla invariata**. Usare metri/chilometri e validazione coerente con il motore obiettivi; nessun valore negativo, zero ambiguo o obiettivo inventato.
- **«Salva obiettivo»** applica soltanto una modifica valida; **«Annulla»/Indietro** scarta la bozza. Dopo il salvataggio, tornare alla vista di provenienza senza spostare la camera GPS se si proveniva dalla Mappa.
- Il tracciamento (distanza, cronometro, GPS, sensori e classificazione) **continua** mentre la pagina è aperta; il salvataggio non azzera il totale, non interrompe lo Sprint già registrato e non conclude l’allenamento.
- Se il nuovo target è **maggiore della distanza totale corrente**, mostrare distanza residua calcolata sul totale già percorso e annunciare il raggiungimento **una sola volta** quando verrà realmente superato. Il messaggio usa il formato definito al §7.19 e conserva il tempo reale di raggiungimento.
- Se l’utente **rimuove** l’obiettivo, nascondere l’avanzamento relativo e non annunciare un nuovo traguardo. I checkpoint configurati restano indipendenti e continuano ad annunciarsi.
- **Caso deciso — nuovo target già raggiunto:** se la nuova distanza target è **inferiore o uguale al totale già percorso** al momento del salvataggio (per esempio 3 km impostati dopo 4 km, oppure 4 km dopo 4 km), **salvare comunque** il target e mostrare lo stato visivo «Obiettivo già raggiunto». Non pronunciare un annuncio di raggiungimento retroattivo, non inventare né stimare un tempo storico al superamento della soglia e non interrompere la sessione. Mostrare un progresso compiuto anziché una distanza residua negativa; i checkpoint restano indipendenti (§7.21).
- **Deduplicazione:** non ripetere un annuncio di obiettivo già emesso per il medesimo evento soltanto perché la pagina viene riaperta, viene modificato un campo, cambia la Mappa o il servizio riemette lo stato. L’eventuale modifica di un target dopo un precedente raggiungimento richiederà una politica di eventi coerente da validare, senza falsi doppi annunci.

**Esempio di flusso confermato:** a 3,2 km l’utente cambia il target da 5 km a 6 km e salva → l’allenamento continua, torna alla vista precedente, l’avanzamento mostra **2,8 km rimanenti**; raggiunti realmente i 6 km, si annuncia **«Obiettivo di 6 km raggiunto, in …»** con il tempo totale al superamento. Se in quello stesso istante matura un checkpoint, applicare la sequenza **obiettivo → checkpoint**; se avviene durante uno Sprint, comunicare entrambi dopo la fine dello Sprint secondo §7.13–§7.15.

**Criteri di accettazione:**

- Da Statistiche e Mappa è possibile raggiungere «Modifica obiettivo» senza navigazioni obbligatorie attraverso lo Sprint.
- Entrando, modificando, salvando o annullando, **registrazione e cronometro non si fermano** e la posizione/area del grande dock Sprint nelle viste operative resta identica.
- Nessuna modifica non salvata influisce sul tracking o sull’obiettivo attivo; un salvataggio valido aggiorna immediatamente il target e il progresso per la sessione corrente.
- A target futuro: progressi, residuo e annuncio con il tempo al raggiungimento sono coerenti; nessun falso annuncio al semplice salvataggio.
- Rimuovere un target non modifica intervallo/checkpoint e non chiude la sessione.
- A distanza attuale **4 km**, impostare e salvare un target **3 km** (o esattamente **4 km**) riesce: lo stato mostra «Obiettivo già raggiunto» e non viene generato un annuncio TTS, un tempo di arrivo retroattivo, un checkpoint aggiuntivo o una terminazione della sessione; la registrazione continua e lo Sprint resta fermo nello stesso dock. Verificare persistenza al rientro nella schermata e dopo un cambio vista.

**Distinzione tra proposta e codice:** la possibilità di cambiare il target durante la sessione, la navigazione dedicata, il ricalcolo dell’avanzamento e la politica degli annunci dopo una modifica sono **requisiti UX target da verificare e implementare**. La presente bozza e la futura PR sono solo documentali.

### 7.21 Decisione confermata — consentire un obiettivo già raggiunto senza annuncio retroattivo (A)

**Decisione esplicita dell’utente:** durante l’allenamento è possibile salvare un obiettivo di distanza **inferiore alla distanza già percorsa**. La UI mostra **«Obiettivo già raggiunto»** e **non produce alcun annuncio vocale retroattivo**. Per coerenza e per evitare un confine ambiguo si applica lo stesso comportamento quando la distanza target è **esattamente uguale** alla distanza corrente.

**Flusso e messaggi:**

1. Nella pagina «Opzioni allenamento → Modifica obiettivo» l’utente inserisce una distanza valida e preme «Salva obiettivo»; la validazione non blocca valori soltanto perché già superati.
2. Se `target <= distanza totale attendibile al salvataggio`, conservare il nuovo target e visualizzare **«Obiettivo già raggiunto»** nel riepilogo di sessione e nell'area obiettivo. Non mostrare «Mancano −1 km» né simulare un obiettivo ancora da percorrere.
3. Il salvataggio **non equivale a un nuovo evento di traguardo**: niente TTS «Obiettivo di … raggiunto, in …», niente tempo di raggiungimento inventato e niente recupero di annunci alla fine dello Sprint. La regola di §7.19 si applica solo a un target **ancora futuro** e poi realmente raggiunto con dati attendibili.
4. I checkpoint già raggiunti rimangono registrati e seguono il proprio intervallo, senza duplicati. Il cambio di target non crea o elimina checkpoint.
5. Tracciamento, cronometro, Mappa e Sprint non si interrompono. Il target resta modificabile: se successivamente l’utente imposta un obiettivo **superiore alla distanza corrente**, riprende il normale progresso verso il prossimo evento di raggiungimento.
6. Se la qualità GPS non permette di conoscere in modo attendibile la distanza corrente, evitare un giudizio di traguardo arbitrario: conservare la preferenza salvata, segnalare l’incertezza e aggiornare lo stato soltanto quando la distanza sarà attendibile. Non inventare un evento audio.

**Esempio di accettazione:** a **4,0 km**, salvare **3,0 km** → target aggiornato e indicazione «Obiettivo già raggiunto»; nessun annuncio vocale o tempo storico fittizio. A **4,0 km**, salvare **4,0 km** → stesso esito. A **4,0 km**, salvare **6,0 km** → «Mancano 2,0 km», annuncio con il tempo effettivo soltanto al successivo raggiungimento reale dei 6 km. Un semplice cambio di vista o riapertura delle opzioni non deve riprodurre annunci.

**Tracciabilità:** decisione di progetto che aggiorna il caso prima aperto in §7.20; non descrive il comportamento implementato oggi. La gestione del raggiungimento e la deduplicazione dopo modifiche successive richiedono verifica tecnica.

### 7.22 Decisione confermata — una sola pagina «Opzioni allenamento»

**Decisione esplicita dell’utente:** raccogliere **obiettivo di distanza, checkpoint vocali e configurazione Sprint** in un'unica **pagina indice «Opzioni allenamento»**, raggiungibile sia da **Statistiche** sia da **Mappa live**. È una pagina di impostazioni e navigazione, **non** un passaggio obbligatorio prima di avviare o continuare una sessione. Non sostituisce il grande pulsante Sprint: **«Avvia sprint» rimane sempre visibile e fisso nelle due viste operative**, mentre le sue opzioni restano secondarie.

**Gerarchia proposta della pagina:**

1. **Intestazione:** «Opzioni allenamento»; ritorno chiaramente etichettato alla vista operativa di provenienza. Una riga discreta informa «L’allenamento continua» quando la sessione è attiva.
2. **«Obiettivo di distanza»** con stato sintetico («Nessun obiettivo», «5 km · Mancano 1,8 km», «Obiettivo già raggiunto»). Toccare la riga apre «Modifica obiettivo» con salvataggio/rimozione e regole già confermate (§7.19–§7.21).
3. **«Checkpoint vocali»** con stato sintetico («Attivi · Ogni 1 km», «Attivi · Ogni 750 m» oppure «Disattivati»). Toccare la riga apre la **pagina dedicata «Checkpoint vocali»** per attivazione, intervalli predefiniti e valore libero (§7.16–§7.18). La pagina indice non duplica i campi dell’editor.
4. **«Configurazione Sprint»** con stato sintetico («50 m · Countdown 5 s», «30 s · Countdown 5 s», «Non configurato»). Toccare la riga apre l’editor del preset persistente, **solo se la prova non è occupata** (§7.10.7–§7.10.8). Durante preparazione TTS/countdown/Sprint attivo, mantenere la voce visibile ma non azionabile, con spiegazione «Modifica sprint disponibile al termine della prova».
5. **Nessun altro controllo operativo ad alto rischio nella pagina:** non inserire «Termina e salva», «Annulla sprint» né un secondo grande «Avvia sprint». L’avvio e la pressione di 2 s per annullare rimangono nella stessa superficie del dock delle viste Statistiche/Mappa (§7.10).

**Navigazione e dati:**

- Il comando «Opzioni allenamento» è disponibile **da entrambe le viste operative** con la stessa etichetta e una posizione riconoscibile, ma non deve competere visivamente con la CTA Sprint.
- L’ingresso conserva la **vista di provenienza** e, dalla Mappa, stato di camera, inquadratura e selezione del percorso; tornare alle viste operative **senza interrompere GPS, cronometro, classificazione o registrazione**. Non spostare il dock Sprint nelle viste operative, né cambiarne la geometria per aggiungere il collegamento alle opzioni.
- Ciascun editor gestisce **la propria bozza**: «Salva» applica soltanto impostazioni valide di quella funzione, «Annulla»/Indietro abbandona le modifiche non salvate. Il comportamento di ritorno dopo il salvataggio deve rispettare quello delle rispettive pagine già definito, consentendo di recuperare il contesto di partenza. Non sovrascrivere accidentalmente le altre due preferenze.
- Il preset Sprint resta persistente tra sessioni; checkpoint vocali abilitati di default ogni 1 km **solo in assenza di una scelta esplicita pregressa**; l’**obiettivo non viene invece ereditato da una sessione conclusa** e ogni nuovo allenamento è libero salvo scelta esplicita per quella sessione (§7.23). La pagina indice riflette le impostazioni realmente salvate e non mostra dati provvisori come se fossero applicati.
- In caso di assenza di sessione (accesso da Home/Impostazioni), mostrare gli stessi ingressi per la preconfigurazione facoltativa, senza falsi stati «allenamento in corso»; l’eventuale modifica dell'obiettivo segue le regole già definite per la Home.
- Il layout è leggibile con caratteri grandi e tecnologie assistive: ogni riga è una singola area di tocco, ha nome e stato accessibili anche senza colori; la voce Sprint disabilitata espone il motivo. La pagina non richiede gesti precisi né scrolling mentre si corre: le modifiche sono attività da svolgere quando è sicuro interagire.

**Wireframe testuale (esempio, non dati reali):**

```text
← Allenamento                         Opzioni allenamento
L’allenamento continua

[Obiettivo di distanza                        >]
  5 km · Mancano 1,8 km

[Checkpoint vocali                            >]
  Attivi · Ogni 1 km

[Configurazione Sprint                        >]
  Sprint di 50 m · Countdown 5 s
```

**Criteri di accettazione:**

- Da Statistiche e da Mappa si arriva alla stessa pagina con **un solo ingresso secondario**; da lì i tre editor sono riconoscibili senza dover cercare tra statistiche o tab.
- Durante sessione attiva, apertura, modifica, salvataggio e ritorno non fermano il tracking; un rientro da Mappa conserva camera e selezione. Il pulsante Sprint rimane **nella stessa posizione** nelle viste operative.
- Con Sprint inattivo, l’editor del preset è raggiungibile; con preparazione/countdown/prova attivi, il relativo ingresso è disabilitato e spiega il motivo. Obiettivo e checkpoint restano voci indipendenti, soggette alle proprie regole.
- Un salvataggio dei checkpoint aggiorna immediatamente solo i checkpoint futuri, come §7.18; un obiettivo inferiore/uguale alla distanza già percorsa viene salvato senza annuncio retroattivo, come §7.21; il preset Sprint salvato vale per le prove successive.
- Le bozze annullate non hanno effetti, i riepiloghi della pagina sono aggiornati solo dai valori salvati e l’editing di una funzione non cambia le altre.
- L’avvio diretto dalla Home resta possibile senza aprire «Opzioni allenamento»; la pagina è configurazione facoltativa, non una barriera all'inizio della registrazione.

**Confine della proposta:** architettura informativa e navigazione target; non afferma che la pagina esista già nel codice attuale e non implementa nuove Activity o modifica servizi. Dimensioni, ordine/posizione del collegamento e comportamento preciso del sistema Indietro vanno verificati su dispositivo.

### 7.23 Decisione confermata — ogni nuovo allenamento riparte senza obiettivo di distanza (B)

**Decisione esplicita dell’utente:** il target di distanza **non è una preferenza persistente tra sessioni**. Un allenamento nuovo deve partire **libero, senza obiettivo**, salvo che l’utente scelga volontariamente una distanza per quella specifica sessione. Questo differenzia l’obiettivo dal **preset Sprint** e dai **checkpoint vocali**, che invece conservano le rispettive preferenze fra allenamenti.

**Regole UX:**

1. **Home prima di un nuovo allenamento:** stato iniziale «Obiettivo: nessuno · Allenamento libero»; «Modifica obiettivo» è facoltativo e non blocca «Inizia allenamento». Non ricopiare il target dell’ultima sessione né proporre tacitamente quello usato più spesso.
2. **Selezione esplicita pre-avvio:** se l’utente imposta ad esempio 5 km prima di partire, quel target viene applicato alla **nuova sessione imminente**. Se il flusso di avvio viene fermato dai permessi, da GPS debole o da «Annulla», la bozza selezionata resta disponibile per **riprovare lo stesso avvio**, senza confonderla con un automatismo che eredita il target della sessione precedente.
3. **Durante la sessione attiva:** «Opzioni allenamento → Obiettivo di distanza» mostra il valore effettivo di quella sessione e permette di modificarlo o rimuoverlo secondo §7.20–§7.21. Tornare a Home, passare a Mappa o riprendere il tracking dopo un’interruzione **non costituisce un nuovo allenamento** e non resetta l’obiettivo corrente.
4. **Alla conclusione:** il riepilogo e lo Storico mantengono il target effettivamente associato alla sessione terminata, quando presente. **Per il successivo nuovo allenamento, l’obiettivo torna assente**; non cancellare retroattivamente dai dati storici gli obiettivi completati o impostati.
5. **Nessun annuncio fittizio:** una nuova sessione senza target non genera un annuncio «Obiettivo raggiunto»; continuano a valere i checkpoint vocali predefiniti ogni 1 km, indipendenti dall’obiettivo. Se l’utente imposta successivamente un target durante la corsa, applicare le normali regole per target futuro o già raggiunto.
6. **Comunicazione chiara:** la Home e «Opzioni allenamento» devono distinguere «Nessun obiettivo» da «Obiettivo di 5 km»; non mostrare «Mancano …» dove non esiste un target. Un avvio con obiettivo preimpostato deve confermare visivamente che verrà applicato a quella sessione.

**Casi di accettazione:**

- Sessione A con obiettivo 5 km → «Termina e salva» → Home inattiva → avvio di sessione B senza intervenire sulle opzioni: **nessun obiettivo nella sessione B**; sessione A e relativo riepilogo conservano il dato storico.
- Home per nuova sessione → selezione volontaria 7 km → avvio GPS annullato → nuovo tentativo di avvio: **7 km rimane selezionato per il tentativo non concluso**; non è una preferenza di default delle sessioni future.
- Sessione in corso con obiettivo 5 km → app in background, passaggio alla Mappa, oppure recupero tracking: **l’obiettivo resta 5 km**, perché la sessione è la stessa.
- Nuova sessione senza obiettivo: **checkpoint vocali** eventualmente attivi secondo la scelta persistente (default 1 km), **preset Sprint** persistente, **nessun messaggio vocale di obiettivo** non impostato.

**Confine della proposta:** comportamento UX richiesto da verificare/implementare nel ciclo di vita di Home, avvio, sessione attiva e archivio; non afferma che il codice attuale separi già target di sessione e preferenza ereditata.

## 8. Mappa GPS live e dettaglio del tratto — specifica UX proposta

### 8.1 Decisioni approvate e obiettivo

- **Decisione approvata — mappa A:** la mappa live è una vista dedicata quasi a schermo intero; **distanza complessiva, durata complessiva e ritmo attuale (min/km)** restano visibili in una fascia compatta. Il tracciamento continua mentre si consulta la mappa.
- **Decisione approvata — Sprint nella Mappa A:** durante una sessione attiva il grande comando Sprint occupa **lo stesso dock inferiore** della vista statistiche; nessun ritorno ad Allenamento è richiesto per avviare una prova già configurata.
- **Decisione approvata — dettagli B:** toccare un tratto GPS dalla mappa o dall'elenco apre **una pagina autonoma dedicata al tratto**, non un pannello inferiore né un `AlertDialog`. La pagina presenta tutte le statistiche disponibili di *quel tratto*, con un comando chiaramente riconoscibile «← Torna alla mappa».
- **Decisione approvata — mini-mappa A:** la pagina di dettaglio include **una mini-mappa contestuale** con il tratto selezionato in evidenza. Se le tessere o i punti GPS non sono disponibili, la pagina rimane utile e spiega chiaramente il limite.

Queste sono scelte **UX target**, non descrizioni dell'interfaccia attuale. Nel codice analizzato selezione, evidenziazione, zoom e statistiche dei tratti esistono già; attualmente il tap sulla polilinea apre un `AlertDialog`, mentre l'elenco seleziona il tratto nella mappa. La navigazione alla pagina dedicata è da realizzare in un intervento successivo alla PR documentale.

### 8.2 Gerarchia della vista Mappa

1. **Testata:** «← Allenamento», stato GPS espresso anche con testo; comando di fine sessione solo se collocato in un'area distinta e con conferma (non confonderlo con i comandi di mappa).
2. **Tre metriche compatte e sempre visibili:** distanza della *sessione*, durata della *sessione*, ritmo *attuale*; in caso di ritmo non affidabile, `--:-- min/km`, non un dato vecchio presentato come corrente.
3. **Mappa dominante:** posizione corrente quando valida, tracciato e tipi di movimento; non disegnare collegamenti attraverso perdite prolungate di GPS.
4. **Azioni di camera:** «Seguimi» (posizione live) e «Mostra percorso» (inquadratura del percorso) sono distinte. Un gesto di trascinamento/zoom porta in esplorazione senza interrompere il tracking.
5. **Accesso all'elenco dei tratti:** comando «Tratti (N)», che apre un elenco leggero di numero, tipo, distanza e durata; scegliere una riga apre la **pagina di dettaglio**, esattamente come toccare una polilinea. L'elenco non diventa il luogo delle statistiche complete.
6. **Legenda testuale:** Camminata, Corsa, Sprint, Altro/incerto; non affidare il significato esclusivamente ai colori.
7. **Dock Sprint persistente:** comando «⚡ Avvia sprint» in basso, **sempre visibile e nella stessa identica posizione/dimensione della vista Allenamento**, con preset sintetico; durante preparazione/countdown/prova la **stessa grande superficie** diventa «Tieni premuto 2 s per annullare» (un tap breve non annulla), con avanzamento visivo della tenuta, senza spostare la mappa o consentire altri avvii. La mappa deve poter occupare quasi tutto lo spazio rimanente, senza che il dock ne copra i controlli o i tratti GPS. Vedere §7.10.6.

*Indicazione visuale:*

```text
MAPPA LIVE
┌─────────────────────────────────────┐
│ ← Allenamento             GPS buono │
│ 3,42 km       24:18      6:12 min/km│
├─────────────────────────────────────┤
│                                     │
│          PERCORSO GPS               │
│         (tocca un tratto)           │
│                        [Seguimi]    │
│                        [Percorso]   │
│                                     │
├─────────────────────────────────────┤
│ [ Tratti del percorso (4) ]         │
├─────────────────────────────────────┤
│       ⚡ AVVIA SPRINT               │
│        50 m · 5 s                   │
└─────────────────────────────────────┘
```

### 8.3 Comportamento della mappa e passaggio al dettaglio

| Situazione | Comportamento UX target |
|---|---|
| Apertura della mappa live con posizione valida | Inizialmente segue la posizione corrente e mostra il percorso disponibile |
| Pan o zoom manuale | Esplorazione libera senza ricentratura automatica; la registrazione continua |
| «Seguimi» | Torna alla posizione corrente e al seguimento live |
| «Mostra percorso» | Inquadra il percorso registrato disponibile senza alterare la registrazione |
| Tap su polilinea di un tratto | Memorizza tratto scelto e camera/posizione di esplorazione; apre la **pagina Dettaglio tratto** |
| «Tratti del percorso» → riga | Apre la **stessa pagina Dettaglio tratto**, senza una variante informativa diversa |
| «← Torna alla mappa» dal dettaglio | Ripristina l'area geografica/zoom e il tratto selezionato o evidenziato, senza ricentrare improvvisamente |
| «Deseleziona» sulla mappa | Rende visibili tutti i tratti normalmente; non implica automaticamente «Seguimi» |
| Tap su «Avvia sprint» nella Mappa live (preset pronto) | Avvia subito il countdown **senza lasciare la Mappa**; usa lo stesso stato e lo stesso preset della vista Allenamento |
| Passaggio Mappa ↔ Allenamento durante countdown/prova | Mantiene la fase Sprint effettiva e impedisce l’avvio duplicato; il dock aggiorna solo il contenuto **senza cambiare geometria**. Una tenuta d’annullamento interrotta non produce alcun comando |
| Conclusione Sprint mentre è aperta la Mappa | Breve feedback vocale e riepilogo non modale sovrapposto **fuori dal dock**, nessun passaggio forzato alle statistiche; «Avvia sprint» torna pronto nella medesima posizione |

La conservazione precisa della camera, la distinzione dei comandi e la navigazione alla pagina sono requisiti **da implementare/verificare**; il comportamento attuale del codice non garantisce tutti questi passaggi. La modalità Tratto selezionato non deve ridurre l'area della mappa con un pannello di dettaglio.

### 8.4 Pagina dedicata «Dettaglio tratto GPS»

**Principio:** focalizzarsi su un solo tratto, distinguendone chiaramente i valori da quelli dell'allenamento complessivo. Non duplicare tutte le statistiche della sessione nella pagina.

**Contenuti, in ordine:**

1. **Navigazione:** freccia e testo «Torna alla mappa»; nell'allenamento attivo, stato breve «Registrazione in corso» (non un comando di stop). Nello storico, nessuna indicazione falsa di registrazione in corso.
2. **Identificazione:** «Tratto 3 · Corsa» oppure Camminata/Sprint/Altro; se il tratto è ancora in acquisizione, indicare «Tratto in corso · valori provvisori». Per un segmento incerto, non presentare una classificazione certa.
3. **Dati principali del tratto:** **distanza** e **durata** in grande; se una metrica è assente, `—` con spiegazione, senza inventare uno zero.
4. **Dati secondari disponibili:** velocità media e massima (km/h), orario iniziale e finale quando noto, campioni GPS validi; mantenere le etichette e le unità. Non promettere un ritmo o una cadenza *per tratto* se non previsti dai dati attuali.
5. **Mini-mappa contestuale (approvata):** rappresentazione compatta focalizzata sulla geometria del tratto selezionato, dopo distanza e durata e prima dei dati secondari. Il tratto è evidenziato con colore e label, mentre il resto del percorso, se disponibile, è attenuato; non usare linee di raccordo non registrate. Mostrare solo posizioni e marcatori supportati da dati attendibili. La mini-mappa nasce come anteprima *non interattiva* (niente pan/zoom che compete con lo scorrimento della pagina), con azione esplicita «Apri sulla mappa» per esplorare il tratto nella vista geografica principale. In assenza di tessere, preferire il percorso su sfondo neutro, se la geometria è disponibile; con geometria insufficiente, mostrare un messaggio. Non richiede nuove funzionalità di routing.
6. **Trasparenza:** nota sintetica «Le misure sono stime basate sui campioni GPS e possono differire dai totali della sessione». Per sprint storici approssimativi, indicare «Ricostruzione stimata»; per campioni insufficienti evitare numeri non attendibili.
7. **Navigazione facoltativa:** «Tratto precedente» / «Tratto successivo», da validare in test, per confrontare più segmenti senza continue uscite; sempre chiaro il numero/tipo del tratto attivo.

**Wireframe testuale, valori fittizi:**

```text
DETTAGLIO TRATTO GPS
┌──────────────────────────────────────┐
│ ← Torna alla mappa                  │
│ Tratto 3 · Corsa                    │
│ Registrazione in corso              │
├──────────────────────────────────────┤
│ DISTANZA DEL TRATTO                  │
│ 1,42 km                              │
│                                      │
│ DURATA DEL TRATTO                    │
│ 08:46                                │
├──────────────────────────────────────┤
│ MINI-MAPPA DEL TRATTO               │
│ [ percorso selezionato evidenziato ] │
│ [ Apri sulla mappa ]                 │
├──────────────────────────────────────┤
│ Velocità media       9,7 km/h        │
│ Velocità massima     12,1 km/h       │
│ Campioni GPS         54              │
│ Orario inizio        18:10           │
│ Orario fine          18:19           │
│ Le misure sono stime GPS.            │
├──────────────────────────────────────┤
│ [Tratto precedente] [Successivo]     │
└──────────────────────────────────────┘
```

Il wireframe non descrive la UI implementata; i dati sono illustrativi. **La mini-mappa è una decisione confermata**, ma vanno verificati dimensioni, resa con font grandi, accessibilità e prestazioni. In assenza di geometria attendibile la regione della mini-mappa deve mostrare uno stato informativo, non essere rimossa silenziosamente. Posizionamento dei controlli precedente/successivo e comportamento in corso richiedono prototipo/test.

### 8.4.1 Specifica UX della mini-mappa — decisione confermata (A)

**Scopo:** dare un riferimento geografico immediato al dato «Tratto N», senza duplicare l'esplorazione della mappa a schermo quasi intero.

**Requisiti di contenuto e comportamento:**

1. **Identità prima di tutto:** mini-mappa collocata sotto distanza e durata; intestazione «Percorso del tratto». Mostrare il solo tratto selezionato in evidenza, con etichetta testuale «Tratto N · [tipo]». Porzioni vicine del percorso possono apparire attenuate per contesto, solo se esistono come punti registrati.
2. **Inquadratura automatica stabile:** al primo caricamento inquadrare la geometria valida del tratto con un margine adeguato. Se il tratto è ancora in corso, aggiornare il segmento senza spostare continuamente la visuale a ogni punto; offrire in una vista estesa il comportamento di seguimento, non nella mini-mappa.
3. **Anteprima compatta, non seconda mappa da manovrare:** nessun gesto pan/zoom sulla mini-mappa nella prima proposta. Un pulsante nominato **«Apri sulla mappa»** passa alla mappa principale con *quel tratto* evidenziato e inquadrato. **«Torna alla mappa»** in testata, invece, ripristina selezione e inquadratura esistenti prima di aprire il dettaglio. Non confondere le due azioni.
4. **Dati onesti:** non inventare punti, ponti grafici dopo interruzioni GPS, posizione di inizio/fine o velocità quando le prove non sono sufficienti. Per uno sprint storico ricostruito, la dicitura «Ricostruzione stimata» resta visibile anche vicino alla mini-mappa se i confini geografici sono approssimativi.
5. **Offline ed errori:** se manca lo sfondo cartografico, ma sono presenti coordinate registrate, mostrare il tracciato su una superficie neutra e il messaggio «Sfondo mappa non disponibile offline». Se mancano punti sufficienti, mostrare «Percorso del tratto non disponibile» con motivazione breve, lasciando leggibili le altre statistiche.
6. **Privacy e prestazioni:** riutilizzare la cartografia OpenStreetMap e i dati locali; la vista non deve caricare tessere non necessarie o inviare l'intero tracciato a servizi remoti. Le richieste delle tessere online possono comunque comunicare al relativo fornitore l'area geografica visualizzata, come già avviene nelle mappe dell'app.
7. **Accessibilità:** offrire descrizione testuale equivalente (es. «Tratto 3, corsa, 1,42 km; percorso disponibile»), nome accessibile per «Apri sulla mappa», stato non veicolato solo dal colore e navigazione che non imponga gesti sulla cartografia.

**Stati da rappresentare nella mini-mappa:**

| Stato | Risposta UX |
|---|---|
| Tratto con punti validi e cartografia disponibile | Segmento evidenziato e centrato, eventuale contesto attenuato |
| Punti validi ma tessere non disponibili | Tracciato su sfondo neutro, nota sullo sfondo assente |
| Un solo punto o geometria non sufficiente a rappresentare il segmento | Punto registrato se utile, altrimenti messaggio chiaro; nessuna linea inventata |
| Tratto live in aggiornamento | Etichetta «Valori provvisori», geometria aggiornata senza salti di camera continui |
| Nessun punto GPS attendibile | «Percorso del tratto non disponibile»; non visualizzare una mini-mappa generica fuorviante |

**Nota di implementazione differita:** nel commit analizzato `TrackMap` è una mappa completa condivisa fra schermate; una mini-mappa dedicata e la sua modalità di anteprima sono requisiti UX nuovi, da progettare tecnicamente in una PR futura, non parte di questa PR documentale.

### 8.5 Colori, legenda e accessibilità

| Tipo di tratto | Colore presente nel codice analizzato | Etichetta proposta |
|---|---|---|
| Camminata | Verde | «Camminata» |
| Corsa | Blu | «Corsa» |
| Sprint | Rosso | «Sprint» |
| Altro, fermo o incerto | Grigio | «Altro / incerto», con spiegazione del significato |

I colori correnti non sono un vincolo grafico definitivo. Usare **testo, forme o spessore** oltre al colore; etichette semantiche per TalkBack e un elenco accessibile anche quando le polilinee sono difficili da toccare.

### 8.6 Stati particolari e messaggi

| Situazione | Comportamento UX proposto |
|---|---|
| Nessuna posizione GPS | «In attesa della posizione GPS»; niente marker o dati inventati |
| Tratti non ancora disponibili | «I tratti appariranno dopo i primi punti GPS validi»; niente pagina dettaglio vuota fittizia |
| GPS impreciso | Avviso sulla qualità delle stime; conservare percorso già attendibile |
| Ritmo attuale non affidabile sulla mappa | `--:-- min/km`; nessun riuso del ritmo medio o dell'ultimo valore come corrente |
| Nessuna rete / tessere non in cache | «Sfondo mappa non disponibile offline. La registrazione continua»; nella mini-mappa mantenere la geometria su sfondo neutro se disponibile |
| Discontinuità GPS | Non unire segmenti distanti con linee inventate |
| Tratto ancora in formazione | «Tratto in corso · valori provvisori»; aggiornarne i dati senza farlo apparire concluso |
| Statistiche non calcolabili | Segnaposto `—` e ragione comprensibile; non confonderlo con 0 |
| Sprint storico ricostruito | «Ricostruzione stimata» vicino alle misure pertinenti |
| La sessione è interrotta | Stato e ultimo dato valido; non dichiarare che il tracking continui |

### 8.7 Navigazione e continuità dell'allenamento

- **Allenamento → Mappa → Dettaglio tratto → Mappa:** il ritorno dal dettaglio deve riportare alla mappa con **stesso tratto evidenziato, zoom e posizione geografica**, non all'inizio della sessione o alla Home.
- Il pulsante **Indietro** di Android e l'azione «Torna alla mappa» devono essere coerenti; non perdere la selezione per una semplice navigazione.
- **Mappa → Allenamento:** la sessione continua. L'eventuale comando «Termina e salva» disponibile in mappa richiede sempre conferma; evitare di ripeterlo nella pagina del tratto, dove potrebbe essere confuso con la chiusura del dettaglio.
- Nello **storico**, riutilizzare la stessa pagina di dettaglio tratto e mini-mappa con informazioni archiviate: niente «Seguimi», stato di registrazione o ritmo attuale se non applicabile.
- Distinguere **«Torna alla mappa»** (ripristina la camera precedente) da **«Apri sulla mappa»** sotto la mini-mappa (inquadra il tratto selezionato); in entrambi i casi, la sessione live continua.
- Se durante l'apertura del dettaglio arrivano nuovi campioni o nasce un nuovo tratto, mantenere l'identità del tratto selezionato; non passare automaticamente a quello successivo.

### 8.8 Criteri di accettazione UX

1. La mappa si apre dalla sessione con un tocco, conserva le tre metriche essenziali e non interrompe il tracking.
2. Un tap sulla polilinea e un tap sulla riga dello stesso tratto aprono la **stessa pagina**, con stessi dati e identità.
3. Il dettaglio mostra subito tipo di tratto, distanza e durata **del tratto**, distinguendoli esplicitamente dai totali della sessione.
4. Il ritorno alla mappa conserva tratto selezionato e contesto di esplorazione, anche dopo pan/zoom precedente.
5. La vista dettaglio non espone un pulsante ambiguo che possa terminare l'allenamento per errore.
6. Valori assenti, incerti o provvisori e sprint ricostruiti sono descritti correttamente.
7. La pagina funziona per sessioni in corso e storiche senza stati di registrazione impropri.
8. La pagina presenta una mini-mappa focalizzata sul tratto, senza richiedere gesti per usarla; «Apri sulla mappa» inquadra il tratto, mentre «Torna alla mappa» conserva la vista precedente.
9. Offline, con GPS debole o con tessere mancanti si riescono a leggere i dati disponibili senza promettere una cartografia completa: se c’è geometria, la mini-mappa può mostrarla anche senza tessere; altrimenti esplicita il limite.
10. TalkBack, caratteri grandi, aree di tocco e navigazione Indietro sono utilizzabili; la mini-mappa ha un equivalente testuale e tipo/selezione non sono comunicati solo tramite colore.
11. «Seguimi», «Mostra percorso» e «Deseleziona» hanno effetti diversi e comprensibili.

### 8.9 Questioni ancora aperte da testare (non decisioni approvate)

- Mini-mappa **approvata**: da validare dimensioni, rapporto con le statistiche, resa senza tessere e costi di rendering su telefoni reali (non rimettere in discussione la presenza).
- Pulsanti «Precedente» / «Successivo»: velocizzano l'analisi oppure generano tocchi accidentali durante il movimento?
- Elenco dei tratti: meglio come schermata dedicata o sezione compatta sovrapposta alla mappa? **Il dettaglio rimane comunque una pagina autonoma**, come deciso.
- Dove mantenere il comando «Termina» senza affollare la mappa e senza introdurlo nella pagina del tratto?
- Ripristino di zoom e selezione dopo rotazione, passaggio in background e riapertura della mappa.

## 9. Fine allenamento e riepilogo finale — specifica UX proposta

### 9.1 Decisione condivisa, obiettivo e ambito

**Decisione approvata — opzione A:** dopo aver terminato una sessione e averne verificato il salvataggio locale, PassoLibero **apre automaticamente un riepilogo immediato**. Non riporta direttamente alla Home né obbliga a cercare l'allenamento nello Storico. Questa è una proposta UX su dati già disponibili; l'apertura automatica e l'eventuale schermata dedicata sono **da implementare in una futura PR di codice**, non nella PR documentale.

Obiettivo: in pochi secondi, rispondere a «Quanto ho fatto?», «Quanto è durato?», «Come è andata?» e «Dove sono passato?». Mantenere lo stesso linguaggio per corsa e camminata, senza giudizi sull'andatura o elementi celebrativi obbligatori.

**Base attuale verificata:** l'interfaccia di `ActiveSessionActivity.java` ha il pulsante «Termina e salva la sessione» con conferma; `TrackingService.java` persiste lo snapshot conclusivo locale nel flusso di stop; `SessionDetailActivity.java` mostra metriche della sessione, mappa, sprint, obiettivo e tracce; lo storico apre oggi i dettagli di una sessione. **Non risulta già implementata una navigazione automatica post-salvataggio verso un riepilogo dedicato**. La UI deve attendere un esito confermato, non assumere che l'invio del comando STOP garantisca il salvataggio.

### 9.2 Flusso di uscita sicuro

1. **Allenamento attivo:** «Termina allenamento» sempre raggiungibile, ma visivamente distinto da «Torna alla Home» e «Annulla sprint».
2. **Conferma:** messaggio «Terminare e salvare l’allenamento? I risultati saranno disponibili nello storico.» Azioni «Continua allenamento» e «Termina e salva»; il sistema non termina se la conferma viene annullata.
3. **Salvataggio:** mostrare «Salvataggio dell’allenamento…», con progressione indeterminata se non misurabile; impedire comandi ripetuti di stop che potrebbero produrre risultati ambigui. Non mostrare un messaggio «Salvato» anticipato.
4. **Esito positivo verificato:** visualizzare subito il riepilogo della *sessione appena conclusa*, identificata tramite ID stabile; nessuna necessità di aprire manualmente lo Storico.
5. **Errore o esito incerto:** non simulare un risultato concluso; mostrare «Non siamo riusciti a confermare il salvataggio» e azioni di recupero coerenti con lo stato reale. Prima di offrire «Riprova» verificare se il record è già presente, per non creare duplicati. Dettagli tecnici da progettare con l'implementazione.
6. **Navigazione dal riepilogo:** «Torna alla Home» come uscita semplice; «Esplora dettagli» per l'analisi; «Apri mappa» per il percorso. Tornando indietro dopo una sessione conclusa, non riaprire una vista apparentemente ancora in registrazione.

**Stato avvio conclusione:** se un nuovo stop viene richiesto mentre uno è in fase di salvataggio, non lanciare una seconda chiusura. Un eventuale sprint attivo viene gestito secondo le regole esistenti; non far passare il suo annullamento per la conclusione dell'intero allenamento.

### 9.3 Gerarchia del riepilogo

1. **Testata di conferma:** «Allenamento salvato», con data e ora della sessione e indicazione di completata/non completata quando pertinente; «salvato» descrive il successo di persistenza, non la qualità sportiva.
2. **Numero protagonista — distanza complessiva:** grande, in km. È il totale registrato della sessione, **non** la somma dei soli tratti classificati e **non** la somma di tutti gli allenamenti.
3. **Due statistiche secondarie:** durata **totale** e **ritmo medio complessivo (min/km)**. Durante la sessione la terza metrica è il **ritmo attuale**; dopo la conclusione diventa il **ritmo medio**. I valori devono usare la stessa definizione esposta nello storico. Se il tempo totale comprende soste, dichiararlo nel dettaglio/metadati: non spacciarlo per ritmo calcolato soltanto in movimento.
4. **Ripartizione dell'attività:** una rappresentazione compatta di corsa, camminata e quota non attribuita, usando metriche compatibili fra loro (es. tempo per categorie); chiarire cosa rappresentano movimento non classificato, movimento incerto e soste. Non attribuire automaticamente la parte incerta a corsa o camminata. La somma delle categorie va verificata sulla medesima base temporale prima di mostrare grafici proporzionali.
5. **Obiettivo (solo se impostato):** avanzamento finale, raggiunto oppure distanza rimanente. Evitare messaggi valutativi negativi se un obiettivo non è stato completato. Senza obiettivo, non inserire una scheda vuota.
6. **Anteprima del percorso GPS:** riquadro secondario che inquadra il tracciato disponibile, con «Apri mappa». La mappa non compete con i numeri principali. Se lo sfondo cartografico manca offline, il tracciato valido può restare su sfondo neutro; se manca anche la geometria, mostrare un messaggio esplicito.
7. **Uscita primaria dopo il salvataggio:** «Torna alla Home» è il pulsante più evidente della schermata di riepilogo immediato, raggiungibile senza scorrere fino al termine dei contenuti. La Home si presenta **inattiva**, con «Inizia allenamento»: non aprire una nuova sessione automaticamente.
8. **Approfondimento secondario:** «Esplora dettagli» è sempre riconoscibile e accessibile, ma non compete visivamente con il pulsante Home. Porta a velocità, passi/cadenza, tempi in movimento/da fermo, sprint, checkpoint e sezioni GPS. «Apri mappa» è un'azione contestuale dell'anteprima percorso. Esportazione JSON e cancellazione restano azioni secondarie; nessuna nuova condivisione social.
9. **Provenienza dallo Storico:** usare gli stessi dati e la stessa impaginazione; il comando di ritorno contestuale diventa «Torna allo Storico» (o freccia Indietro), per non deviare alla Home chi sta consultando una vecchia sessione.

### 9.4 Distinzione delle grandezze

| Informazione | Semantica UX e limite |
|---|---|
| Distanza complessiva | Distanza registrata nella singola sessione, inclusiva delle porzioni valide non attribuite alle due andature; **non** distanza cumulativa dell'intero storico |
| Durata totale | Tempo dall'avvio alla fine, non sinonimo di solo tempo in movimento |
| Ritmo medio complessivo | Etichetta chiaramente distinta da «ritmo attuale»; segue la metrica storica della sessione (tempo e distanza corrispondenti), con `--:--` se non calcolabile |
| Tempo corsa/camminata | Valori derivati da periodi effettivamente classificati; non assorbono automaticamente il movimento non classificato o incerto |
| Movimento non classificato/incerto | Tipi diversi di incertezza; vanno mantenuti identificabili nelle analisi anche se il riepilogo li compatta |
| Distanze per andatura | Non necessariamente equivalenti al totale GPS; non presentare la loro somma come distanza complessiva senza esplicitare l'esclusione |
| Obiettivo | Dato della **sessione appena conclusa**, mostrato solo se presente; nessuna promessa di traguardi o premi non implementati |
| Percorso GPS | Potrebbe essere parziale o mancante: il riepilogo resta utilizzabile anche senza mappa |

### 9.5 Wireframe testuale — valori inventati

```text
┌────────────────────────────────┐
│ ✓ Allenamento salvato          │
│ Domenica · 15 novembre         │
│                                │
│ DISTANZA COMPLESSIVA           │
│            5,24 km             │
│                                │
│ DURATA TOTALE    RITMO MEDIO   │
│    38:12         7:17 min/km   │
│                                │
│ LA TUA ATTIVITÀ                │
│ Corsa: 18:10 · Camminata: 16:02│
│ Altri periodi: vedi dettagli   │
│                                │
│ Obiettivo 5 km · Raggiunto ✓   │  ← solo se impostato
│                                │
│ [Anteprima percorso GPS]       │
│ [ Apri mappa ]                 │
│                                │
│ [ Torna alla Home ]  PRIMARIO  │
│ [ Esplora dettagli ] SECONDARIO│
└────────────────────────────────┘
```

I dati sono esemplificativi e non descrivono sessioni reali. Il ritmo medio nel wireframe è illustrativo; lo sviluppo dovrà usare i valori calcolati e salvati dall'app. La mappa è facoltativa nella disponibilità dei dati, **non** una funzione di tracciamento aggiuntiva.

### 9.6 Stati e microcopy

| Stato | Messaggio | UX richiesta |
|---|---|---|
| Conferma di conclusione | «Terminare e salvare l’allenamento?» | Possibilità chiara di annullare e continuare |
| In attesa del salvataggio | «Salvataggio dell’allenamento…» | Non mostrare successo finché non verificato |
| Salvato | «Allenamento salvato» | Passaggio automatico al riepilogo della sessione corretta |
| Fallimento/esito sconosciuto | «Non è stato possibile confermare il salvataggio» | Non falsificare riuscita; recuperare il record senza duplicarlo |
| Distanza zero / ritmo invalido | «Ritmo medio non disponibile» | Segnaposto esplicito, non `0:00` ingannevole |
| Nessun obiettivo | Nessuna card obiettivo | Evitare un blocco irrilevante |
| Obiettivo non raggiunto | «Obiettivo 5 km · Mancavano 600 m» | Dato neutro, nessun giudizio |
| Mappa senza tessere | «Sfondo mappa non disponibile offline» | Eventuale percorso su fondo neutro |
| Percorso GPS assente | «Percorso non disponibile per questa sessione» | Statistiche consultabili comunque |
| Dati movimento incerti | «Alcuni periodi non sono classificabili con certezza» | Accesso al dettaglio, senza attribuzioni arbitrarie |
| Sessione interrotta | «Allenamento interrotto» | Non comunicare «Completato» se lo stato persistito non lo consente |

### 9.7 Relazione fra riepilogo, dettagli e storico

- Il **riepilogo immediato** e il **riepilogo raggiunto dallo Storico** devono presentare gli stessi valori salvati e la stessa organizzazione: evitare due definizioni discordanti della durata o del ritmo.
- Lo Storico può aprire direttamente lo stesso riepilogo; da lì «Esplora dettagli» consente filtri Corsa/Camminata, dati GPS, sprint, tempi ed esportazione, senza duplicare l'intera UX.
- «Apri mappa» apre una mappa storica esplorabile, da cui si può accedere alla **pagina dedicata ai dettagli dei tratti con mini-mappa** già approvata nel §8.
- Nessuna indicazione «Registrazione attiva» nel riepilogo di una sessione conclusa. L'assenza di rete non pregiudica la lettura delle metriche già salvate.
- Da riepilogo post-salvataggio, il pulsante **primario** «Torna alla Home» e il comando di sistema «Indietro» conducono alla Home *inattiva*; il riepilogo non deve tornare alla schermata di allenamento non più attivo.
- Da riepilogo aperto dallo Storico, il comando di ritorno è contestuale e porta allo **Storico**; non imporre «Torna alla Home» come CTA primaria in questo percorso.
- Dopo «Esplora dettagli», Indietro riporta al riepilogo di provenienza, preservando la distinzione fra post-salvataggio e consultazione dello Storico.

### 9.8 Criteri di accettazione UX

1. «Termina e salva» è protetto da conferma; «Continua allenamento» non interrompe la registrazione.
2. Dopo salvataggio **verificato**, il riepilogo si apre automaticamente per la sessione appena conclusa, senza passare da Home o Storico.
3. Esito del salvataggio non verificato ≠ messaggio «Allenamento salvato»; non creare duplicati con tocchi ripetuti.
4. Distanza complessiva, durata totale e ritmo medio sono leggibili subito; differenza da ritmo attuale e da soli tratti classificati comprensibile.
5. Il riepilogo mostra corsa/camminata e rende accessibili periodi fermi, non classificati e incerti senza attribuzioni false.
6. Obiettivo compare soltanto se configurato; progresso e risultato finale rispecchiano i dati della sessione.
7. Anteprima percorso e accesso alla mappa funzionano quando ci sono punti GPS validi; in assenza di geometria, il riepilogo resta utilizzabile.
8. Nel riepilogo appena salvato «Torna alla Home» è chiaramente **primario** e «Esplora dettagli» **secondario**, entrambi raggiungibili senza un lungo scroll; esportazione ed eliminazione rimangono azioni secondarie.
9. Riaprendo la sessione dallo Storico si consultano gli stessi valori; Indietro torna alla provenienza corretta.
10. Accessibilità: TalkBack annuncia le unità, navigazione coerente, lettura con caratteri ingranditi, nessuna dipendenza dal solo colore.

### 9.9 Questioni da discutere e testare

- **Anteprima percorso:** quanto spazio assegnarle prima di farla competere con le statistiche? Proposta iniziale: riquadro basso e subordinato a distanza/durata/ritmo.
- **Ripartizione dell'attività:** preferire tempi testuali o visualizzazione proporzionale? Verificare la correttezza della base temporale e l'esperienza con dati incerti.
- **Feedback di completamento:** conferma sobria o microcelebrazione discreta? Non aggiungere premi, gamification o valutazioni non basate sui dati.


### 9.10 Decisione confermata: priorità delle azioni dopo il riepilogo (A)

**Approvato:** nel riepilogo aperto **automaticamente dopo il salvataggio**, il pulsante principale è **«Torna alla Home»**; **«Esplora dettagli»** è un'azione secondaria. La scelta riguarda l'enfasi visiva e la navigazione, **non** la disponibilità delle statistiche complete.

**Comportamento di interazione:**

1. Il riepilogo si presenta solo quando l'app ha verificato la persistenza della sessione, con i risultati principali subito leggibili.
2. La CTA primaria «Torna alla Home» deve essere riconoscibile senza dover raggiungere il fondo di una pagina lunga (esempio: fascia inferiore persistente, da validare con dimensioni schermo, gesti Android e font ingranditi).
3. «Esplora dettagli» mantiene contrasto, etichetta e area di tocco adeguati; può stare nello stesso gruppo di azioni ma con peso visivo minore.
4. «Apri mappa» rimane associato all'anteprima geografica, senza competere con le due CTA globali.
5. Toccando «Torna alla Home», si lascia il riepilogo senza perdere il salvataggio; la Home non contiene più una sessione attiva e propone un nuovo avvio.
6. Toccando «Esplora dettagli», si entra nel dettaglio completo della sessione salvata. Il comando Indietro riporta al riepilogo, non all'allenamento terminato.
7. Se lo stesso riepilogo è aperto **dallo Storico**, la gerarchia dei dati rimane uguale ma il ritorno segue la provenienza: «Torna allo Storico» / Indietro, senza forzare un passaggio alla Home.
8. In caso di salvataggio non confermato, non mostrare né la CTA «Allenamento salvato» né un riepilogo apparentemente definitivo: usare prima lo stato di verifica/recupero previsto al §9.6.

**Test da svolgere:** verificare su dispositivi piccoli, con font grandi e TalkBack che «Torna alla Home» è identificabile come azione principale e che il dettaglio resta facilmente raggiungibile; verificare il comportamento dei tasti Indietro e delle navigation stack in entrambi i punti d'ingresso.

## 10. Flussi UX target

### 10.1 Prima di allenarsi

**Home inattiva** → (facoltativo: «Modifica obiettivo» / «Opzioni allenamento») → azione primaria «Inizia allenamento» → controlli posizione/permessi e verifica GPS → **allenamento attivo**. La schermata preparatoria non è obbligatoria nella variante Home qui proposta.

- Se la posizione è disattivata, spiegare il blocco e offrire «Apri impostazioni».
- Se manca il permesso posizione precisa, spiegare il motivo al momento della richiesta.
- Durante i circa 12 secondi di controllo GPS, indicare che l'app sta verificando il segnale senza suggerire che la registrazione sia già attiva.
- In caso di segnale debole, mostrare «Riprova», «Inizia comunque» (con avviso sui dati) e «Annulla».
- Lasciare configurazioni avanzate modificabili, ma non obbligatorie.

### 10.2 Durante l'allenamento

Per la specifica completa della vista, vedere **§7 — Allenamento attivo**. Schermata con un blocco persistente e leggibile contenente **distanza**, **durata**, **ritmo attuale (min/km)**, **stato dell'attività** e **stato GPS**. Se un valore non è affidabile, mostrare un segnaposto e la causa, non uno zero potenzialmente ingannevole.

- La «Mappa» apre la vista dedicata quasi a schermo intero descritta nel **§8**; filtri Corsa/Cammina possono vivere nei «Dettagli». Da **Statistiche e Mappa** si apre la **stessa pagina secondaria «Opzioni allenamento»**: tre righe «Obiettivo di distanza», «Checkpoint vocali» e «Configurazione Sprint», con apertura dei rispettivi editor (§7.22). La navigazione non ferma la sessione; al ritorno dalla Mappa si conserva la camera.
- Obiettivo e checkpoint, quando configurati, appaiono in una fascia compatta e contestuale.
- «Avvia sprint» è un **grande comando diretto e persistente in entrambe le viste operative, Allenamento e Mappa live**, collocato nel **medesimo dock inferiore**, senza configurazione o conferma al momento del tap. Il countdown vocale precede l’effettiva misurazione; dopo la prova, stessa configurazione pronta per lo sprint successivo. «Opzioni → Modifica sprint» permette l’editing anche durante l’allenamento ma solo quando la prova è inattiva, senza alterare il dock fisso. Il pulsante non deve restare attivabile durante countdown o prova. Vedere §7.10.
- Azione «Termina» riconoscibile e raggiungibile senza percorrere una lunga schermata; sempre protetta da conferma. Non simulare una funzione «Pausa» non disponibile.
- «Torna alla Home» mantiene il tracciamento; al rientro l'utente vede chiaramente che una sessione è ancora attiva.
- Se il servizio appare fermo, mostrare un messaggio esplicito e l'azione già prevista «Riprendi tracciamento», senza attribuire dati a intervalli non osservati.

### 10.3 Dopo l'allenamento

**Conferma fine sessione** → stato «Salvataggio in corso» → conferma di persistenza locale → **riepilogo finale immediato** (§9) → **«Torna alla Home» (primario)** oppure **«Esplora dettagli» (secondario)**; «Apri mappa» rimane contestuale all’anteprima percorso. In caso di salvataggio non verificato, mostrare uno stato di recupero, mai un successo fittizio.

Il riepilogo è una **nuova organizzazione UX di dati già presenti**, non una richiesta di nuove metriche. La nuova schermata e la navigazione post-salvataggio sono lavoro implementativo successivo, **fuori dalla PR documentale**.

### 10.4 Storico e dettaglio — decisione UX confermata

**Decisione condivisa:** mantenere la struttura **cronologica compatta nei contenuti** dell'opzione A, ma usare **schede più spaziose con una mini-mappa statica** per ciascun allenamento. L'anteprima non è una seconda mappa esplorabile: serve unicamente a riconoscere **dove** si è corso o camminato. Si tratta di un ibrido deliberato delle opzioni precedenti, non di un feed di schede tecniche dense di dettagli.

#### 10.4.1 Organizzazione e gerarchia della lista

1. Ordinamento: **dalla sessione più recente alla più vecchia**; la data completa e l'ora locale sono visibili nella parte superiore della scheda. L'ordine deve restare prevedibile e non cambiare per distanza, durata o disponibilità del percorso.
2. Prima riga informativa: **distanza complessiva**, **durata totale** e **ritmo medio** della sessione, con etichette e unità chiare. Non mostrare il ritmo attuale nello Storico e non introdurre il totale chilometri di tutte le sessioni come se fosse già presente.
3. Zona geografica: mini-mappa **orizzontale, non interattiva**, che occupa una porzione significativa ma subordinata alle tre metriche. Altezza iniziale da provare: **120–150 dp** su smartphone; il percorso completo deve rimanere leggibile senza ritagliarlo. Non si impone una dimensione definitiva senza test.
4. Stato: «Completato», «In corso» oppure «Interrotto / da recuperare», secondo i dati disponibili. Non definire *completata* una sessione attiva o interrotta. Le sessioni attive non possono essere eliminate; l'eventuale selezione apre il contesto appropriato alla loro condizione.
5. **Un'azione principale per scheda:** toccare la scheda apre il **riepilogo della sessione** (§9). L'anteprima non dispone di gesti, controlli di zoom, pan, selezione tratti o azioni proprie. Se il tocco sulla mini-mappa ricade nella zona attiva della scheda, equivale al tocco sulla scheda: **non apre una mappa navigabile**. «Apri mappa» resta nel riepilogo.
6. Azioni come esportare o eliminare rimangono secondarie nel dettaglio o in un menu accessibile, e non devono competere con il riconoscimento rapido degli allenamenti.

**Wireframe testuale — una scheda, valori inventati:**

```text
STORICO ALLENAMENTI                         [Impostazioni ricerca: non richieste]

┌──────────────────────────────────────────────┐
│ Dom 15 nov 2026 · 08:42          Completato   │
│                                              │
│   5,24 km         38:12         7:17 min/km  │
│   Distanza        Durata        Ritmo medio  │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │   [strade / contesto geografico]       │  │
│  │       ╭── traccia (1 colore) ──╮       │  │
│  │       ╰───────────────╮  │             │  │
│  │                       ╰──╯             │  │
│  └────────────────────────────────────────┘  │
│  Anteprima percorso · non interattiva        │
│                                              │
│  Tocca la scheda per il riepilogo         ›  │
└──────────────────────────────────────────────┘

[Allenamento precedente, stessa struttura]
```

Il nome di una via o località non viene dedotto automaticamente: **nessuna geocodifica inversa** è tra le funzionalità presenti nel catalogo. La mappa cartografica fornisce l'orientamento quando le tessere sono disponibili.

#### 10.4.2 Specifica della mini-mappa statica

- **Inquadratura:** includere l'intero percorso GPS affidabile salvato della sessione, con margine sufficiente; rappresentare la posizione geografica nel contesto delle strade e dell'area circostante, **non una grafica astratta** come soluzione normale.
- **Tracciato monocolore (decisione approvata — opzione A):** disegnare **tutti i segmenti GPS attendibili con un unico colore ad alto contrasto**, senza distinguere nell'anteprima corsa, camminata o sprint. Il colore esatto è una scelta di UI da validare in seguito; non deve codificare l'attività. Usare le coordinate locali già salvate, mantenere le **discontinuità reali** e **non unire** lacune prolungate o punti scartati. Un solo colore non significa una sola polilinea continua: le interruzioni GPS rimangono visibili come tratti separati. Niente legenda delle attività nell'anteprima; nella **mappa navigabile e nei dettagli dei tratti** restano i colori differenziati già previsti (camminata, corsa, sprint, altro).
- **Nessuna navigazione:** niente zoom, trascinamento, follow GPS, marker di posizione attuale, selezione dei tratti o finestre informazioni. La mini-mappa è un'**anteprima**, non un'esplorazione. Non offrire un controllo di zoom disabilitato.
- **Azioni:** l'intera scheda è un singolo target che apre il riepilogo; il testo accessibile può essere «Allenamento del 15 novembre, 5,24 chilometri, 38 minuti, apri riepilogo». Per TalkBack la mini-mappa ha una descrizione di supporto, ad esempio «Anteprima del percorso GPS, non interattiva», senza creare un secondo elemento cliccabile.
- **Prestazioni:** costruire o caricare le anteprime soltanto per le schede visibili o prossime alla visualizzazione, senza renderizzare contemporaneamente tutte le mappe dell'archivio; la lista deve restare scorrevole con molti allenamenti. Valutare un'anteprima statica/cache **locale**, evitando servizi remoti di generazione thumbnail o caricamento di tracce GPS.
- **Privacy:** il percorso resta nel dispositivo. L'eventuale caricamento delle tessere OpenStreetMap può comportare richieste di rete relative all'area inquadrata, come già accade nelle mappe dell'app; non caricare i punti GPS su servizi esterni per creare le anteprime. Rispettare cache e policy del fornitore di tessere.

**Esempi di comportamento visivo — monocolore (decisione confermata):**

- **Anteprima normale:** sfondo cartografico con strade e area circostante; sopra, linea GPS sempre nello stesso colore leggibile (anche nei cambi di attività). Nessuna legenda di corsa/camminata/sprint e nessuna evidenziazione dei singoli tratti.
- **GPS discontinuo:** ad esempio due segmenti distanziati vengono resi con due porzioni dello **stesso** colore, senza linea di collegamento inventata.
- **Offline senza tessere:** stesso tracciato monocolore su fondo neutro, più messaggio conciso sull'assenza dello sfondo; non presentare una cartina generica di un'altra area.
- **Tap sulla mini-mappa:** equivale al tap sulla scheda e porta al riepilogo; i colori specifici per attività diventano disponibili solo aprendo la mappa dettagliata dal riepilogo.
- **Accessibilità:** testo della scheda e stato del percorso devono comunicare l'esistenza dell'anteprima senza richiedere la capacità di distinguere i colori. Non codificare con il colore unico alcuna metrica o stato di attività.

**Distinzione intenzionale fra viste:** l'anteprima nello **Storico** usa un solo colore perché risponde alla domanda «Dove mi sono allenato?». La **mappa completa** e la **mini-mappa della pagina dedicata a un tratto GPS** conservano la gerarchia di colori e l'evidenziazione del segmento quando pertinenti. Le due mini-mappe hanno scopi diversi e non devono essere uniformate forzatamente.

#### 10.4.3 Stati particolari dell'anteprima

| Stato | Comportamento UX | Messaggio suggerito |
|---|---|---|
| Percorso e cartografia disponibili | Anteprima statica di percorso e strade, centrata sulla traccia | «Anteprima del percorso» |
| Offline, tessere già in cache | Mostrare lo sfondo cartografico disponibile senza richiedere rete | Nessun avviso, salvo dettaglio utile |
| Offline senza tessere | Se ci sono coordinate valide, mostrare la traccia su fondo neutro, distinguendo la perdita dello **sfondo** dalla disponibilità del **percorso** | «Mappa di sfondo non disponibile offline» |
| Nessuna geometria GPS attendibile | Usare un riquadro con icona e testo; non inventare una linea o una località | «Percorso GPS non disponibile» |
| Tracciato parziale o interrotto | Mostrare soltanto segmenti attendibili; evitare collegamenti artificiali | «Percorso parziale» quando utile |
| Sessione ancora attiva | Etichetta «In corso», anteprima eventualmente parziale; nessun suggerimento che i dati siano finali | «Percorso in aggiornamento» |
| Caricamento o errore della sola mini-mappa | Mantenere sempre accessibili data, metriche e apertura del riepilogo | «Anteprima non disponibile» |

#### 10.4.4 Navigazione e continuità

**Home → Storico → Scheda allenamento → Riepilogo salvato → (facoltativo) Mappa navigabile → Dettaglio tratto.** Da riepilogo aperto dallo Storico, «Torna allo Storico» e Indietro rispettano la provenienza. Tornando alla lista, conservare **posizione di scorrimento** e ordinamento cronologico. Non obbligare a ricaricare tutte le anteprime per ritrovare la scheda consultata.

L'apertura di una sessione attiva deve conservare il tracking e portare alla vista coerente con il suo stato; lo Storico non deve consentire di eliminarla. L'eliminazione di una sessione salvata rimane protetta da conferma dell'irreversibilità, con esportazione disponibile dal dettaglio.

#### 10.4.5 Criteri di accettazione UX dello Storico

- Le schede sono in ordine cronologico inverso e mostrano **data/ora, distanza complessiva, durata, ritmo medio e anteprima**, senza aprire il dettaglio.
- Le schede sono più spaziose di una riga di elenco, ma non diventano pagine complete o contenitori di statistiche avanzate.
- La mini-mappa mostra **il luogo e la forma** del percorso quando cartografia e coordinate sono disponibili; tutti i segmenti attendibili usano **un unico colore**, senza legenda delle attività e mantenendo eventuali interruzioni reali della traccia. Non è possibile trascinarla, ingrandirla o selezionare segmenti.
- Toccando la scheda, **anche in corrispondenza della mini-mappa**, si apre il riepilogo e non la mappa interattiva.
- Lo Storico resta consultabile offline e senza geometria GPS: le statistiche salvate continuano a essere visibili; non vengono disegnati percorsi fittizi.
- Con decine o centinaia di allenamenti la lista mantiene uno scorrimento utilizzabile, grazie al caricamento progressivo delle anteprime.
- TalkBack legge il riepilogo di ogni scheda una sola volta e l'immagine non aggiunge pulsanti nascosti; la resa deve funzionare anche con font grandi.
- Le sessioni attive/interrotte sono riconoscibili e non presentate come completate; le azioni distruttive non sono l'elemento principale della scheda.

#### 10.4.6 Da verificare con prototipo

- Altezza ideale dell'anteprima (ipotesi 120–150 dp) e spazio per data e metriche su schermi piccoli.
- Verificare su schermi reali la leggibilità del **colore unico** su tessere differenti (strade, parchi, modalità scura se prevista) e su fondo neutro offline. **Non** reintrodurre una legenda corsa/camminata/sprint nello Storico: la sua assenza è una decisione approvata.
- Sostenibilità dell'uso di tessere cartografiche nelle schede, in termini di prestazioni, rete, policy delle tessere e privacy: valutare rendering statico locale senza nuove API o servizi cloud.

### 10.5 Meteo per correre — specifica UX (consultazione, selettore città e preferenze separate approvati)

#### 10.5.1 Decisione condivisa e obiettivo

**Confermato:** il Meteo è una destinazione utile di per sé e mostra **una città alla volta con passaggio rapido tra quelle salvate**, **senza obbligare a configurare notifiche, fasce orarie o soglie meteorologiche**. Le preferenze sono divise in **«Le mie condizioni»** e **«Gestisci avvisi»**, senza mescolare i due compiti. L'esperienza predefinita è leggere le previsioni; solo in un secondo momento l'utente può personalizzare i criteri e richiedere avvisi.

**Attenzione al perimetro:** l'app esistente cerca città, legge le previsioni orarie di temperatura/vento e consente il refresh manuale. Il caricamento **automatico all'ingresso nella schermata** e la separazione delle pagine sono proposte di UX/comportamento futuro; non sono dichiarate implementate. Non si introducono algoritmi meteo nuovi, GPS in background, permessi di localizzazione necessari per il Meteo, né nuove fonti dati.

#### 10.5.2 Prima apertura: nessuna città salvata

1. Intestazione «Meteo per correre» e testo «Consulta temperatura e vento prima di uscire».
2. Invito principale «Per quale città vuoi vedere il meteo?» con campo di ricerca e pulsante «Cerca città» (ricerca remota esistente).
3. Risultati con città, regione e Paese, per disambiguare nomi simili; selezionando una città si passa alla consultazione e la preferenza viene conservata sul dispositivo.
4. Nessun interruttore notifiche, orario di controllo o soglia obbligatoria. Nessuna richiesta di permesso GPS/posizione per cercare una città.
5. Ricerca non disponibile: spiegazione «Serve una connessione per cercare una città» con «Riprova»; nessuna città fittizia.

La prima scelta della città è necessaria perché le previsioni dipendono da una località. Non confonderla con la configurazione degli avvisi.

#### 10.5.3 Pagina Meteo: una città selezionata

Ordine informativo proposto, dall'alto verso il basso:

1. **Intestazione e città:** «Meteo per correre» e **nome della città selezionata come controllo «Cambia città»**, con indicazione chiara della località in vista. La schermata contiene le previsioni **di una sola città**, non una griglia di località. Il catalogo supporta fino a 20 città salvate.
2. **Riepilogo della fascia oraria corrente:** temperatura in °C, vento in km/h, orario della previsione e indicazione «Stima oraria»; non presentarlo come una misurazione dal telefono o una previsione minuto per minuto.
3. **Previsioni delle prossime 24 ore:** righe o piccole schede cronologiche con ora, temperatura e vento. Nessuna soglia necessaria.
4. **Aggiorna:** azione secondaria sempre disponibile, con indicazione dell'ultimo aggiornamento riuscito quando noto. La proposta prevede inoltre la richiesta automatica dei dati quando si entra con una città già scelta, senza richiedere il tap «Aggiorna».
5. **Le mie condizioni (facoltativo):** ingresso secondario alla pagina omonima, con riepilogo solo delle soglie salvate oppure «Nessun criterio impostato».
6. **Gestisci avvisi (facoltativo):** ingresso separato alla pagina omonima, con stato reale «Disattivati»/«Attivi»/«Da completare» secondo configurazione e autorizzazioni, senza occupare il centro della consultazione.
7. Crediti del fornitore Open-Meteo e spiegazione breve che le previsioni richiedono rete e possono variare.

**Non assumere nuove variabili meteorologiche:** il catalogo garantisce temperatura e vento, non pioggia, qualità dell'aria, umidità o UV.

#### 10.5.4 «Condizioni favorevoli»: semantica corretta

L'implementazione attuale usa `WeatherRules` con criteri facoltativi in AND: se nessuno è impostato, ogni ora con temperatura e vento validi risulta tecnicamente conforme. Per evitare un giudizio fuorviante nel nuovo design:

- **Nessun criterio configurato:** mostrare valori meteorologici **neutri**, senza «Favorevole», «Perfetto per correre» o badge verde; proporre «Personalizza condizioni» solo come possibilità.
- **Almeno un criterio configurato:** usare «Rispetta le tue condizioni» / «Non rispetta le tue condizioni» per ogni ora e, se utile, un totale delle ore che rispettano le preferenze nelle prossime 24 ore. Le soglie lasciate vuote non applicano un vincolo.
- **Dati insufficienti:** «Impossibile valutare questa fascia oraria», non classificarla favorevole.
- **Criteri personali:** temperatura minima/massima e vento massimo come già supportato; criteri condivisi tra le città come oggi. Mostrare in linguaggio naturale le soglie attive («Tra 8 e 24 °C · vento fino a 20 km/h»; numeri puramente esemplificativi).

Le etichette descrivono la corrispondenza ai criteri individuali, **non una garanzia oggettiva di sicurezza o idoneità a correre**.

#### 10.5.5 Gestione città e notifiche come funzionalità progressive

- **Decisione approvata:** «Cambia città» apre un **selettore rapido delle città già salvate** (elenco accessibile e scrollabile, nome completo per distinguere omonimie, indicatore della selezione corrente). Il tap su una città chiude il selettore e aggiorna soltanto le **previsioni in primo piano**; non avvia l’allenamento, non modifica criteri od orari e non attiva le notifiche. «Gestisci città» rimane l’azione distinta per aggiungere/rimuovere città (massimo 20, ricerca remota e deduplicazione già supportate).
- **Con una sola città salvata** il nome resta identificabile e può aprire il selettore con la sola voce e il comando «Aggiungi città»; con nessuna città appare il flusso di ricerca iniziale. Non mostrare una lista vuota senza istruzioni.
- **Stato selezionato:** conservare sul dispositivo l’ultima città visualizzata (nuova preferenza di presentazione). All’apertura usare quella se è ancora salvata; altrimenti scegliere una città salvata in modo deterministico; se vengono rimosse tutte, tornare allo stato iniziale. **Non usare il GPS per selezionarla automaticamente.**
- **Cambio città:** rendere visibile il caricamento della nuova località, aggiornarne insieme intestazione, orari e valori; non lasciare i dati della città precedente sotto un nuovo nome. In caso di errore, mantenere il nome scelto e mostrare «Previsioni non disponibili. Riprova».
- **Città salvate e avvisi:** l’elenco delle città salvate attualmente alimenta anche i controlli meteo programmati; **cambiare solo la città visualizzata non cambia quali città ricevono controlli**, mentre aggiungere/rimuovere città può incidere sugli avvisi attivi. Questo effetto va spiegato in «Gestisci città» quando gli avvisi sono attivi. I criteri e gli orari rimangono condivisi tra le città come nell’implementazione attuale.
- **Decisione approvata — due schermate distinte:** «Le mie condizioni» espone temperatura minima, temperatura massima e vento massimo e un’azione «Salva condizioni», senza controlli delle notifiche. Se i campi sono vuoti, la consultazione resta disponibile senza giudizi di favorevolezza.
- «Gestisci avvisi» espone esclusivamente attivazione/disattivazione, città coinvolte (rimando a «Gestisci città») e orari quotidiani (aggiungi, modifica, rimuovi). I permessi di notifica sono richiesti **nel contesto dell’attivazione degli avvisi**, se necessari; il Meteo resta consultabile senza permessi di notifica. Non cambiare implicitamente le dipendenze del tracking.
- Confermare che i controlli programmati possono essere ritardati da Android e che l'app limita gli avvisi ripetuti a uno ogni 6 ore per città. Se mancano città/orari, non dichiarare gli avvisi operativi.

##### 10.5.5.1 Flusso di cambio città — decisione confermata

```text
Meteo: Bologna, Italia [Cambia città]
    └─ Apre «Città salvate» (selettore rapido)
         ├─ ✓ Bologna, Italia (città corrente)
         ├─ Salerno, Italia  → seleziona → aggiorna solo la vista Meteo di Salerno
         ├─ Milano, Italia   → seleziona → aggiorna solo la vista Meteo di Milano
         └─ Gestisci città   → ricerca, aggiunta, eliminazione (fino a 20)
```

- Se l'utente annulla/chiude il selettore, non cambia alcun valore.
- Il nome lungo della località deve rimanere distinguibile, senza affidarsi a sigle ambigue.
- Il passaggio non richiede GPS, non cambia il set di città salvate né l'abilitazione degli avvisi. Le previsioni della nuova città devono essere ricaricate: questo automatismo è una proposta UX da implementare.
- Con la sola città salvata il cambio è facoltativo; il comando «Aggiungi città» resta accessibile. Con 20 città, il selettore è scrollabile senza rimpicciolire i tocchi.

#### 10.5.6 Wireframe testuale, valori inventati

```text
METEO PER CORRERE                 [Impostazioni]
Bologna, Italia  [⌄ Cambia città]  ← elenco delle città salvate

Adesso · stima per le 12:00
19 °C                       Vento 12 km/h

PROSSIME ORE                         [Aggiorna]
13:00                 20 °C            13 km/h
14:00                 21 °C            15 km/h
15:00                 20 °C            17 km/h
[Mostra tutte le 24 ore]

[Le mie condizioni]             Nessun criterio impostato
[Gestisci avvisi]               Disattivati

Previsioni Open-Meteo · richiedono connessione
```

Il selettore rapido, aperto dal nome della città, mostra ad esempio «✓ Bologna, Italia», «Salerno, Italia», «Milano, Italia» e l’azione distinta «Gestisci città»; **non** mostra le previsioni di tutte le località insieme. Alla **prima visita senza città**, la stessa schermata mostra invece solo la ricerca della località e una spiegazione breve. Se vengono definite condizioni personali, alle ore può affiancarsi un testo «Rispetta le tue condizioni» / «Non le rispetta», senza basarsi sul solo colore.

#### 10.5.7 Stati, errore, rete e privacy

| Stato | Comportamento / microcopy |
|---|---|
| Nessuna città | «Scegli una città per consultare le previsioni»; campo di ricerca protagonista |
| Più città salvate | Selettore rapido accessibile da «Cambia città» con città corrente indicata e azione «Gestisci città» |
| Cambio città | La nuova città viene mostrata con stato di caricamento: i numeri precedenti non restano associati alla nuova località |
| Città selezionata rimossa | Passare a un’altra città salvata in modo deterministico o mostrare la ricerca se l’elenco è vuoto |
| Caricamento previsioni | Indicatore di caricamento, città visibile, nessuna falsa stima corrente |
| Previsioni ricevute | Ora della previsione, temperatura, vento e righe orarie |
| Nessuna rete / errore servizio | «Previsioni non disponibili. Controlla la connessione e riprova»; lascia visibile la città selezionata |
| Dati orari mancanti | Valore «—» e indicazione «Dato non disponibile», senza valutazioni favorevoli fittizie |
| Nessun criterio | Previsioni descrittive, niente «favorevole/sfavorevole» |
| Criteri impostati | Etichetta di corrispondenza alle preferenze, non giudizio assoluto |
| Avvisi spenti | Consultazione integralmente utilizzabile; non chiedere permessi di notifica |
| Orario/permesso notifiche mancante | Mostrare che gli avvisi non sono pronti; spiegare cosa manca prima dell'attivazione |

Il Meteo **non richiede GPS in background**: usa le città selezionate. La ricerca località e la richiesta delle previsioni usano servizi esterni online; non promettere consultazione offline, cache persistente o dati aggiornati senza rete, poiché non sono attestati nell'implementazione corrente.

#### 10.5.8 Criteri di accettazione UX e cambi rispetto all'implementato

- Con nessuna città salvata, l'utente comprende che serve **solo** scegliere una località; non deve impostare notifiche o soglie.
- Con una città salvata e rete disponibile, l'apertura della pagina mostra automaticamente i dati o uno stato di caricamento appropriato **(richiede modifica dell'attuale comportamento di aggiornamento manuale)**.
- Con soglie tutte vuote, nessuna ora viene etichettata come oggettivamente «favorevole»; se alcune soglie sono impostate, le etichette riportano esclusivamente la conformità alle preferenze.
- Il riepilogo contiene solo dati realmente supportati (temperatura, vento e ore); nessun indicatore su pioggia o altri fattori non presenti.
- È sempre possibile consultare le previsioni con avvisi disattivati e senza concedere il permesso notifiche.
- Se la rete non funziona, la pagina mantiene stato della città e alternative comprensibili; nessun falso valore.
- L'utente può passare da una città salvata all’altra **dal nome della località nella schermata principale**, senza tornare a impostazioni o ricerca, e vede solo i dati della città selezionata.
- La città attualmente selezionata è identificabile per nome e non soltanto per colore, si ricorda tra aperture dell’app e ha un comportamento definito in caso di eliminazione.
- Cambiare città non attiva notifiche né modifica condizioni e orari; se sono attivi gli avvisi per le città salvate, «Gestisci città» chiarisce l’effetto di aggiungerne o rimuoverne una.
- L'utente può accedere a gestione città, condizioni, orari e notifiche senza che questi controlli invadano la vista principale.
- Font ingranditi, TalkBack e schermi piccoli mantengono chiaramente etichettate città, unità, orari e stati; colore non è l'unico segnale.

**Da validare in prototipo:** forma concreta del selettore (menu/pannello), ordine delle città, quante ore mostrare senza scorrimento e comportamento su schermi piccoli. La decisione «una città per volta, con selettore rapido» **non è più aperta**.

#### 10.5.9 Decisione confermata: preferenze Meteo in due schermate (A)

**Decisione condivisa:** non usare un unico modulo lungo per soglie, notifiche e orari. Dalla consultazione Meteo si accede a due destinazioni indipendenti, con compiti riconoscibili:

- **«Le mie condizioni»**: definire come interpretare temperatura e vento delle previsioni; sono preferenze personali, comuni a tutte le città salvate.
- **«Gestisci avvisi»**: scegliere se l'app invia notifiche locali e in quali orari giornalieri controllare le previsioni; i controlli coinvolgono **tutte le città salvate**, non soltanto quella selezionata nella pagina Meteo.

**Percorso di navigazione:** `Meteo per correre → Le mie condizioni → Indietro a Meteo` e `Meteo per correre → Gestisci avvisi → Indietro a Meteo`. Da «Gestisci avvisi», «Gestisci città» è un collegamento contestuale alla gestione delle località, non una terza configurazione nascosta. La pagina Meteo espone lo stato sintetico di entrambi, senza usare toggle di attivazione direttamente sulla consultazione.

##### 10.5.9.1 Pagina «Le mie condizioni» — gerarchia e comportamenti

**Obiettivo UX:** specificare volontariamente cosa significa «rispetta le tue condizioni», senza chiedere all'utente di attivare anche le notifiche.

1. **Header:** «Le mie condizioni»; descrizione «Scegli quali temperature e quanto vento consideri adatti per allenarti».
2. **Tre campi numerici facoltativi e identificati dalle unità:** «Temperatura minima (°C)», «Temperatura massima (°C)», «Vento massimo (km/h)». Ogni campo ha un'etichetta visibile anche mentre si scrive, non solo un placeholder. Campo vuoto = nessun limite. Tastiera numerica adatta ai valori decimali e ai segni della temperatura; accettare virgola e punto come separatori decimali.
3. **Riepilogo comprensibile prima del salvataggio:** ad esempio «Temperatura tra 8 e 24 °C · vento fino a 20 km/h». Mostrare solo i vincoli compilati; se tutti vuoti «Nessun criterio impostato: vedrai le previsioni senza valutazioni».
4. **CTA unica:** «Salva condizioni». Dopo salvataggio riuscito, tornare a Meteo o rimanere sulla pagina mostrando una conferma non ambigua, con l'ultimo riepilogo aggiornato. Non alterare stato, orari o permesso degli avvisi quando si modificano le soglie, **salvo il caso esplicitamente confermato** in cui si elimina l’ultima condizione e gli avvisi devono essere disattivati contestualmente (vedi §10.5.9.5).
5. **Errore nel campo**, prima del salvataggio: temperatura minima e massima tra -90 °C e 65 °C, vento massimo tra 0 e 300 km/h; se entrambi compilati, minimo ≤ massimo. Tali limiti rispecchiano le regole esistenti, non valori meteorologici consigliati. Errore testuale sotto il campo, focus all'errore e nessuna modifica salvata finché la validazione fallisce.
6. **Modifiche non salvate:** tornando indietro dopo una modifica, proporre «Scarta modifiche» / «Continua a modificare», oppure adottare un salvataggio automatico coerente in tutta l'app (scelta da validare). Questa bozza assume esplicitamente **salvataggio su tap**.

**Wireframe testuale — «Le mie condizioni», numeri di esempio:**

```text
← Meteo per correre                  Le mie condizioni
Quali condizioni preferisci per allenarti?

Temperatura minima (°C)              [ 8  ]
Temperatura massima (°C)              [24  ]
Vento massimo (km/h)                  [20  ]
(Per non impostare un limite, lascia il campo vuoto)

RIEPILOGO
Tra 8 e 24 °C · vento fino a 20 km/h
Si applica a tutte le città salvate.

[         Salva condizioni           ]
```

**Semantica delle valutazioni:** in Meteo, se almeno una soglia è impostata e la previsione dispone dei dati richiesti, mostrare «Rispetta le tue condizioni» o «Non rispetta le tue condizioni» per ogni ora. Non promettere condizioni oggettivamente «sicure»; non sostituire «nessuna soglia» con predefiniti non autorizzati. Le soglie sono condivise tra tutte le città nell'app attuale.

##### 10.5.9.2 Pagina «Gestisci avvisi» — gerarchia e comportamenti

**Obiettivo UX:** poter programmare notifiche senza modificare soglie e senza scambiare gli orari per previsioni meteorologiche.

1. **Header:** «Gestisci avvisi» e frase «Ricevi un avviso quando una previsione rispetta le tue condizioni». Se non esiste almeno una soglia salvata, mostrare subito lo stato «Configura prima le tue condizioni», senza definire la previsione «favorevole» e senza promettere notifiche.
2. **Stato avvisi:** interruttore «Ricevi avvisi meteo», inizialmente non attivo per una nuova installazione, con stato chiaro e possibilità di disattivare senza cancellare città, soglie o orari. **In assenza di soglie salvate l’attivazione non è disponibile:** il motivo è spiegato accanto al controllo, con accesso a «Le mie condizioni». Per questa bozza la modifica dello switch è una **bozza locale** finché non si preme «Salva avvisi»; non dichiarare «Attivi» prima che la configurazione sia effettivamente applicata.
3. **Città monitorate:** «Le notifiche riguardano tutte le [N] città salvate», con link «Gestisci città». La città selezionata nel Meteo **non limita** i controlli. Se N = 0, spiegare che serve almeno una città per attivare gli avvisi.
4. **Orari di controllo giornalieri:** elenco ordinato `HH:mm` con azioni «Modifica» e «Rimuovi» su ciascuna riga, e «Aggiungi orario» che apre il selettore orario Android. Evitare duplicati; rispettare l'ora locale del dispositivo. Quando l'elenco è vuoto, «Nessun controllo programmato» con CTA «Aggiungi orario».
5. **CTA «Salva avvisi»** dopo i controlli: per attivare, richiedere **almeno una condizione personalizzata effettivamente salvata**, almeno una città salvata e almeno un orario. Se manca una soglia, non applicare l’attivazione: mostrare «Per ricevere avvisi, imposta almeno una condizione meteo» e il comando «Configura le mie condizioni». Non inserire automaticamente soglie o accettare una bozza non salvata. Con avvisi disattivati è possibile conservare orari predisposti senza programmare controlli automatici. Mostrare esito reale del salvataggio.
6. **Permessi Android:** chiedere l'autorizzazione alle notifiche solo quando pertinente all'attivazione degli avvisi, nei sistemi che la richiedono; se negata o disabilitata nelle impostazioni, mostrare «Avvisi non disponibili: autorizza le notifiche» e un percorso verso le impostazioni Android. **Non confondere la preferenza salvata con notifiche effettivamente erogabili.**
7. **Informazioni concise:** «I controlli potrebbero arrivare in ritardo per le ottimizzazioni Android; richiedono Internet. Massimo un avviso ogni 6 ore per città». Gli orari sono momenti di verifica, non orari garantiti di consegna.

**Wireframe testuale — «Gestisci avvisi», numeri di esempio:**

```text
← Meteo per correre                    Gestisci avvisi
Ricevi avvisi quando le previsioni rispettano le tue condizioni.

[ ] Ricevi avvisi meteo             Disattivati

CITTÀ MONITORATE
Tutte le 3 città salvate           [Gestisci città]

ORARI DI CONTROLLO · OGNI GIORNO
07:00                             [Modifica] [Rimuovi]
18:30                             [Modifica] [Rimuovi]
[+ Aggiungi orario]

Condizioni richieste: almeno una soglia salvata.
Gli orari sono approssimativi. Serve Internet.
Massimo un avviso ogni 6 ore per città.

[             Salva avvisi           ]
```

##### 10.5.9.3 Matrice degli stati e messaggi

| Stato | Messaggio o risposta UX |
|---|---|
| Nessuna soglia salvata, consultazione Meteo | «Nessun criterio impostato»: mostrare le previsioni, senza classificare le ore come favorevoli |
| Nessuna soglia salvata, tentativo di attivare avvisi | Impedire l’attivazione; «Per ricevere avvisi, imposta almeno una condizione meteo» e «Configura le mie condizioni» |
| Solo temperatura minima | Applicare soltanto quel limite; vento e temperatura massima restano senza restrizione |
| Minimo temperatura maggiore del massimo | Errore sotto il campo: «La temperatura minima deve essere inferiore o uguale alla massima»; non salvare |
| Testo non numerico o fuori intervallo | Evidenziare il campo con intervallo valido e unità, senza perdere gli altri valori digitati |
| Nessuna città, attivazione avvisi | «Aggiungi almeno una città per ricevere avvisi» con accesso alla gestione città |
| Nessun orario, attivazione avvisi | «Aggiungi almeno un orario di controllo»; non applicare un'attivazione incompleta |
| Avvisi disattivati | Le previsioni restano consultabili e soglie/orari restano salvati |
| Ultima condizione rimossa mentre gli avvisi sono attivi | Prima di salvare, richiedere conferma esplicita della disattivazione degli avvisi; con «Annulla» non cambiare dati né programmazione |
| Conferma dell’eliminazione dell’ultima condizione | Salvare le condizioni vuote **e** disattivare gli avvisi come un’unica operazione coerente; conservare città e orari |
| Permesso notifiche negato | «Avvisi non disponibili: autorizza le notifiche»; conservare la configurazione senza dichiarare avvisi operativi |
| Offline | Conservare modifiche locali; spiegare che i controlli e le previsioni dipendono dalla rete |
| Controllo programmato ritardato | Non garantire consegna all'ora selezionata; indicazione preventiva nella pagina |
| Ultima città vista cambiata | Nessun effetto su soglie, elenco città monitorate o programmazione avvisi |

##### 10.5.9.4 Decisione confermata: una condizione obbligatoria per attivare gli avvisi (A)

**Decisione condivisa:** è possibile consultare le previsioni senza alcuna soglia; **per attivare gli avvisi meteo è obbligatorio avere salvato almeno un criterio** fra temperatura minima, temperatura massima o vento massimo. Le altre soglie rimangono facoltative. L'app non sceglie valori predefiniti al posto dell'utente.

**Flusso di attivazione:**

1. L'utente apre «Gestisci avvisi».
2. Se non è presente nessuna soglia salvata, lo switch di attivazione è inattivo e presenta una spiegazione testuale e un collegamento a «Le mie condizioni». Non è sufficiente che l'utente abbia modificato un campo senza salvarlo.
3. Tornando da «Le mie condizioni» con almeno una soglia valida salvata, può attivare gli avvisi, purché siano presenti anche una città e un orario di controllo.
4. L'app richiede l'eventuale permesso Android in modo contestuale e segnala lo stato reale, senza dichiarare attivi gli avvisi in assenza di autorizzazioni o programmazione effettiva.
5. Se non ci sono soglie, la consultazione delle previsioni resta completamente disponibile e senza giudizi di «favorevolezza».

**Microcopy consigliata** (senza soglie):

```text
Avvisi non ancora attivi
Per ricevere avvisi, indica almeno una condizione meteo
che preferisci per allenarti.
[Configura le mie condizioni]
Le previsioni rimangono consultabili anche senza avvisi.
```

**Differenza rispetto all'implementazione attuale:** `WeatherRules.matches()` può considerare soddisfatte le condizioni se tutte le soglie sono vuote, a fronte di dati validi; la programmazione delle notifiche dipende oggi da città, orari e interruttore, non dall'esistenza di un limite. La proposta richiede un controllo aggiuntivo **sia al momento dell'attivazione sia nell'esecuzione dei controlli programmati**, per non inviare notifiche contro la nuova regola. Non è una modifica cosmetica e non è ancora implementata. Il documento UX non cambia algoritmi o codice.

**Configurazioni esistenti:** se una versione precedente ha già gli avvisi abilitati senza soglie, la nuova UX deve rappresentare la configurazione come «Da completare» e non dichiarare notifiche attive finché non è stata definita almeno una condizione; preservare città e orari già memorizzati. Comportamento da considerare nel piano di implementazione e nei test.

**Decisione sul caso dell'ultima soglia:** è stata approvata l'alternativa **B — conferma esplicita con disattivazione contestuale degli avvisi** (vedi §10.5.9.5). Non è più una scelta aperta.

##### 10.5.9.5 Decisione confermata: eliminare l’ultima condizione con avvisi attivi (B)

**Innesco:** l’utente apre «Le mie condizioni» con avvisi già attivi, svuota tutti i campi validi e preme «Salva condizioni». Il controllo riguarda lo **stato attualmente salvato**, non un interruttore ancora in bozza in «Gestisci avvisi».

**Flusso UX desiderato:**

1. L’utente modifica i campi e preme «Salva condizioni». Prima di qualsiasi modifica persistente, l’app verifica validità dei campi e se la nuova configurazione ha **zero condizioni** mentre gli avvisi risultano **attivi**.
2. In questo solo caso viene aperta una conferma esplicita, senza chiudere la pagina e senza aggiornare già i dati.
3. **«Annulla»** chiude la conferma: *nessuna preferenza o programmazione è cambiata*. Le modifiche digitabili restano nella pagina come **bozza non salvata**, così l’utente può correggerle o decidere di annullarle.
4. **«Disattiva avvisi e salva»** applica insieme il salvataggio delle condizioni vuote e la disattivazione degli avvisi. Gli orari e le città salvate si conservano; viene interrotta la programmazione dei controlli automatici, senza cambiare la città attualmente consultata.
5. Solo dopo la riuscita dell’operazione mostrare «Condizioni salvate. Avvisi meteo disattivati». La consultazione delle previsioni continua, senza etichette «favorevole/sfavorevole» basate su condizioni assenti. Un successivo tentativo di riattivazione richiederà almeno una soglia salvata.
6. Se il salvataggio o l’applicazione del nuovo stato non è verificabile, **non mostrare successo**: spiegare il problema, mantenere accessibile la configurazione e impedire notifiche senza criteri. La specifica richiede coerenza tra stato salvato e controlli programmati, senza imporre uno specifico meccanismo tecnico.

**Microcopy della conferma:**

```text
Disattivare gli avvisi meteo?

Stai rimuovendo l'ultima condizione personalizzata.
Senza condizioni, gli avvisi non possono rimanere attivi.

Le città e gli orari salvati resteranno disponibili.

[Annulla]            [Disattiva avvisi e salva]
```

**Quando non chiedere conferma:** se rimane almeno una soglia valida, oppure se gli avvisi sono già disattivati, «Salva condizioni» segue il normale percorso senza dialoghi aggiuntivi. La conferma non deve comparire durante la semplice modifica di un campo né per un tentativo che non supera la validazione.

**Nota per la futura implementazione:** oggi condizioni, città, orari e toggle degli avvisi sono gestiti nella stessa `WeatherSettingsActivity`; la separazione proposta deve evitare aggiornamenti parziali e preservare i valori non coinvolti. Questa sezione specifica soltanto il comportamento atteso dall’utente.

##### 10.5.9.6 Criteri di accettazione delle due schermate

- Da Meteo, gli ingressi «Le mie condizioni» e «Gestisci avvisi» sono distinti, rintracciabili e possono essere aperti in qualsiasi ordine.
- Si possono consultare le previsioni senza configurare né una schermata né l'altra.
- I tre limiti sono facoltativi, hanno unità visibili e rispettano validazione e dipendenza min ≤ max; un campo vuoto non applica vincoli.
- Il riepilogo delle condizioni contiene esclusivamente le soglie realmente salvate e indica che valgono per tutte le città.
- Cambiare una soglia non attiva né disattiva da solo le notifiche; **solo il salvataggio confermato dell’ultima soglia rimossa** comporta la disattivazione degli avvisi attivi. Cambiare la città consultata non modifica gli avvisi.
- «Gestisci avvisi» mostra le città interessate, gli orari di controllo e lo stato autorizzativo dei permessi.
- Aggiunta, modifica e rimozione di orari sono eseguibili senza duplicati; gli orari sono ordinati e modificabili anche se gli avvisi sono momentaneamente disattivati.
- Un tentativo di attivazione con zero città o zero orari fornisce una spiegazione e non dichiara falsamente gli avvisi operativi.
- La UI distingue richiesta di avvisi, salvataggio e loro effettiva disponibilità quando il permesso Android è negato.
- Se si rimuove l’ultima soglia con avvisi attivi, «Salva condizioni» chiede conferma **prima** di cambiare le preferenze; «Annulla» non modifica dati e programmazione, mentre «Disattiva avvisi e salva» conserva città e orari e interrompe gli avvisi.
- Se gli avvisi erano già disattivati, l’eliminazione dell’ultima soglia non richiede ulteriori conferme; errori di validazione non aprono la conferma.
- I controlli pianificati sono dichiarati approssimativi; è indicato il limite di un avviso ogni sei ore per città.
- Nessuna richiesta di GPS o permesso di notifica è necessaria per consultare una previsione o cambiare una soglia.

**Nota tecnica di tracciabilità (non prescrizione di implementazione):** nell'attuale `WeatherSettingsActivity` tutte le opzioni vengono salvate insieme. Separarle in due pagine richiederà di preservare la semantica dei dati condivisi e non sovrascrivere accidentalmente campi dell'altra pagina. Il documento definisce l'esperienza target, non il modo in cui realizzarla.

## 11. Microcopy ed etichette (prime proposte)

| Contesto | Etichetta proposta | Intenzione |
|---|---|---|
| Home | «Inizia allenamento» | Azione chiara, non tecnica |
| Sessione in corso | «Stai camminando» / «Stai correndo» / «Sei fermo» | Interpretazione immediata del riconoscimento |
| Movimento incerto | «Movimento non determinato» | Non inventare una classificazione |
| GPS debole | «GPS poco preciso: distanza e ritmo potrebbero variare» | Collegare lo stato alla conseguenza |
| Mappa live | «Seguimi» / «Mostra percorso» / «Tratti del percorso» | Distinguere centratura, panoramica e accesso all’elenco |
| Opzioni allenamento — pagina unica | «Opzioni allenamento» / «Obiettivo di distanza» / «Checkpoint vocali» / «Configurazione Sprint» | Ingresso secondario identico in Statistiche e Mappa, con stati sintetici dei tre editor (§7.22) |
| Opzioni allenamento — Sprint occupato | «Modifica sprint disponibile al termine della prova» | Riga presente ma disabilitata mentre countdown o Sprint sono in corso, senza bloccare la sessione |
| Sprint — pronto in Allenamento e Mappa live | «⚡ Avvia sprint» / «50 m · Countdown 5 s» | Grande CTA identica nelle due viste, non un accesso alla configurazione |
| Sprint — opzione secondaria (inattivo) | «Modifica sprint» | Cambia il preset durante la sessione senza alterare il grande pulsante di avvio; non disponibile a prova occupata |
| Sprint — completamento naturale | «Sprint completato» / «50 m in 12 s» / «Velocità non disponibile: dati GPS insufficienti» | Feedback vocale breve e banner temporaneo non modale, senza pagina di riepilogo o falsa precisione |
| Sprint — terminato per limite | «Sprint terminato: limite di tempo» | Non dichiarare raggiunto un obiettivo di distanza non conseguito |
| Sprint — countdown o prova in corso | «Sprint tra 5…» / «Sprint in corso» / **«Tieni premuto 2 s per annullare sprint»** | **Stessa grande area fissa**: tap breve innocuo, pressione continua di ~2 s per annullare solo lo sprint; progresso della tenuta visibile |
| Sprint — guida vocale durante prova | Countdown «5, 4…», «Via!», «Mancano 15 secondi» / «Mancano 50 metri», «Sprint terminato» | **Annunci intermedi sul residuo richiesti** per prove abbastanza lunghe; tempo da clock e metri solo da GPS attendibile, mai messaggi scaduti o sovrapposti |
| Obiettivo — annuncio vocale con tempo | «Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi» | Formato deciso: distanza obiettivo + tempo totale al **raggiungimento**, anche se annunciato dopo Sprint; distinto e prioritario rispetto al checkpoint (§7.19) |
| Obiettivo — modifica durante la sessione | «Modifica obiettivo» / «Salva obiettivo» / «Annulla» | Accesso secondario da Allenamento/Mappa senza interrompere la registrazione o spostare Sprint (§7.20) |
| Obiettivo — target già superato al salvataggio | «Obiettivo già raggiunto» | Salvare la distanza scelta anche se già raggiunta, senza annuncio TTS o tempo retroattivo inventato (§7.21) |
| Obiettivo — nuova sessione | «Obiettivo: nessuno · Allenamento libero» | Ogni nuovo allenamento senza target salvo selezione esplicita; non ereditare il target precedente, ma conservarlo nell’eventuale sessione attiva da riprendere (§7.23) |
| Checkpoint — impostazioni dedicate | «Checkpoint vocali» / «Annunci vocali dei checkpoint» / «Ogni quanto: 1 km» / «Personalizzato» / «Intervallo personalizzato (metri)» | Pagina autonoma con default attivo ogni 1 km, scelte rapide e valore libero, persistenti senza sovrascrivere preferenze già salvate |
| Checkpoint — salvataggio | «Impostazioni checkpoint salvate» | Confermare l’esito e conservare intervallo e stato anche tra sessioni |
| Checkpoint — raggiunto | «Due chilometri raggiunti. Tempo trascorso: dodici minuti e trenta secondi» | **Annuncio vocale automatico di distanza + durata totale della sessione**; durante Sprint si accoda e, se ne maturano più di uno, dopo la prova si pronuncia solo il checkpoint più recente, senza perdere la registrazione degli altri |
| Checkpoint — più soglie durante Sprint | «Checkpoint: tre chilometri e duecentocinquanta metri. Tempo trascorso: diciotto minuti e cinquantacinque secondi» | Al termine dello Sprint annunciare **solo il checkpoint più recente**, conservando tutti i checkpoint registrati e dando precedenza all’eventuale obiettivo |
| Dettaglio GPS | «Tratto N · Corsa» / «Torna alla mappa» | Indicare chiaramente il tratto e il ritorno al contesto geografico |
| Mappa offline senza tessere | «Mappa di sfondo non disponibile. La registrazione continua» | Distinguere cartografia dal tracciamento |
| Statistiche | «Corsa + camminata» e «Distanza complessiva» | Evitare ambiguità nei totali |
| Servizio non aggiornato | «Il tracciamento sembra interrotto. Riprendi» | Chiarire il problema e la via d'uscita |
| Fine allenamento — conferma | «Terminare e salvare l’allenamento?» | Esplicitare che la registrazione si concluderà |
| Salvataggio | «Salvataggio dell’allenamento…» | Non promettere successo prima della conferma |
| Riepilogo pronto | «Allenamento salvato» | Conferma chiara, senza attribuire meriti o prestazioni arbitrarie |
| Riepilogo post-salvataggio — principale | «Torna alla Home» | Uscita immediata e non ambigua dopo aver visto il risultato |
| Riepilogo post-salvataggio — secondaria | «Esplora dettagli» | Accesso facoltativo a metriche e tratti, senza nuovo allenamento |
| Riepilogo da Storico — ritorno | «Torna allo Storico» | Rispettare il contesto di provenienza |
| Riepilogo — nessun percorso | «Percorso non disponibile per questa sessione» | Non confondere assenza di traccia con assenza di dati |
| Storico vuoto | «Nessun allenamento salvato. Inizia il primo.» | Stato vuoto orientato all'azione |
| Storico — mini-mappa | «Anteprima del percorso» | Rendere esplicito che il riquadro non è navigabile |
| Storico — scheda | «Apri riepilogo allenamento» | Un unico comando per raggiungere i risultati salvati |
| Storico offline senza sfondo | «Mappa di sfondo non disponibile offline» | Distinguere la mancanza delle tessere dalla traccia GPS locale |
| Meteo — prima visita | «Scegli una città per vedere le previsioni» | Richiedere solo l'informazione davvero necessaria |
| Meteo — senza criteri | «Previsioni orarie» | Evitare un giudizio «favorevole» senza preferenze |
| Meteo — criteri attivi | «Rispetta le tue condizioni» | Precisare che il giudizio deriva dalle preferenze |
| Meteo — errore rete | «Previsioni non disponibili. Riprova» | Non attribuire l'errore al tracking GPS |
| Meteo — notifiche | «Avvisi meteo: disattivati» / «Gestisci avvisi» | Rendere la consultazione indipendente dalle notifiche |
| Meteo — cambio città | «Bologna, Italia · Cambia città» | Rendere il luogo attivo visibile e l’interazione riconoscibile |
| Meteo — selettore città | «Città salvate» / «Gestisci città» | Separare scelta rapida da modifica dell’elenco |
| Meteo — preferenze | «Le mie condizioni» / «Gestisci avvisi» | Due compiti distinti, accessibili dalla consultazione |
| Meteo — condizioni vuote | «Nessun criterio impostato» | Mai definire un’ora favorevole senza preferenze esplicite |
| Meteo — attivazione bloccata senza soglie | «Per ricevere avvisi, imposta almeno una condizione meteo» / «Configura le mie condizioni» | Chiarire il prerequisito e proporre un’azione concreta |
| Meteo — rimozione ultima condizione con avvisi attivi | «Disattivare gli avvisi meteo?» / «Disattiva avvisi e salva» / «Annulla» | Confermare l’effetto collaterale prima del salvataggio |
| Meteo — esito confermato | «Condizioni salvate. Avvisi meteo disattivati» | Comunicare l’esito solo dopo l’applicazione effettiva |
| Meteo — soglie condivise | «Queste condizioni valgono per tutte le città salvate» | Evitare l’idea che riguardino solo la città aperta |
| Meteo — orari | «Orari di controllo» | Non promettere la consegna esatta della notifica |
| Meteo — permesso negato | «Avvisi non disponibili: autorizza le notifiche» | Non confondere switch attivo con capacità di notificare |

## 12. Accessibilità e uso reale

- Controlli principali con area di tocco adeguata (riferimento: almeno 48 × 48 dp) e testi leggibili anche in movimento.
- Informazioni importanti non comunicate solo dal colore; le mappe usano anche legenda e descrizioni testuali.
- Ordine di lettura sensato per TalkBack; pulsanti e filtri con nomi chiari, unità esplicite e stato selezionato.
- Annunci vocali disattivabili per le funzioni opzionali; per gli sprint rendere chiarissimo lo stato della voce e mantenere la priorità del countdown vocale già implementata. Il pulsante deve poter essere usato anche senza fissare lo schermo.
- Gestire font di sistema più grandi, schermi piccoli, orientamento e assenza di rete/sensori senza nascondere le azioni fondamentali.
- Non richiedere contatti prolungati per **avviare** una prova o consultare i dati. **Unica eccezione deliberata:** tenuta di circa 2 s per **annullare** uno sprint ed evitare annullamenti accidentali; verificare l’ergonomia in corsa, con feedback evidente e percorso accessibile alternativo per chi non può mantenere la pressione.

## 13. Criteri di accettazione UX (da verificare con prototipi/test)

- Da Home inattiva l'utente identifica immediatamente come iniziare un allenamento.
- L'utente riconosce in ogni momento se la sessione sta registrando, è interrotta o è terminata.
- Durante una sessione si leggono distanza, durata e ritmo attuale (min/km) senza aprire schede secondarie; le assenze di dato sono riconoscibili.
- L'azione di termine non richiede uno scroll lungo e non può essere attivata irreversibilmente con un solo tocco.
- Informazioni incerte e valori assenti non sono presentati come numeri affidabili.
- Per ogni **nuovo allenamento** il target è assente, anche se la sessione precedente prevedeva un obiettivo. Una scelta esplicita pre-avvio vale per la sessione imminente; se il tentativo di avvio viene annullato, può essere ripreso senza reinserire il target. Riprendere la **stessa sessione attiva** conserva invece il suo obiettivo; i riepiloghi storici non perdono i target precedenti. Sprint e checkpoint mantengono separatamente le preferenze persistenti (§7.23).
- L’obiettivo di distanza può essere modificato o rimosso **anche durante un allenamento attivo** da «Opzioni allenamento → Modifica obiettivo»: salvataggio valido efficace subito, annullamento innocuo, tracciamento continuo e pulsante Sprint sempre nello stesso dock; target futuri annunciati solo al reale raggiungimento. Se il target salvato è **inferiore o uguale alla distanza già percorsa**, mostrare **«Obiettivo già raggiunto»** senza annuncio vocale retroattivo né tempo fittizio (§7.20–§7.21).
- Il raggiungimento attendibile dell’obiettivo di distanza produce una volta sola il messaggio con **distanza target e tempo totale misurato al raggiungimento**: «Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi». In presenza di checkpoint coincidente, obiettivo prima e checkpoint poi; durante Sprint pronunciarlo dopo l’annuncio finale senza ricalcolare il tempo; la sessione non termina da sola (§7.19).
- Un checkpoint effettivamente raggiunto genera **un annuncio vocale automatico e sintetico con distanza raggiunta e tempo totale dall’inizio**, non ripetuto e senza interazione visiva obbligatoria. Se cade durante countdown/Sprint, non interrompe la voce della prova ma **viene recuperato dopo il messaggio finale Sprint (solo il più recente se sono stati raggiunti più checkpoint nella prova)**; non recuperare insieme gli avvisi ordinari periodici. Quando coincide con l’obiettivo complessivo, **entrambi sono pronunciati in coda, prima l’obiettivo e poi il checkpoint**, anche se raggiunti durante Sprint, senza audio sovrapposto e senza chiudere la sessione.
- Se durante un singolo Sprint maturano **più checkpoint attendibili**, il TTS annuncia **solo l’ultimo** con distanza e tempo di raggiungimento; **tutti** i checkpoint restano registrati. L’obiettivo raggiunto conserva il suo annuncio prioritario, prima dell’ultimo checkpoint. Nessuna raffica di notifiche vocali arretrate.
- Durante uno Sprint, gli annunci ordinari della sessione **non si sovrappongono mai** alla voce della prova: le letture periodiche sono sospese/scartate, mentre **obiettivo e checkpoint effettivamente raggiunti costituiscono eventi significativi da recuperare dopo l’annuncio finale Sprint nell’ordine obiettivo → ultimo checkpoint maturato (se sono più di uno)**, senza leggere a cascata tutti i vecchi messaggi; le comunicazioni aggiornate riprendono successivamente. Eventuali avvisi urgenti sono gestiti in sequenza senza audio simultaneo. L’interfaccia resta **essenziale, senza grandi contatori di avanzamento a tempo o distanza**: lo stato è sintetico, il dock del pulsante non si sposta e l’utente può seguire countdown, partenza e fine attraverso i feedback vocali. Annunci intermedi **richiesti** per comunicare quanto manca: secondi residui da cronometro negli sprint a tempo, metri residui da GPS affidabile negli sprint a distanza, senza annunciare soglie tardive nelle prove brevissime.
- Alla conclusione di uno Sprint, un breve annuncio vocale e un riepilogo visivo non modale appaiono **senza cambiare schermata**; il dock rimane fermo, torna automaticamente «Avvia sprint» senza conferme e consente una nuova prova soltanto con un nuovo tap. Dati mancanti, annullamento e limite di tempo sono distinti da «obiettivo raggiunto», e i dettagli rimangono consultabili in seguito.
- La pagina unica **«Opzioni allenamento»** è raggiungibile sia da Statistiche sia da Mappa live, senza interrompere il tracciamento. Presenta tre accessi con stato salvato e non duplica i form: «Obiettivo di distanza», «Checkpoint vocali», «Configurazione Sprint»; conserva il contesto al ritorno, lascia il grande comando Sprint fisso solo nelle viste operative e rende la modifica Sprint indisponibile con spiegazione durante preparazione/countdown/prova (§7.22).
- Il preset Sprint può essere modificato durante una sessione da «Opzioni → Modifica sprint» solo a prova inattiva; un salvataggio valido aggiorna il prossimo sprint e resta persistente, mentre «Annulla» preserva i vecchi valori. La configurazione non avvia una prova e non interrompe il tracking.
- Uno sprint già configurato può essere **avviato con un solo tocco sia da Allenamento sia da Mappa live**, senza cambiare schermata, con una CTA grande nel **medesimo dock inferiore immobile** in entrambe; durante preparazione/countdown/prova **la stessa CTA cambia in «Tieni premuto 2 s per annullare»**. Un tap breve non annulla, una tenuta continua completa sì; il rilascio anticipato non produce effetti. Countdown, esecuzione, completamento/annullamento condividono lo stato e non si confondono con il termine della sessione. Ripetere uno sprint non richiede una nuova configurazione.
- Dopo «Termina e salva», il riepilogo compare automaticamente **solo quando la sessione risulta effettivamente salvata**; in caso di esito non verificabile appare uno stato appropriato.
- Il riepilogo distingue distanza complessiva, durata totale, ritmo medio complessivo e ripartizione dell'andatura; non inventa classificazioni o dati cumulativi.
- Dopo salvataggio, «Torna alla Home» è la CTA principale e «Esplora dettagli» quella secondaria; entrambe sono reperibili senza scroll prolungato e rispettano il comportamento del comando Indietro.
- Nello Storico, ogni sessione ha una scheda cronologica leggibile con mini-mappa **statica e monocolore**; l'intera scheda apre il riepilogo, mentre l'anteprima non ha zoom, pan o pulsanti propri. I cambi di attività non cambiano colore nell'anteprima e le lacune GPS non sono collegate artificialmente.
- Esportazione ed eliminazione sono reperibili ma non competono con l'apertura di un dettaglio storico.
- Con GPS debole, offline o sensori mancanti l'utente riceve spiegazioni e alternative coerenti con le funzioni esistenti.
- Nel Meteo, la prima visita richiede solo la scelta di una città; la consultazione delle previsioni non obbliga a impostare soglie, orari o notifiche. **Una sola città alla volta**, con cambio rapido tra le città salvate; il passaggio non altera avvisi o preferenze meteorologiche. **Le mie condizioni** e **Gestisci avvisi** sono pagine distinte; le soglie sono facoltative e comuni alle città, gli avvisi coinvolgono tutte le città salvate e richiedono città/orari configurati. Senza soglie non vengono assegnati giudizi di favorevolezza. È **deciso** che gli avvisi richiedono almeno una soglia personalizzata salvata, oltre a città e orari. È **deciso** che rimuovere l’ultima soglia con avvisi attivi richiede conferma prima di disattivarli contestualmente, preservando città e orari; il caricamento automatico all’apertura resta da implementare.
- Nella mappa live le tre metriche essenziali restano visibili; da polilinea o elenco si apre una pagina autonoma per ogni tratto con **mini-mappa contestuale**, senza dialoghi bloccanti né sospensione del tracking. «Torna alla mappa» ripristina selezione e camera, mentre «Apri sulla mappa» focalizza il tratto.

## 14. Fuori ambito

- Nuovi algoritmi di classificazione, nuove metriche cumulative, cloud/account, integrazioni social, funzioni manuali di pausa non presenti.
- Implementazione UI, riscrittura dell'app, modifica database/API, nuove librerie, permessi o servizi.
- Decisioni visive finali (palette, tipografia, component library) prima dei wireframe e delle verifiche.

## 15. Decisioni consolidate e punti ancora aperti prima della PR

1. **Deciso:** due profili serviti con progressiva esposizione delle funzioni; avvio immediato con controlli di posizione/GPS e obiettivo facoltativo prima del tap; **configurazione Sprint facoltativa prima della sessione**, non prerequisito dell’avvio.
2. **Deciso:** durante l’allenamento sono prioritarie le **statistiche**; la mappa è accessibile su richiesta. La terza metrica principale è **ritmo attuale (min/km)**, insieme a distanza e durata; con dato non attendibile si mostra uno stato di indisponibilità.
3. **Deciso:** la mappa live è una vista dedicata quasi a schermo intero con tre metriche essenziali persistenti; il tracking continua. **Deciso:** i dettagli GPS di un singolo tratto si consultano su una **pagina dedicata con mini-mappa contestuale**, aperta da mappa o elenco, non in un pannello inferiore. «Torna alla mappa» mantiene contesto e selezione; «Apri sulla mappa» focalizza il tratto.
4. **Deciso:** dopo «Termina e salva» e la conferma del salvataggio locale, aprire **subito il riepilogo finale**. Priorità a distanza complessiva, durata totale e ritmo medio complessivo; corsa/camminata, obiettivo e anteprima del percorso seguono. **«Torna alla Home» è la CTA principale, «Esplora dettagli» secondaria.** Da una sessione aperta dallo Storico si torna invece allo Storico. La resa visuale resta da testare.
5. **Deciso:** lo **Storico** è un elenco ordinato dal più recente, con **schede più ampie** che mostrano data/ora, distanza, durata, ritmo medio e una **mini-mappa non interattiva con tracciato monocolore**. Il colore unico semplifica l'identificazione del *luogo*, mentre **i colori per attività rimangono nella mappa completa**. La scheda apre il riepilogo; la mappa navigabile è nei dettagli. Le anteprime non devono introdurre servizi cloud, falsa geometria o gesti interattivi.
6. **Deciso:** il Meteo parte dalla **consultazione delle previsioni di temperatura/vento**, anche senza notifiche o criteri; la scelta iniziale di una città è l'unico passaggio necessario. Le soglie sono progressive, e senza preferenze nessuna ora viene indicata come «favorevole». **Proposto come modifica da implementare:** caricamento automatico all'apertura con città già selezionata. **Deciso:** una città alla volta, con selettore rapido fra le città salvate, senza confronto simultaneo. **Deciso:** «Le mie condizioni» e «Gestisci avvisi» sono due pagine indipendenti; entrambe applicano preferenze condivise tra le città, senza obblighi per la consultazione.
7. **Deciso:** per attivare gli avvisi Meteo è necessaria **almeno una condizione personalizzata salvata**; non si attivano automaticamente né si propongono soglie predefinite. Questo è un **cambiamento funzionale rispetto al codice attuale** e richiede verifiche anche nei controlli programmati e nelle configurazioni esistenti. **Deciso:** se si rimuove l’ultima soglia mentre gli avvisi sono attivi, richiedere conferma esplicita prima di salvare le condizioni vuote e disattivare gli avvisi contestualmente; annullando non modificare la configurazione, confermando conservare città e orari.
8. **Deciso:** **Sprint si configura una volta e si avvia durante la corsa tramite un unico pulsante grande, persistente e ad alto contrasto**, senza entrare in un form o confermare. Countdown vocale prima della prova; durante la prova il pulsante cambia stato e non consente avvii doppi; dopo il completamento si può ripetere con gli stessi parametri. **Deciso:** lo stesso comando è sempre visibile, **immobile e con geometria identica**, nello stesso dock, **sia da Allenamento sia da Mappa live**; **tap per avviare, tenuta continua di circa 2 secondi per annullare**, sulla **medesima superficie** durante preparazione/countdown/prova; rilascio anticipato innocuo e indicatore di avanzamento della tenuta. **Deciso:** conservazione locale del preset anche tra allenamenti, senza nuove configurazioni obbligatorie, con modifica esplicita e stato «Configura sprint» se assente. **Deciso:** modifica possibile anche durante una sessione, tramite «Opzioni allenamento → Configurazione Sprint» e soltanto a prova inattiva; salvataggio per le prove successive senza fermare il tracking, uscita senza salvataggio innocua. **Deciso:** fine naturale con annuncio vocale breve e riepilogo temporaneo non modale nella stessa vista (Statistiche o Mappa), quindi ripristino automatico della CTA nello stesso dock; dettagli Sprint solo su richiesta, distinzione esplicita fra completamento, dati insufficienti, annullamento e fine per limite. **Deciso:** durante lo Sprint preferire **voce e stato visivo essenziale**, senza grandi contatori per metri o secondi residui, per uso con telefono al braccio. **Deciso:** la guida vocale include **countdown, segnale di partenza, annunci intermedi di quanto manca e fine**: secondi residui negli sprint a tempo; metri residui negli sprint a distanza quando il GPS è affidabile. Priorità a partenza/fine, nessun messaggio in ritardo e riduzione degli annunci nelle prove brevissime. **Deciso:** frequenza **automatica e adattiva** degli annunci intermedi in funzione della durata/distanza; non sono previste preferenze manuali di frequenza. **Deciso:** gli annunci ordinari della sessione non devono mai sovrapporsi a quelli Sprint: sospenderli/scartarli durante countdown e prova, non accumularli a fine Sprint, riprendere gli aggiornamenti futuri dopo il messaggio finale; gestire gli eventi critici in sequenza. **Da validare:** misure effettive del dock, ergonomia della tenuta 2 s, cadenza/latenza concreta degli annunci, percorso alternativo accessibile e fallback TTS. Dettagli in §7.10, soprattutto §7.10.10–§7.10.13.
9. **Deciso:** ogni checkpoint dell’allenamento **attendibilmente raggiunto** viene segnalato con **un annuncio vocale automatico breve che comunica distanza raggiunta e tempo totale trascorso dall’avvio della sessione**, senza passaggi di schermata o tocchi. La voce del checkpoint non può interferire con quella Sprint: il checkpoint raggiunto durante countdown/prova è mantenuto come evento di progresso da **recuperare dopo l’annuncio finale Sprint**, mentre i normali aggiornamenti periodici saltati non vengono riprodotti in ritardo. **Deciso:** checkpoint vocali **attivi di default ogni 1 km nei nuovi allenamenti** e **pagina autonoma «Checkpoint vocali»** per attivazione/disattivazione e intervallo, con rispetto delle preferenze esplicite pregresse. Deduplicazione e fallback senza audio; da verificare su dispositivo. Specifica in §7.11. **Deciso:** se l’obiettivo complessivo è raggiunto contemporaneamente a un checkpoint, i due eventi devono essere pronunciati **in coda — obiettivo prima, checkpoint dopo**, perché il checkpoint informa dei progressi senza consultare il telefono. Obiettivo raggiunto ≠ fine automatica dell’allenamento. Specifica in §7.12–§7.14. **Il tempo annunciato resta quello del raggiungimento**, anche se il checkpoint viene pronunciato dopo uno Sprint. **Deciso:** se durante lo Sprint vengono raggiunti più checkpoint, annunciare soltanto quello più recente dopo la prova, registrando tutti i traguardi; mantenere l’eventuale obiettivo distinto e prioritario. Specifica in §7.15. **Default 1 km e pagina dedicata:** §7.16. **Deciso:** selezione sia di intervalli rapidi (es. 250 m, 500 m, 1 km, 2 km, 5 km) sia di una **distanza checkpoint personalizzata**, con validazione, unità visibile e persistenza (§7.17). **Deciso:** se l’intervallo viene cambiato e salvato mentre una sessione è attiva, si applica **subito** dal primo multiplo del nuovo intervallo superiore alla distanza già percorsa; es. a 3,2 km scegliendo 750 m il prossimo checkpoint è 3,75 km, senza annunci passati (§7.18).
**Decisione aggiuntiva sugli obiettivi (§7.19):** il messaggio vocale contiene **distanza target e tempo complessivo al raggiungimento**, con forma canonica «Obiettivo di 5 km raggiunto, in 30 minuti e 40 secondi». Anche se accodato dopo Sprint, il tempo pronunciato resta quello dell’evento; se c’è un checkpoint coincidente, l’obiettivo completo viene prima e il checkpoint dopo. La registrazione continua.

**Decisione aggiuntiva sugli obiettivi in-sessione (§7.20–§7.21):** il target di distanza può essere **modificato, rimosso o conservato** anche durante l’allenamento da un comando secondario, senza arrestare cronometro/GPS e senza spostare Sprint; il salvataggio applica subito la modifica. **Deciso:** un nuovo obiettivo inferiore **o uguale** al totale già percorso viene salvato e mostrato come **«Obiettivo già raggiunto»**, senza annuncio vocale retroattivo, tempo storico inventato o arresto dell’allenamento. Un target superiore al totale corrente verrà annunciato solo al successivo raggiungimento effettivo con tempo misurato.

**Decisione aggiuntiva di architettura informativa (§7.22):** «Opzioni allenamento» è una **pagina unica** raggiungibile da Statistiche e Mappa live, con voci distinte **Obiettivo di distanza**, **Checkpoint vocali** e **Configurazione Sprint**, ciascuna con stato salvato e proprio editor. Il tracking continua; al ritorno va preservato il contesto della Mappa e il dock Sprint non si sposta nelle viste operative. Durante Sprint occupato il solo editor Sprint non è disponibile, con spiegazione; il percorso di avvio immediato da Home rimane invariato.


**Decisione aggiuntiva sul default dell’obiettivo (§7.23):** **nessun obiettivo di distanza viene riportato automaticamente nell’allenamento successivo**; ogni nuova sessione inizia libera, salvo selezione esplicita da Home o dalle opzioni. Riprendere una sessione **già attiva** non la resetta; un avvio interrotto non deve disperdere la bozza di obiettivo scelta per quel tentativo. Il target registrato nelle sessioni concluse rimane nello Storico. La persistenza del preset Sprint e dei checkpoint vocali resta invariata.

10. **Da validare:** comandi camera («Seguimi»/«Mostra percorso»/«Deseleziona»), dimensioni e resa della mini-mappa, eventuale navigazione «Precedente/Successivo» fra tratti, forma dell’elenco tratti, comportamento su schermi piccoli. Il meteo è una funzione principale della giornata o uno strumento occasionale nelle impostazioni?
11. Una sola proposta UX complessiva oppure un primo documento focalizzato su Home + allenamento attivo + mappa + fine sessione?

**Da validare per i checkpoint:** limiti numerici realmente supportabili per il valore libero, dettagli del percorso di accesso in-sessione, migrazione delle preferenze pregresse, casi di distanza GPS temporaneamente incerta e gestione di TTS generale disabilitato. **Sono decisioni confermate, non punti aperti:** checkpoint vocali attivi di default ogni 1 km, pagina dedicata accessibile anche dalla sessione, scelte rapide più intervallo personalizzato e **applicazione immediata ai soli checkpoint futuri dopo salvataggio** (§7.18).

## 16. Tracciabilità

- Catalogo: [`docs/catalogo-funzionalita.md`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/docs/catalogo-funzionalita.md)
- Sprint: [`SprintTrial.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/SprintTrial.java), [`TrackingService.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/TrackingService.java)
- Home: [`MainActivity.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/MainActivity.java)
- Sessione attiva: [`ActiveSessionActivity.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/ActiveSessionActivity.java)
- Storico: [`HistoryActivity.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/HistoryActivity.java)
- Dettaglio: [`SessionDetailActivity.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/SessionDetailActivity.java)
- Meteo: [`WeatherSettingsActivity.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/WeatherSettingsActivity.java), [`WeatherRules.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/WeatherRules.java), [`WeatherMonitor.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/WeatherMonitor.java)
- Mappa e camera GPS: [`TrackMap.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/TrackMap.java)
- Elenco e selezione tratti: [`RouteSectionList.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/RouteSectionList.java), [`RouteSectionSelection.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/RouteSectionSelection.java), [`RouteSections.java`](https://github.com/VittorioS/app-running/blob/cd27fc6b7656bef80bb6479484425c754a676448/app/src/main/java/it/example/passolibero/RouteSections.java)


## 17. Registro delle decisioni confermate nella revisione

> **Regola di lettura:** questo registro raccoglie le risposte esplicitamente confermate nella revisione successiva alla v2.20. In caso di divergenza con i capitoli precedenti, prevale la decisione più recente; le correzioni saranno integrate nei capitoli tematici prima della chiusura della specifica. Sono requisiti UX proposti, **non funzionalità già implementate**. La domanda 42 non è ancora stata decisa.

### D01 — Storico: destinazione delle schede

**Domanda:** Cosa deve succedere quando si tocca una sessione ancora attiva nello Storico?

**Risposta confermata:** Una sessione attiva apre direttamente l'allenamento in corso; una sessione salvata apre il riepilogo definitivo. Non usare il riepilogo finale per sessioni non concluse.

**Riferimenti:** §9, §10.4.

### D02 — Da quale vista è possibile terminare la sessione?

**Domanda (sintesi):** Da quale vista è possibile terminare la sessione?

**Risposta confermata:** «Termina e salva» soltanto dalla vista Statistiche, sempre visibile senza scroll e con conferma. Dalla Mappa live si torna a Statistiche con un tocco; il dock Sprint resta nella medesima posizione.

**Riferimenti:** §6, §9.

### D03 — Dove porta «Configura sprint» se il preset non esiste?

**Domanda (sintesi):** Dove porta «Configura sprint» se il preset non esiste?

**Risposta confermata:** Il comando nel dock Sprint apre direttamente l'editor di configurazione Sprint, senza pagina indice intermedia.

**Riferimenti:** §7.10.

### D04 — Come raggiungere gli editor durante l'allenamento?

**Domanda (sintesi):** Come raggiungere gli editor durante l'allenamento?

**Risposta confermata:** Da Statistiche e Mappa live sono disponibili accessi diretti ai tre editor: obiettivo distanza, checkpoint vocali e Sprint, senza sospendere il tracking.

**Riferimenti:** §7.22.

### D05 — La pagina unica «Opzioni allenamento» resta accessibile durante una sessione?

**Domanda (sintesi):** La pagina unica «Opzioni allenamento» resta accessibile durante una sessione?

**Risposta confermata:** No: durante la sessione usare accessi diretti ai tre editor; «Opzioni allenamento» rimane disponibile soltanto fuori allenamento (per esempio Home/Impostazioni). Questa scelta sostituisce la previsione in-sessione di §7.22.

**Riferimenti:** §7.22.

### D06 — Quanto dura la bozza dell'obiettivo scelta prima dell'avvio?

**Domanda (sintesi):** Quanto dura la bozza dell'obiettivo scelta prima dell'avvio?

**Risposta confermata:** L'obiettivo esplicito pre-avvio sopravvive a chiusura e riapertura app finché la sessione inizia oppure l'utente lo elimina esplicitamente. Dopo l'avvio vale per quella sessione; il prossimo nuovo allenamento riparte senza obiettivo.

**Riferimenti:** §7.23.

### D07 — Un obiettivo già raggiunto va annunciato se nel frattempo è stato modificato?

**Domanda (sintesi):** Un obiettivo già raggiunto va annunciato se nel frattempo è stato modificato?

**Risposta confermata:** Sì: conservare evento, distanza target originale e istante di raggiungimento; annunciarlo una volta sola anche se la configurazione cambia prima del TTS. Evitare duplicati.

**Riferimenti:** §7.19–§7.21.

### D08 — Cosa accade ai checkpoint vocali in coda quando l'utente disabilita la voce?

**Domanda (sintesi):** Cosa accade ai checkpoint vocali in coda quando l'utente disabilita la voce?

**Risposta confermata:** Scartare tutti gli annunci checkpoint pendenti, senza cancellare gli eventi registrati e senza recuperi retroattivi alla riattivazione. Annunci obiettivo e Sprint indipendenti.

**Riferimenti:** §7.11, §7.18.

### D09 — Cosa accade agli eventi checkpoint pendenti quando cambia l'intervallo?

**Domanda (sintesi):** Cosa accade agli eventi checkpoint pendenti quando cambia l'intervallo?

**Risposta confermata:** Gli eventi già registrati mantengono distanza e ora originali; se sono più di uno annunciare solo il più recente. Il nuovo intervallo vale in avanti, senza recuperare soglie retroattive.

**Riferimenti:** §7.15–§7.18.

### D10 — Se il TTS è temporaneamente indisponibile quando si raggiunge l'obiettivo?

**Domanda (sintesi):** Se il TTS è temporaneamente indisponibile quando si raggiunge l'obiettivo?

**Risposta confermata:** Recuperare l'annuncio al ritorno del TTS soltanto mentre lo stesso allenamento è ancora attivo, con dettagli originali, una sola volta e rispettando la priorità Sprint.

**Riferimenti:** §7.19.

### D11 — Se il TTS torna dopo un guasto e più checkpoint sono in attesa?

**Domanda (sintesi):** Se il TTS torna dopo un guasto e più checkpoint sono in attesa?

**Risposta confermata:** Annunciare solo l'ultimo checkpoint pendente, con distanza e tempo originari; conservare tutti gli eventi nel registro e dare priorità alla voce Sprint. Distinto dalla disattivazione volontaria.

**Riferimenti:** §7.11–§7.15.

### D12 — Come gestire l'assenza GPS durante uno Sprint a distanza?

**Domanda (sintesi):** Come gestire l'assenza GPS durante uno Sprint a distanza?

**Risposta confermata:** Non fermare automaticamente lo Sprint: convertire soltanto i metri rimanenti in una stima di tempo basata su ritmo personale. Evitare di imporre un nuovo Sprint.

**Riferimenti:** §7.10.

### D13 — Quale ritmo usare per stimare lo Sprint senza GPS?

**Domanda (sintesi):** Quale ritmo usare per stimare lo Sprint senza GPS?

**Risposta confermata:** Priorità a velocità attendibile rilevata nello Sprint in corso, quindi ritmi di Sprint precedenti; solo in subordine usare riferimenti di corsa ordinaria.

**Riferimenti:** §7.10.

### D14 — Come ricorrere ai ritmi di corsa non Sprint per il fallback?

**Domanda (sintesi):** Come ricorrere ai ritmi di corsa non Sprint per il fallback?

**Risposta confermata:** Se mancano ritmi Sprint attendibili usare il ritmo affidabile della corsa corrente, poi ritmi delle corse precedenti; applicare una maggiorazione prudenziale alla velocità stimata, da precisare.

**Riferimenti:** §7.10.

### D15 — Come interpretare il fattore +50% del fallback?

**Domanda (sintesi):** Come interpretare il fattore +50% del fallback?

**Risposta confermata:** Aumentare la velocità stimata del 50% (velocità × 1,5; ritmo min/km ÷ 1,5), non dimezzare il ritmo. È un'assunzione di fallback, non una garanzia fisiologica.

**Riferimenti:** §7.10.

### D16 — Se il GPS torna durante uno Sprint convertito a tempo, riprendere i dati GPS?

**Domanda (sintesi):** Se il GPS torna durante uno Sprint convertito a tempo, riprendere i dati GPS?

**Risposta confermata:** Decisione finale: sì, usare i nuovi dati GPS attendibili, preservando il progresso già stimato e correggendo eventuali sovrapposizioni con misure effettive. Sostituisce la precedente ipotesi di conversione irreversibile a tempo.

**Riferimenti:** §7.10.

### D17 — Come presentare uno Sprint concluso tramite stima temporale?

**Domanda (sintesi):** Come presentare uno Sprint concluso tramite stima temporale?

**Risposta confermata:** Annunciare brevemente «Sprint completato»; nel riepilogo distinguere la distanza realmente misurata da GPS dalla porzione stimata/non verificata, senza dichiarare tutta la distanza misurata.

**Riferimenti:** §7.10, §9.

### D18 — La distanza Sprint stimata conta per distanza e obiettivo della sessione?

**Domanda (sintesi):** La distanza Sprint stimata conta per distanza e obiettivo della sessione?

**Risposta confermata:** No: il progresso stimato vale esclusivamente per il target Sprint, non per distanza GPS totale, obiettivo o checkpoint dell'allenamento. Se il GPS torna affidabile, le nuove distanze reali alimentano il totale della sessione.

**Riferimenti:** §7.10, §7.19.

### D19 — Cosa usare se non esiste alcun ritmo personale attendibile?

**Domanda (sintesi):** Cosa usare se non esiste alcun ritmo personale attendibile?

**Risposta confermata:** Come fallback estremo ipotizzare un ritmo base di corsa, ancora da definire e validare, con velocità maggiorata del 50%; dichiarare la scarsa confidenza della stima. Non è stato approvato alcun valore numerico di ritmo base.

**Riferimenti:** §7.10.

### D20 — Quando applicare il limite cautelativo di 8 secondi?

**Domanda (sintesi):** Quando applicare il limite cautelativo di 8 secondi?

**Risposta confermata:** Soltanto nel caso estremo di GPS assente e nessun dato personale attendibile di ritmo; gli 8 secondi sono tempo totale dello Sprint escluso il countdown preparatorio, non una soglia universale di sicurezza fisiologica.

**Riferimenti:** §7.10.

### D21 — Come descrivere l'arresto per limite estremo di 8 secondi?

**Domanda (sintesi):** Come descrivere l'arresto per limite estremo di 8 secondi?

**Risposta confermata:** Pronunciare «Sprint concluso per limite prudenziale», senza dire che il target è stato raggiunto; nel riepilogo distinguere misure osservate e stime parziali.

**Riferimenti:** §7.10, §9.

### D22 — Gli Sprint con ritmo personale affidabile hanno limite di 8 secondi?

**Domanda (sintesi):** Gli Sprint con ritmo personale affidabile hanno limite di 8 secondi?

**Risposta confermata:** No: non applicare il tetto di 8 secondi. Stimare soltanto i metri residui rispetto a quelli già misurati (esempio 100 m target, 40 m GPS, 60 m stimati), fino al completamento del target.

**Riferimenti:** §7.10.

### D23 — Cosa annunciare quando si perde il GPS durante lo Sprint?

**Domanda (sintesi):** Cosa annunciare quando si perde il GPS durante lo Sprint?

**Risposta confermata:** Messaggio breve «GPS perso. Continuo a tempo», quindi tempo residuo stimato, evitando un'interazione visiva invasiva.

**Riferimenti:** §7.10.

### D24 — Quanto tollerare una perdita GPS transitoria durante uno Sprint?

**Domanda (sintesi):** Quanto tollerare una perdita GPS transitoria durante uno Sprint?

**Risposta confermata:** Concedere 2 secondi prima del passaggio alla stima temporale; durante questa tolleranza non conteggiare distanze GPS inaffidabili.

**Riferimenti:** §7.10.

### D25 — Cosa fare quando arrivano campioni GPS affidabili riferiti a un intervallo già stimato?

**Domanda (sintesi):** Cosa fare quando arrivano campioni GPS affidabili riferiti a un intervallo già stimato?

**Risposta confermata:** Sostituire la porzione stimata sovrapposta con la misura GPS affidabile, senza conteggiare due volte la stessa progressione e senza perdere altre parti valide.

**Riferimenti:** §7.10.

### D26 — Se GPS tardivo corregge al ribasso uno Sprint già concluso?

**Domanda (sintesi):** Se GPS tardivo corregge al ribasso uno Sprint già concluso?

**Risposta confermata:** Non riaprire Sprint né richiedere corsa aggiuntiva. La conclusione tramite stima resta conclusione; aggiornare il riepilogo indicando con precisione quota misurata e quota inizialmente stimata/corretta.

**Riferimenti:** §7.10, §9.

### D27 — Se la correzione al ribasso arriva mentre Sprint è ancora attivo?

**Domanda (sintesi):** Se la correzione al ribasso arriva mentre Sprint è ancora attivo?

**Risposta confermata:** Ricalcolare la distanza residua usando misure affidabili e stime non sovrapposte; lo Sprint può durare più di quanto inizialmente previsto.

**Riferimenti:** §7.10.

### D28 — Introdurre un nuovo tetto temporale negli Sprint con ritmo personale affidabile?

**Domanda (sintesi):** Introdurre un nuovo tetto temporale negli Sprint con ritmo personale affidabile?

**Risposta confermata:** No: mantenere il completamento per distanza senza ulteriore limite di durata. L'aumento stimato della velocità può far terminare prima del previsto; le correzioni GPS si applicano per precisione, non implicano automaticamente un prolungamento.

**Riferimenti:** §7.10.

### D29 — Da quale istante stimare dopo i 2 secondi di tolleranza GPS?

**Domanda (sintesi):** Da quale istante stimare dopo i 2 secondi di tolleranza GPS?

**Risposta confermata:** Dall'inizio effettivo della perdita di segnale, coprendo retroattivamente anche i primi 2 secondi; rimuovere ogni sovrapposizione qualora ritornino misure GPS affidabili.

**Riferimenti:** §7.10.

### D30 — Quale riferimento usare a ogni nuova perdita GPS dello stesso Sprint?

**Domanda (sintesi):** Quale riferimento usare a ogni nuova perdita GPS dello stesso Sprint?

**Risposta confermata:** Ricalcolare la velocità utilizzando tutti i segmenti Sprint misurati da GPS affidabile, escludendo progressi solo stimati; privilegiare misure reali dello Sprint corrente rispetto a precedenti storici.

**Riferimenti:** §7.10.

### D31 — Se si interrompe completamente il servizio di tracking durante Sprint?

**Domanda (sintesi):** Se si interrompe completamente il servizio di tracking durante Sprint?

**Risposta confermata:** Concludere lo Sprint con stato «Interrotto per problema tecnico»; non riprenderlo automaticamente alla riattivazione del servizio. L'allenamento generale può invece essere recuperato.

**Riferimenti:** §6.5, §7.10.

### D32 — Come conservare uno Sprint interrotto per problema tecnico?

**Domanda (sintesi):** Come conservare uno Sprint interrotto per problema tecnico?

**Risposta confermata:** Mantenerlo nel riepilogo e nello Storico con stato «Interrotto per problema tecnico», tempo, distanza e sole statistiche attendibili, distinto da Sprint completato o annullato volontariamente.

**Riferimenti:** §7.10, §9–§10.

### D33 — Se il tracking generale non riparte, è possibile salvare la sessione parziale?

**Domanda:** Se il tracking generale non riparte, è possibile salvare la sessione parziale?

**Risposta confermata:** Sì: presentare «Riprova» e «Termina e salva», conservando i dati attendibili fino all'interruzione e indicando chiaramente la registrazione parziale, senza inventare periodi non osservati.

**Riferimenti:** §6.5, §9.

### D34 — Come mostrare nel riepilogo il tempo senza tracking?

**Domanda:** Come mostrare nel riepilogo il tempo senza tracking?

**Risposta confermata:** Mostrare distintamente durata complessiva e tempo senza tracking; non attribuire periodi senza servizio a corsa, camminata o sosta. **Aggiornamento successivo D37:** il valore principale della durata sarà la somma dei periodi monitorati, mentre la durata cronologica totale resterà separata.

**Riferimenti:** §9.

### D35 — Quando persistere gli allenamenti in un'app completamente offline?

**Domanda:** Quando persistere gli allenamenti in un'app completamente offline?

**Risposta confermata:** Scrivere progressivamente nel database locale GPS, statistiche ed eventi; «Termina e salva» finalizza prevalentemente una sessione già persistita. Evitare meccanismi complessi da servizio online. In caso di terminazione imprevista, preservare i dati già scritti.

**Riferimenti:** §6.5, §9.
