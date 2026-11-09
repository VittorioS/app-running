import it.example.passolibero.SprintTrial;

public final class SprintTrialTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static boolean fix(SprintTrial s, long now, long fix, double speed) {
        return s.onFix(now, fix, speed, .3, 4);
    }
    public static void main(String[] args) {
        SprintTrial s = new SprintTrial();
        s.start(100_000, 5, SprintTrial.Mode.TIME, 10);
        check(s.phase() == SprintTrial.Phase.COUNTDOWN, "Countdown iniziato");
        check(!fix(s, 102_000, 102_000, 8), "Non registra la partenza anticipata");
        s.advance(104_999);
        check(s.phase() == SprintTrial.Phase.COUNTDOWN, "Countdown non ancora finito");
        s.advance(105_000);
        check(s.phase() == SprintTrial.Phase.ACTIVE, "Partenza effettiva");
        check(fix(s, 105_000, 105_000, 5), "Prima velocita' valida");
        check(fix(s, 105_200, 105_200, 6), "Campionamento 200 ms");
        check(s.samples() == 2, "Numero campioni");
        check(Math.abs(s.distanceM() - 1.1) < .001, "Integrazione GNSS trapezoidale");
        check(Math.abs(s.peakSpeedKmh() - 21.6) < .001, "Velocita' massima GNSS");
        check(!s.onFix(106_000, 105_400, 8, 2.0, 4), "Velocita' imprecisa scartata");
        check(!s.onFix(106_000, 105_400, 8, .3, 17), "Posizione imprecisa scartata");
        check(!s.onFix(106_000, 105_400, Double.NaN, .3, 4), "Velocita' assente scartata");
        check(!s.onFix(106_000, 105_400, 12, .3, 4), "Velocita' non plausibile");
        check(!fix(s, 106_000, 105_200, 7), "Duplicati scartati");
        check(!fix(s, 109_000, 106_000, 7), "Fix in ritardo scartato");
        check(fix(s, 110_000, 110_000, 7), "Nuova misura dopo interruzione GPS");
        check(s.distanceM() < 2, "Non accumula distanza nel buco GPS");
        s.advance(115_000);
        check(s.phase() == SprintTrial.Phase.FINISHED, "Durata imposta rispettata");
        check(s.elapsedMs(120_000) == 10_000, "Durata esatta");
        check(!fix(s, 116_000, 114_900, 10), "Nessun campione dopo fine");
        check(Double.isFinite(s.averageSpeedKmh(120_000)), "Media calcolata");
        check(Math.abs(SprintTrial.averageSpeedKmh(55.0, 11_000, 12) - 18.0) < .00001,
                "55 metri in 11 secondi = 18 km/h, non 18000");
        check(Math.abs(SprintTrial.averageSpeedKmh(51.6, 11_000, 12) - 16.8872727) < .001,
                "Regressione screenshot: 16882 km/h diventano circa 16.9");
        check(Double.isNaN(SprintTrial.averageSpeedKmh(55.0, 11_000, 1)),
                "GNSS insufficiente: media non disponibile");
        check(Double.isNaN(SprintTrial.averageSpeedKmh(55.0, 0, 12)),
                "Durata nulla: niente divisione per zero");
        check(Double.isNaN(SprintTrial.averageSpeedKmh(Double.NaN, 11_000, 12)),
                "Distanza non valida");
        // Una nuova prova sulla stessa sessione riparte da zero senza sovrascrivere la precedente.
        double firstPeak = s.peakSpeedKmh();
        s.start(200_000, 10, SprintTrial.Mode.DISTANCE, 10);
        check(s.samples() == 0 && s.distanceM() == 0 && Double.isNaN(s.peakSpeedKmh()),
                "Secondo sprint indipendente dal precedente");
        check(firstPeak > 0, "Risultato precedente completato");
        s.advance(210_000);
        fix(s, 210_000, 210_000, 8);
        fix(s, 211_000, 211_000, 8);
        check(s.phase() == SprintTrial.Phase.ACTIVE, "Distanza non ancora raggiunta");
        fix(s, 212_000, 212_000, 8);
        check(s.phase() == SprintTrial.Phase.FINISHED, "Distanza raggiunta");
        check(s.elapsedMs(250_000) == 2000, "Durata sprint per metri");
        s.start(300_000, 5, SprintTrial.Mode.DISTANCE, 500);
        s.advance(425_000);
        check(s.phase() == SprintTrial.Phase.FINISHED
                && "LIMITE_TEMPO".equals(s.finishReason()), "Sicurezza massimo 120 s");
        s.start(500_000, 5, SprintTrial.Mode.TIME, 15);
        s.cancel(502_000);
        check(s.phase() == SprintTrial.Phase.CANCELLED, "Annullamento countdown");
        boolean rejected = false;
        try { s.start(1, 4, SprintTrial.Mode.TIME, 10); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Countdown minimo 5 secondi");
        rejected = false;
        try { s.start(1, 8, SprintTrial.Mode.DISTANCE, 600); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Distanza massima 500 metri");
        System.out.println("PASS: countdown sprint, GPS 200 ms, velocita', distanze e filtri.");
    }
}
