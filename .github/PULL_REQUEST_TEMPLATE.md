## Issue e obiettivo
Closes #

## Criteri di accettazione
- [ ] Tutti i criteri della issue sono soddisfatti (indicare eventuali eccezioni).

## Modifiche e confini
- Cosa è stato implementato:
- File/moduli coinvolti:
- Cosa **non** è stato modificato:

## Evidenze di verifica
- [ ] `sh tests/run-core-tests.sh` — esito:
- [ ] `python3 tests/check-db-migration.py` — esito:
- [ ] `sh ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — esito:
- [ ] Compatibilità Android 11/API 30 e job CI `android11` verificati:
- [ ] Test manuale su telefono (se necessario) — dispositivo/API e risultati:
- [ ] Screenshot/video UI senza informazioni personali (se necessario).

## Rischi e compatibilità
- [ ] Invarianti di `docs/invariants.md` rispettate.
- [ ] Nessuna perdita di sessioni o punti storici; migrazioni testate se necessario.
- [ ] Nessun nuovo permesso, endpoint, segreto o cambiamento di firma/release non discusso.
- Casi limite e rollback:
- Test **non eseguiti** e motivazione:

## Checklist
- [ ] Modifica limitata alla funzionalità approvata; test e documentazione aggiornati.
- [ ] La PR non esegue pubblicazioni, modifica secret o fa merge automaticamente.
- [ ] Nessuna modifica con dati GPS reali o informazioni sensibili nel diff.
