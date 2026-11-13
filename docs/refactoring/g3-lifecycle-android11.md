# ADR-002 / #24 — Gate G3 su telefono fisico Android 11 (P4a)

**Scopo:** registrare evidenze riproducibili del lifecycle reale START/STOP/recovery di `TrackingService` prima di dichiarare completa la [#24](https://github.com/VittorioS/app-running/issues/24) e sbloccare la [#25](https://github.com/VittorioS/app-running/issues/25). Questo è un **protocollo da eseguire**, non un rapporto di test eseguiti. Integra la [matrice generale](../issue-5-device-matrix.md), `docs/invariants.md` e `AGENTS.md`.

## 1. Stato di partenza e limiti

- Le estrazioni e le caratterizzazioni della #24 sono integrate tramite PR [#49](https://github.com/VittorioS/app-running/pull/49)–[#58](https://github.com/VittorioS/app-running/pull/58). Per la #58 la [CI post-merge #43317680790](https://github.com/VittorioS/app-running/actions/runs/43317680790) ha concluso `verify` e `android11` con successo. Questi risultati **non dimostrano** il comportamento su un telefono fisico.
- Non attribuire un PASS G3 ai test JVM, ai fake, all'emulatore API 30, a una build riuscita, né a questo documento.
- Non fare merge automatico, release, alterazioni di firma, `minSdk`, permessi, schema SQLite v7, chiavi SharedPreferences o nuovi tracciamenti/endpoint. Non eseguire i test su un'installazione con storico personale non protetto da backup.

## 2. Preparazione obbligatoria (G3 non inizia senza queste condizioni)

1. Usare un **telefono fisico Android 11 / API 30 di prova**, con un'installazione dedicata e dati/percorsi sintetici. Registrare modello, build Android, versione e `versionCode` APK, commit di provenienza, modalità di installazione, configurazione permessi GPS e ottimizzazione batteria. Non condividere identificativi privati.
2. Verificare un **backup indipendente e recuperabile** prima di qualsiasi aggiornamento o esperimento distruttivo. Non disinstallare o sostituire l'app con storico utente; se la copia sicura non è disponibile usare un dispositivo sacrificabile con dati creati solo per il test. Non fare tentativi con chiavi di firma o workflow privilegiati.
3. Annotare la baseline di sessioni sintetiche visibili nello Storico (numero e identificativi se mostrati, durata, stato, eventuale export anonimo). Non caricare `sessions.db`, preferenze, coordinate o log GPS raw nella issue.
4. Eseguire le prove in un contesto sicuro, non alla guida. Ogni scenario deve partire da uno **stato noto** (nessuna sessione attiva, salvo quando richiesto) e indicare esplicitamente se la prova è stata ripetuta con rete disattivata.
5. Conservare l'esito CI associato all'**esatto SHA** testato: `verify`, `android11` e test avviati/terminati. La CI precedente è solo baseline; una nuova PR va valutata sul proprio SHA.

## 3. Matrice dei test reali — compilare la colonna Esito

Usare esclusivamente **PASS / FAIL / NON ESEGUITO**; non scrivere PASS senza osservazioni pertinenti. Registrare il risultato di ciascun caso, includendo eventuale differenza tra comportamento atteso e osservato.

| ID | Procedura su installazione di prova | Risultato atteso | Esito | Evidenza anonimizzata |
| --- | --- | --- | --- | --- |
| G3-01 | Aprire Home e Storico senza premere START; attendere e riaprire l'app | Nessun nuovo tracking o sessione avviata implicitamente | NON ESEGUITO | — |
| G3-02 | Con GPS e posizione precisa autorizzati, premere START; osservare subito l'FGS; verificare cronometro e dati con rete disattivata | FGS/notifica coerenti, una sola sessione attiva, tracciamento offline e nessun crash | NON ESEGUITO | — |
| G3-03 | Da stato pulito ripetere START seguito immediatamente da STOP durante l'inizializzazione; ripetere con doppio STOP | Nessun Service o notifica residua, nessuna sessione duplicata, nessun doppio conteggio o tracking dopo STOP | NON ESEGUITO | — |
| G3-04 | Fare una sessione normale e STOP esplicito, poi riaprire Home e Storico | Una sola sessione completata, dati conservati, nessuna acquisizione fuori sessione | NON ESEGUITO | — |
| G3-05 | Durante sessione attiva portare l'app in background; provocare **process death** controllato sul dispositivo di prova e osservare la ripresa. Annotare il metodo utilizzato | Recupero coerente del medesimo stato/ID secondo il contratto, senza tempo di movimento inventato, doppio record o crash | NON ESEGUITO | — |
| G3-06 | Tenere una sessione attiva 3–5 minuti a schermo spento; sbloccare e usare STOP. Registrare impostazioni batteria originali | Notifica, durata, recupero e storico coerenti; nessun tracking dopo STOP | NON ESEGUITO | — |
| G3-07 | **Solo senza sessioni da salvare**, ripetere START con posizione negata o GPS spento su dispositivo sacrificabile | Rifiuto/degrado previsto senza sessioni fittizie, FGS residuo, crash o perdita di storico | NON ESEGUITO | — |
| G3-08 | Confrontare prima/dopo le sessioni sintetiche, punti, sprint e stati persistiti visibili; verificare il comportamento su Activity recreation e riapertura | Identità, ordine e dati storici invariati salvo la nuova sessione voluta; nessun conteggio duplicato | NON ESEGUITO | — |

**Process death non è force-stop.** `Impostazioni → Forza interruzione` impedisce il normale riavvio e non è una simulazione equivalente del processo terminato dal sistema. Se non è possibile riprodurre un process death controllato in sicurezza, marcare **G3-05 NON ESEGUITO** e descrivere il limite; non sostituirlo con un risultato dedotto dai fake o dalla CI.

**Permessi/batteria:** non cambiare concessioni o policy sul telefono dell'utente; non effettuare revoche durante sessioni da preservare. Annotare separatamente i casi dove Android o l'OEM impediscono la prova. Non usare un esito su un modello per dedurre esiti su dispositivi diversi.

## 4. Modello di report per la issue #24

Copiare il seguente blocco in un commento della issue **dopo** i test:

```text
P4a / #24 — Gate G3 fisico
Data del collaudo (fuso): 
Responsabile esecuzione / reviewer:
Dispositivo e API: 
APK: versione, versionCode, provenienza e commit:
Firma: compatibilità verificata? (sì/no/non verificata, senza asset o secret)
Backup di dati sintetici: verificato? (sì/no/non applicabile)
Permessi GPS e ottimizzazione batteria:
Rete/offline e note sul contesto:
G3-01: PASS | FAIL | NON ESEGUITO — osservazioni:
G3-02: PASS | FAIL | NON ESEGUITO — osservazioni:
G3-03: PASS | FAIL | NON ESEGUITO — osservazioni:
G3-04: PASS | FAIL | NON ESEGUITO — osservazioni:
G3-05: PASS | FAIL | NON ESEGUITO — metodo process death e osservazioni:
G3-06: PASS | FAIL | NON ESEGUITO — osservazioni:
G3-07: PASS | FAIL | NON ESEGUITO — osservazioni:
G3-08: PASS | FAIL | NON ESEGUITO — osservazioni:
Evidenze redatte / link a CI su SHA esatto:
Regressioni e issue correttive:
Rischio residuo / test non coperti:
Decisione maintainer: accettato | bloccato | ulteriori verifiche
```

Nessuna evidenza deve includere percorsi identificabili, coordinate, database personali, token, APK firmati non autorizzati o materiali di firma. I video/screenshots devono mostrare soltanto fixture sintetiche e dati redatti.

## 5. Regola di uscita verso P4b / #25

La #24 resta **aperta** se manca uno scenario richiesto, una regressione non è risolta, il risultato fisico non è documentato o la review umana non è stata registrata. In tal caso conservare `FAIL` / `NON ESEGUITO` e creare una issue correttiva mirata se necessario, senza avviare un nuovo refactoring del Service.

Per proporre la chiusura della #24 servono: esiti G3 pertinenti e verificabili, rispetto degli invarianti di tracking/storico/privacy, `verify` e `android11` sul commit applicabile, review del maintainer e decisione esplicita di accettazione. **Solo dopo la conferma del maintainer e la soddisfazione dei prerequisiti** si può avviare l'implementazione della #25, con test-first e PR separata. La semplice approvazione del protocollo non equivale a superamento di G3.

**Rollback di questa PR documentale:** revert del solo file e dell'eventuale link in `docs/testing.md`; nessun effetto sull'APK o sui dati.
