# Contribuire a PassoLibero con il proprio coding agent

La repository è pubblica: apri una issue (o scegline una esistente), crea un fork, implementa su un ramo e apri una PR verso `main`. Non serve accesso in scrittura al repository originale.

1. Leggi `AGENTS.md`, `docs/architecture.md`, `docs/invariants.md` e `docs/testing.md`.
2. Crea una issue con comportamento atteso, criteri di accettazione misurabili, casi limite, limiti di scope e piano di test. Per le funzionalità grandi concorda prima una suddivisione.
3. Clona il fork, crea `feature/<nome-issue>` a partire da `main` aggiornato e usa il tuo agent di coding. Non usare branch di rilascio o signing per testare il contributo.
4. Implementa l'intera funzionalità per quanto possibile (comportamento, eventuale persistenza e UI, test di regressione, documentazione) senza introdurre modifiche non collegate.
5. Esegui le verifiche in `docs/testing.md`. Se non hai Android SDK o un dispositivo, dichiaralo: la CI può verificare la build, ma non sostituisce prove hardware.
6. Apri la PR non draft solo quando è revisionabile; compila il template e attendi i controlli automatici e la review del maintainer.

## Convenzioni
- Java 17, API Android compatibili con minSdk 30; evita migrazioni a nuovi framework senza issue concordata.
- Metri internamente, chilometri nelle schermate, ritmo in minuti/km; tempo monotono per classificazione/durata e wall clock solo per registrazioni esterne.
- Mantieni algoritmi Android-free quando possibile; i test non devono dipendere da GPS reale o rete.
- Nessuna chiave, log di coordinate reali, dati utente o credenziali negli allegati delle PR.
- Modifiche a schema, permessi, rete, update e signing richiedono una spiegazione particolare e review dedicata.

## Criteri di merge
Build e test CI verdi, criteri di accettazione soddisfatti, controllo umano delle parti sensibili, review approvata, discussioni risolte. La CI non approva in autonomia.

## Prompt per un agente
"Implementa la issue #N. Leggi AGENTS.md e docs/*.md prima di modificare il codice. Aggiungi test, esegui i controlli disponibili e apri una pull request descrivendo ciò che hai verificato e ciò che richiede un telefono reale. Non pubblicare né fare merge automaticamente."
