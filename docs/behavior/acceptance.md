# Scenari di accettazione essenziali (Gherkin)

Gli scenari qui sotto rappresentano il **comportamento atteso dal codice esistente** e servono da checklist di caratterizzazione; non attestano l'esecuzione automatica dei test. Le condizioni su sensori/rete devono essere riprodotte con fixture o controllate manualmente su dispositivo. Gli scenari deliberatamente omettono dettagli di UI non critici: le decisioni complete sono nelle tabelle delle singole feature.

## 1. Avvio con GPS inaffidabile

```gherkin
Feature: Avvio sessione
  Scenario: Il preflight GPS fallisce e l'utente annulla
    Given nessuna sessione è attiva
    And la posizione precisa è autorizzata e il GPS è abilitato
    And per 12 secondi non arriva un fix GPS attendibile
    When l'utente sceglie "Annulla"
    Then non viene richiesta una nuova sessione di tracciamento
    And compare ancora il percorso per avviare una nuova sessione
```

## 2. Ripresa dopo intervallo non osservato

```gherkin
Feature: Recupero sessione
  Scenario: Un servizio riparte con sessione già attiva
    Given una sessione salvata è marcata attiva con id positivo
    And il servizio è stato interrotto senza STOP esplicito
    When il servizio viene inizializzato nuovamente
    Then ripristina la stessa sessione
    And lo stato di movimento iniziale è UNKNOWN
    And il tempo non osservato non è attribuito retroattivamente a RUN, WALK o STILL
    And il percorso non unisce artificialmente i punti prima e dopo il riavvio
```

## 3. Conservazione del movimento non classificato

```gherkin
Feature: Contabilità del movimento
  Scenario: Esiste movimento certo ma non una categoria affidabile
    Given una sessione in corso e prove recenti che confermano MOVING
    And non è disponibile una classificazione RUN o WALK attendibile
    When la timeline conferma l'intervallo
    Then il movimento visibile è MOVING_UNKNOWN
    And il tempo è incluso in movingUnknownMs e movingMs
    And nessun millisecondo di quell'intervallo viene inventato come RUN o WALK
```

## 4. Sprint con completamento a tempo

```gherkin
Feature: Sprint
  Scenario: Uno sprint a tempo termina e viene registrato
    Given una sessione di allenamento attiva
    And la voce italiana è disponibile
    And uno sprint TIME con countdown di 5 secondi e target di 10 secondi
    When l'utente avvia la prova
    And il countdown raggiunge la scadenza monotona
    And trascorrono i 10 secondi di prova
    Then la fase diventa FINISHED con esito TEMPO
    And il risultato è registrato una sola volta nello storico sprint
```

## 5. Cancellazione sicura dello storico

```gherkin
Feature: Storico
  Scenario: È impossibile eliminare la sessione attiva
    Given una sessione salvata con id X
    And active è true e active_id è X
    When l'utente apre lo storico
    Then il pulsante Elimina della sessione X è disabilitato
    And la sessione, i punti GPS e gli sprint restano nel database
```

## 6. Blocco dell'aggiornamento non sicuro

```gherkin
Feature: Aggiornamento APK
  Scenario: La firma dell'APK scaricato non coincide
    Given nessuna sessione attiva
    And un download APK completato da un canale riconosciuto
    And la verifica rileva un firmatario diverso dall'APK installato
    When l'utente richiede verifica e installazione
    Then il pacchetto viene rifiutato
    And non viene aperto l'installer Android
```

## Criterio di manutenzione

Quando un comportamento cambia, aggiornare prima o insieme alla PR **guardie, tabella, diagramma e scenario** che lo descrivono. Se una feature non richiede un nuovo percorso critico, evitare di moltiplicare scenari Gherkin ridondanti. Per i test reali consultare [testing](../testing.md) e [invarianti](../invariants.md).
