# Release debug firmate — runbook maintainer

## Obiettivo e limiti di sicurezza

La pipeline di verifica PR `.github/workflows/ci.yml` opera **solo in lettura**, su `pull_request` e `push main`; non riceve segreti di firma e non pubblica release.

`.github/workflows/release-debug.yml` è distinta: si avvia **solo manualmente** (GitHub Actions → Publish trusted debug APK → Run workflow), selezionando `main`. I due job verificano esplicitamente `github.repository == 'VittorioS/app-running'` e `github.ref == 'refs/heads/main'`; non è consentito eseguire codice di PR da fork nel contesto di firma/pubblicazione.

L'unico job con `contents: write` è `release-signed-debug`, legato all'ambiente GitHub `apk-release`. Gli altri job hanno `contents: read`. La pipeline **non fa commit, non fa push e non genera nuove chiavi**.

## Configurazione obbligatoria una tantum (amministratore)

**Prima di eseguire anche un dry run firmato**, apri [Settings → Environments](https://github.com/VittorioS/app-running/settings/environments):

1. Crea un ambiente denominato esattamente `apk-release`.
2. Imposta *Deployment branches and tags* su **Selected branches** e autorizza **solo `main`**.
3. Imposta *Required reviewers* sull'account maintainer che approva le pubblicazioni. Se sei l'unico maintainer, **non** attivare l'opzione che impedisce l'auto-approvazione dei deployment: altrimenti nessuno potrà sbloccare il job. Se GitHub/piano non consente i reviewer per l'ambiente, **non considerare il rilascio automaticamente protetto**: limita le autorizzazioni amministrative e abilita un controllo equivalente prima di pubblicare.
4. Dentro `apk-release` aggiungi come **environment secret**, non come variabile e non nel codice, `PASSOLIBERO_SIGNING_PASSPHRASE`: **la passphrase originale** usata per cifrare `.github/signing/debug-keystore.p12.enc`. Non sostituirla con un'altra, non inserirla nella PR, non mostrarla nei log.
5. Conserva in un password manager sicuro la passphrase e una copia recuperabile (cifrata) del keystore. I soli file cifrati nel repository **non** bastano a recuperare la firma se si perde la passphrase.

Attenzione: il riferimento `environment: apk-release` nel YAML **non configura automaticamente le protezioni**. GitHub può creare un environment senza reviewer se non esiste. Serve questa configurazione manuale.

## Eseguire una prova pre-produzione (senza pubblicare)

1. Verifica che `main` abbia status check `verify` e `android11` verdi e il ruleset `protect-main` attivo.
2. Apri [la release debug esistente](https://github.com/VittorioS/app-running/releases/tag/debug-pr-2) e controlla `update.json`, il suo `versionCode` e il certificato. Il valore visto durante la preparazione di questo workflow era circa **10091**, ma va verificato sempre sul JSON corrente.
3. In [Actions](https://github.com/VittorioS/app-running/actions/workflows/release-debug.yml), scegli `Run workflow` → ramo `main`, un `version_code` strettamente superiore a quello del JSON, `publish = false`.
4. Approva il deployment nell'environment `apk-release`, dopo aver verificato il commit e l'operazione richiesta. Il job confronta `versionCode` e firma con la release esistente, decifra la **stessa** chiave, compila l'APK con quel `versionCode`, verifica `aapt`, `apksigner`, app ID, minSdk Android 11, digest e metadata.
5. Il risultato è un artifact Actions contenente APK firmato e `update.json`. Non modifica la GitHub Release, il tag né `main`. Verifica che i report non contengano segreti.

Se l'ambiente o la passphrase non sono configurati, la build deve **fallire chiusa**, senza ricreare alcuna chiave.

## Pubblicare sul canale compatibile con la V2

Dopo dry run riuscito, esegui nuovamente `Run workflow` su `main` con `version_code` appropriato e `publish = true`; richiede una **nuova approvazione** del deployment. La pipeline carica l'APK con nome immutabile `passolibero-debug-v<VERSION_CODE>.apk` nella pre-release `debug-pr-2`, poi aggiorna `update.json` **per ultimo**. Se l'APK della stessa versione esiste già, il retry è ammesso solo se i byte coincidono.

Il client Android V2 legge già il canale `debug-pr-2` e verifica firma, package, digest e `versionCode`. Per non perdere lo storico, verifica che la firma dell'APK installato sia quella originale. **Non** disinstallare l'app per testare un aggiornamento senza avere prima esportato i dati.

Il canale `stable` **non è abilitato** da questa pipeline debug. Non usare la chiave debug come firma di una release destinata allo store. Una release stabile richiede gestione separata della chiave di produzione, approvazione, canale e verifica di migrazione.

## Errori, interruzioni, rollback

- **Passphrase assente o errata, fingerprint diverso:** blocca la pubblicazione; ripristina la passphrase/chiave originale. Non generarne un'altra.
- **Versione nuova non crescente:** scegli un codice maggiore del `versionCode` effettivamente pubblicato. Non basarti sul numero dell'ultimo workflow.
- **Errore durante upload APK, prima di `update.json`:** l'app continuerà a vedere il precedente `update.json`; riprova la stessa versione soltanto se il file coincide.
- **Errore dopo `update.json`:** confronta gli asset pubblicati con la build firmata e controlla la disponibilità dei download. Non modificare a mano metadati per saltare le verifiche.
- **Difetto funzionale dopo pubblicazione:** il client rifiuta i downgrade. Per ritornare a una funzionalità precedente, ricompilala da un commit affidabile con un `versionCode` **maggiore**, stessa firma e test completi, e pubblica come nuova versione. Non sostituire silenziosamente un APK già pubblicato con altri byte.
- **Chiave compromessa:** sospendi le pubblicazioni, avvisa gli utenti e prepara una migrazione firmata/rotazione esplicita. La sostituzione del certificato rompe la compatibilità di aggiornamento ordinaria.

## Non fare

- Non usare `pull_request_target` per eseguire codice di PR non fidate con token di pubblicazione.
- Non dare `contents: write` alla CI delle PR, non esporre passphrase a fork e non scaricare artifact PR per firmarli.
- Non ripristinare il vecchio `bootstrap.yml` che estraeva ZIP e poteva fare `git push`.
- Non abilitare l'ambiente senza protezione come se `environment:` da solo imponesse un'approvazione.
