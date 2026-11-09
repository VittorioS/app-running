# Runbook maintainer — contributi via agenti AI

## Impostazioni GitHub (operazioni manuali, non modificabili dalla CI)
In Settings → Rules → Rulesets crea una regola per `main` (o Branch protection rules se preferito). Richiedi pull request, risoluzione delle conversazioni e status check `verify` **e `android11`** del workflow `Verify Pull Request`. Entrambi devono essere obbligatori per proteggere Android 11/API 30. In questa fase con un solo maintainer il ruleset ha zero review obbligatorie; chiunque abbia solo accesso di lettura può proporre PR, ma non effettuare il merge. Reintrodurre review approvate quando si aggiungono collaboratori con write access. Blocca force push e cancellazione, limita i bypass, non permettere il merge mentre i controlli sono in corso. Verifica i nomi dei check dopo la prima esecuzione.

Abilita GitHub Issues, forking pubblico e, se disponibile, segnalazione privata delle vulnerabilità. Invita i contributori a usare issue e template, senza concedere write access. CODEOWNERS richiede review obbligatoria soltanto se la regola GitHub richiede anche il code-owner review.

## Clean Architecture e standard di codice
Le PR devono dichiarare layer toccati, direzione delle dipendenze, eccezioni legacy e motivazioni delle astrazioni. Il job `verify` contiene `tests/check-architecture.py`, self-test del guardiano e `:app:spotlessCheck`, oltre a build, lint e JVM. Il guardiano controlla solo i **nuovi** package `domain`, `application`, `data`, `platform`, `presentation`: review necessaria per il package radice ancora legacy. Refactor a piccoli passi con test, iniziando da issue #6.

## Sicurezza Actions
- PR da fork: evento `pull_request`, permessi `contents: read`, nessun secret e nessuna pubblicazione.
- Firma debug e release: `.github/workflows/release-debug.yml` eseguito solo manualmente su `main`; proteggere **obbligatoriamente** l'environment `apk-release` con deploy da `main` soltanto e approvazione maintainer, e collocare lì la passphrase originale. Seguire `docs/release.md`. Non eseguire codice o artifact non verificati provenienti da fork nel contesto di pubblicazione.
- La pipeline debug usa la stessa chiave cifrata esistente, non genera firme, richiede versionCode crescente, verifica digest APK/certificato e pubblica metadata per ultimo. Per revoca, incidenti e rollback leggere `docs/release.md`. Non usare la chiave debug stabile in una PR esterna.
- Il vecchio bootstrap che estrae ZIP e fa `git push` dalla CI è sostituito dal workflow di sola verifica.

## Strategia PR
1. Issue verificabile → scope condiviso → implementazione con AGENTS.md → PR.
2. Un solo cambiamento funzionale coerente per PR, idealmente differenze revisionabili. Le dipendenze tra PR vanno dichiarate e ordinate.
3. Test automatici e manuali registrati, review di sicurezza su GPS/database/permissions/network/signing, poi merge umano.
4. Non trattare gli output degli agenti come approvati solo perché i test sono verdi.
5. La PR #2 è stata integrata in `main`; il vecchio workflow con privilegi di scrittura non è stato reintegrato. Le pubblicazioni successive usano il nuovo runbook e le regole di `main`.

## Backlog consigliato
- Portare progressivamente i test algoritmici in Gradle/JUnit eliminando lo script basato su elenco manuale senza perderne copertura.
- Estendere i test Android instrumentation per Activity, permessi, lifecycle, SQLite su device e schermate.
- Separare `TrackingService` in componenti testabili (input dei sensori, state reducer, repository), senza cambiare comportamento nel medesimo PR.
- Aggiungere test deterministici di playback GPS/sensori e benchmark di consumo su hardware reale.
- Mantenere l'environment e la pipeline release dedicata, con firma debug compatibile; progettare **separatamente** la firma production/stable.
