import it.example.passolibero.MovementClassifier;
import it.example.passolibero.MotionEvidencePolicy;
import it.example.passolibero.SessionMotionVoiceEvents;

public final class SessionMotionVoiceEventsTest {
    private static final MovementClassifier.Kind RUN = MovementClassifier.Kind.RUN;
    private static final MovementClassifier.Kind WALK = MovementClassifier.Kind.WALK;
    private static final MovementClassifier.Kind MOVING = MovementClassifier.Kind.MOVING_UNKNOWN;
    private static final MovementClassifier.Kind STILL = MovementClassifier.Kind.STILL;
    private static final MovementClassifier.Kind UNKNOWN = MovementClassifier.Kind.UNKNOWN;
    private static final MotionEvidencePolicy.Evidence E_MOVING =
            MotionEvidencePolicy.Evidence.MOVING;
    private static final MotionEvidencePolicy.Evidence E_STOP =
            MotionEvidencePolicy.Evidence.STILL_STRONG;
    private static final MotionEvidencePolicy.Evidence E_MODERATE =
            MotionEvidencePolicy.Evidence.STILL_MODERATE;
    private static final MotionEvidencePolicy.Evidence E_UNCERTAIN =
            MotionEvidencePolicy.Evidence.UNCERTAIN;

    private static void assertMessage(String actual, String expected, String label) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
    }

    public static void main(String[] args) {
        SessionMotionVoiceEvents speaker = new SessionMotionVoiceEvents();
        assertMessage(speaker.observe(UNKNOWN, E_UNCERTAIN, true, false), null,
                "Nessun annuncio all'avvio");
        assertMessage(speaker.observe(STILL, E_STOP, true, false), null,
                "Niente falsa pausa prima del primo movimento");
        assertMessage(speaker.observe(MOVING, E_MOVING, true, false), null,
                "Movimento iniziale non e' una ripresa");
        if (!speaker.hasSeenMovement()) throw new AssertionError("Pre-riscalda la voce");
        assertMessage(speaker.observe(RUN, E_MOVING, true, false), null,
                "Corsa in corso non ripete annunci");
        assertMessage(speaker.observe(STILL, E_UNCERTAIN, true, false), null,
                "STILL vecchio senza prova attuale non annuncia");
        assertMessage(speaker.observe(STILL, E_STOP, true, false),
                SessionMotionVoiceEvents.PAUSE, "Prima pausa confermata");
        assertMessage(speaker.observe(STILL, E_MODERATE, true, false), null,
                "Una pausa, un annuncio");
        assertMessage(speaker.observe(UNKNOWN, E_UNCERTAIN, true, false), null,
                "Perdita GPS non e' ripresa");
        assertMessage(speaker.observe(WALK, E_MOVING, true, false),
                SessionMotionVoiceEvents.RESUME, "Camminata riprende");
        assertMessage(speaker.observe(RUN, E_MOVING, true, false), null,
                "Passaggio cammino-corsa non e' nuova ripresa");
        assertMessage(speaker.observe(STILL, E_STOP, true, false),
                SessionMotionVoiceEvents.PAUSE, "Seconda pausa");
        assertMessage(speaker.observe(MOVING, E_MOVING, true, false),
                SessionMotionVoiceEvents.RESUME, "Ripresa movimento non classificato");

        speaker.reset();
        assertMessage(speaker.observe(RUN, E_MOVING, false, false), null,
                "Avvia TTS durante il primo movimento");
        assertMessage(speaker.observe(STILL, E_STOP, false, false), null,
                "Coda pausa mentre TTS si prepara");
        assertMessage(speaker.observe(STILL, E_STOP, true, false),
                SessionMotionVoiceEvents.PAUSE, "Annuncia quando TTS e' pronto");
        assertMessage(speaker.observe(RUN, E_MOVING, false, false), null,
                "Coda ripresa quando voce non pronta");
        assertMessage(speaker.observe(RUN, E_MOVING, true, false),
                SessionMotionVoiceEvents.RESUME, "Ripresa pronunciata una volta");

        speaker.reset();
        speaker.observe(WALK, E_MOVING, false, false);
        speaker.observe(STILL, E_STOP, false, false);
        assertMessage(speaker.observe(RUN, E_MOVING, true, false), null,
                "Nessuna ripresa se la pausa non era stata detta");

        speaker.reset();
        speaker.observe(RUN, E_MOVING, true, false);
        assertMessage(speaker.observe(STILL, E_STOP, true, true), null,
                "Non interrompere countdown sprint per pausa");
        assertMessage(speaker.observe(RUN, E_MOVING, true, false), null,
                "Nessuna ripresa dopo pausa silenziata");
        assertMessage(speaker.observe(STILL, E_STOP, true, false),
                SessionMotionVoiceEvents.PAUSE, "Pausa normale dopo sprint");
        assertMessage(speaker.observe(WALK, E_MOVING, true, true), null,
                "Non interrompere sprint con ripresa");
        assertMessage(speaker.observe(RUN, E_MOVING, true, false), null,
                "Non ripetere ripresa scartata durante sprint");

        speaker.reset();
        assertMessage(speaker.observe(STILL, E_STOP, true, false), null,
                "Reset evita annuncio artificiale dopo riavvio servizio");
        System.out.println("PASS: annunci pausa/ripresa da stati confermati, TTS, sprint, GPS e riavvio.");
    }
}
