package it.example.passolibero;

/**
 * Annunci solo dagli stati gia' confermati dal MotionTimeline esistente.
 * Non cambia soglie, tempi, classificazione o contabilizzazione della sessione.
 * L'evidenza attuale deve essere coerente con lo stato visibile; i periodi
 * incerti/GPS assente non sono ne' pause ne' riprese.
 */
public final class SessionMotionVoiceEvents {
    public static final String PAUSE = "Sessione in pausa";
    public static final String RESUME = "Sessione ripresa";

    private boolean seenMovement;
    private boolean paused;
    private boolean announcedPause;
    private String pending;

    public boolean hasSeenMovement() {
        return seenMovement;
    }

    public void reset() {
        seenMovement = false;
        paused = false;
        announcedPause = false;
        pending = null;
    }

    /**
     * @param visible stato confermato del MotionTimeline (non il candidato GPS)
     * @param evidence evidenza corrente valutata dal rilevatore esistente
     * @param voiceReady TTS italiano pronto a pronunciare l'annuncio
     * @param sprintPriority evita che un avviso interrompa il countdown o lo sprint
     * @return annuncio da pronunciare una sola volta, oppure null
     */
    public String observe(MovementClassifier.Kind visible,
                          MotionEvidencePolicy.Evidence evidence,
                          boolean voiceReady, boolean sprintPriority) {
        boolean reliableMovement = evidence == MotionEvidencePolicy.Evidence.MOVING
                && (visible == MovementClassifier.Kind.RUN
                    || visible == MovementClassifier.Kind.WALK
                    || visible == MovementClassifier.Kind.MOVING_UNKNOWN);
        boolean reliableStillness = visible == MovementClassifier.Kind.STILL
                && (evidence == MotionEvidencePolicy.Evidence.STILL_STRONG
                    || evidence == MotionEvidencePolicy.Evidence.STILL_MODERATE);

        if (reliableMovement) {
            seenMovement = true;
            if (paused) {
                paused = false;
                // Mai annunciare "ripresa" se "pausa" non e' stata pronunciata.
                pending = announcedPause ? RESUME : null;
                announcedPause = false;
            }
        } else if (reliableStillness && seenMovement && !paused) {
            paused = true;
            announcedPause = false;
            pending = PAUSE;
        }

        if (sprintPriority) {
            // La prova sprint ha priorita' assoluta nella sintesi vocale.
            // Non riproporre piu' tardi un annuncio ormai vecchio.
            pending = null;
            announcedPause = false;
            return null;
        }

        if (!voiceReady || pending == null
                || (PAUSE.equals(pending) && !reliableStillness)
                || (RESUME.equals(pending) && !reliableMovement)) return null;

        String result = pending;
        pending = null;
        if (PAUSE.equals(result)) announcedPause = true;
        return result;
    }
}
