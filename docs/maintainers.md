# Runbook maintainer — contributi via agenti AI

## Impostazioni GitHub (operazioni manuali, non modificabili dalla CI)
In Settings → Rules → Rulesets crea una regola per `main` (o Branch protection rules se preferito). Richiedi pull request, almeno una review approvata indipendente, discussioni risolte e status check `verify` **e `android11`** del workflow `Verify Pull Request`. Entrambi devono essere obbligatori per proteggere Android 11/API 30. Blocca force push e cancellazione, limita i bypass, non permettere il merge mentre i controlli sono in corso. Verifica i nomi dei check dopo la prima esecuzione.

Abilita GitHub Issues, forking pubblico e, se disponibile, segnalazione privata delle vulnerabilità. Invita i contributori a usare issue e template, senza concedere write access. CODEOWNERS richiede review obbligatoria soltanto se la regola GitHub richiede anche il code-owner review.

## Sicurezza Actions
- PR da fork: evento `pull_request`, permessi `contents: read`, nessun secret e nessuna pubblicazione.
- Firma APK, release, canale di aggiornamento e gestione keystore: workflow separato **solo da main protetto o workflow_dispatch affidabile**, con `environment` approvato e permessi minimi. Non eseguire codice o artifact non verificati provenienti da fork nel contesto di pubblicazione.
- Prima di introdurre la release pipeline, progettare rotazione/revoca della chiave, controllo dell'APK e versionCode; non usare la chiave debug stabile in una PR esterna.
- Il vecchio bootstrap che estrae ZIP e fa `git push` dalla CI è sostituito dal workflow di sola verifica.

## Strategia PR
1. Issue verificabile → scope condiviso → implementazione con AGENTS.md → PR.
2. Un solo cambiamento funzionale coerente per PR, idealmente differenze revisionabili. Le dipendenze tra PR vanno dichiarate e ordinate.
3. Test automatici e manuali registrati, review di sicurezza su GPS/database/permissions/network/signing, poi merge umano.
4. Non trattare gli output degli agenti come approvati solo perché i test sono verdi.
5. La PR #2 esistente è una draft grande e include più feature e signing. Riesaminarla, suddividere quanto possibile e aggiornare la sua CI **dopo** aver integrato la base AI-contributor-readiness. Non farla bypassare la protezione appena creata.

## Backlog consigliato
- Portare progressivamente i test algoritmici in Gradle/JUnit eliminando lo script basato su elenco manuale senza perderne copertura.
- Estendere i test Android instrumentation per Activity, permessi, lifecycle, SQLite su device e schermate.
- Separare `TrackingService` in componenti testabili (input dei sensori, state reducer, repository), senza cambiare comportamento nel medesimo PR.
- Aggiungere test deterministici di playback GPS/sensori e benchmark di consumo su hardware reale.
- Progettare la release pipeline separata con approvazione environment e firma stabile.
