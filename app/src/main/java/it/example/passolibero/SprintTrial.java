package it.example.passolibero;

/**
 * Sprint volontario, indipendente dall'algoritmo RUN/WALK.
 * Tempi misurati con clock monotono; velocita' dalla misura GNSS del telefono,
 * mai da differenze di coordinate a intervalli molto brevi e rumorosi.
 */
public final class SprintTrial {
    public enum Phase { IDLE, COUNTDOWN, ACTIVE, FINISHED, CANCELLED }
    public enum Mode { TIME, DISTANCE }

    private static final long MAX_DISTANCE_DURATION_MS = 120_000L;
    private static final long MAX_FIX_LAG_MS = 1_800L;
    private static final long MAX_INTEGRATION_GAP_MS = 2_500L;
    private static final double MAX_POSITION_ACCURACY_M = 12.0;
    private static final double MAX_SPEED_ACCURACY_MPS = 1.5;
    private static final double MAX_HUMAN_SPEED_MPS = 40.0 / 3.6;

    private Phase phase = Phase.IDLE;
    private Mode mode = Mode.TIME;
    private int countdownSeconds, target;
    private long countdownEndMs, startedMs, finishedMs, lastFixMs = -1;
    private double lastSpeedMps = Double.NaN, meters, maxKmh = Double.NaN;
    private int validSamples;
    private String finishReason = "";

    public void start(long now, int countdown, Mode goalMode, int value) {
        if (phase == Phase.COUNTDOWN || phase == Phase.ACTIVE)
            throw new IllegalStateException("Sprint gia' in corso");
        if (now < 0 || countdown < 5 || countdown > 10 || goalMode == null
                || (goalMode == Mode.TIME && (value < 5 || value > 120))
                || (goalMode == Mode.DISTANCE && (value < 10 || value > 500)))
            throw new IllegalArgumentException("Impostazioni sprint non valide");
        phase = Phase.COUNTDOWN;
        mode = goalMode;
        countdownSeconds = countdown;
        target = value;
        countdownEndMs = now + countdown * 1000L;
        startedMs = 0;
        finishedMs = 0;
        lastFixMs = -1;
        lastSpeedMps = Double.NaN;
        meters = 0;
        maxKmh = Double.NaN;
        validSamples = 0;
        finishReason = "";
    }

    public void advance(long now) {
        if (phase == Phase.COUNTDOWN && now >= countdownEndMs) {
            phase = Phase.ACTIVE;
            startedMs = countdownEndMs;
        }
        if (phase == Phase.ACTIVE) {
            if (mode == Mode.TIME && now >= startedMs + target * 1000L) {
                finish(startedMs + target * 1000L, "TEMPO");
            } else if (mode == Mode.DISTANCE
                    && now >= startedMs + MAX_DISTANCE_DURATION_MS) {
                finish(startedMs + MAX_DISTANCE_DURATION_MS, "LIMITE_TEMPO");
            }
        }
    }

    /**
     * Valuta i soli fix della prova, con velocita' GNSS nativa e incertezza
     * comunicata dal dispositivo. Nessuna distanza inventata attraverso
     * lunghi buchi di ricezione o campioni fuori finestra.
     */
    public boolean onFix(long now, long fixElapsedMs, double speedMps,
                         double speedAccuracyMps, double positionAccuracyM) {
        advance(now);
        if (phase != Phase.ACTIVE || fixElapsedMs < startedMs
                || fixElapsedMs > now || now - fixElapsedMs > MAX_FIX_LAG_MS
                || !Double.isFinite(speedMps) || speedMps < 0
                || speedMps > MAX_HUMAN_SPEED_MPS
                || !Double.isFinite(speedAccuracyMps) || speedAccuracyMps < 0
                || speedAccuracyMps > MAX_SPEED_ACCURACY_MPS
                || !Double.isFinite(positionAccuracyM) || positionAccuracyM < 0
                || positionAccuracyM > MAX_POSITION_ACCURACY_M
                || (lastFixMs >= 0 && fixElapsedMs <= lastFixMs)) return false;

        // Con tempo imposto non usare neppure un campione acquisito dopo la scadenza.
        if (mode == Mode.TIME && fixElapsedMs > startedMs + target * 1000L) return false;
        if (mode == Mode.DISTANCE
                && fixElapsedMs > startedMs + MAX_DISTANCE_DURATION_MS) return false;

        if (lastFixMs >= 0) {
            long dt = fixElapsedMs - lastFixMs;
            if (dt <= MAX_INTEGRATION_GAP_MS) {
                meters += (lastSpeedMps + speedMps) * 0.5 * (dt / 1000.0);
            }
        }
        lastFixMs = fixElapsedMs;
        lastSpeedMps = speedMps;
        maxKmh = Double.isFinite(maxKmh) ? Math.max(maxKmh, speedMps * 3.6)
                : speedMps * 3.6;
        validSamples++;
        if (mode == Mode.DISTANCE && meters >= target) finish(fixElapsedMs, "DISTANZA");
        return true;
    }

    public void cancel(long now) {
        if (!busy()) return;
        if (phase == Phase.ACTIVE) finishedMs = Math.max(0, now - startedMs);
        phase = Phase.CANCELLED;
        finishReason = "ANNULLATO";
    }

    private void finish(long endMs, String reason) {
        finishedMs = Math.max(0, endMs - startedMs);
        phase = Phase.FINISHED;
        finishReason = reason;
    }

    public boolean busy() { return phase == Phase.COUNTDOWN || phase == Phase.ACTIVE; }
    public Phase phase() { return phase; }
    public Mode mode() { return mode; }
    public int countdownSeconds() { return countdownSeconds; }
    public int target() { return target; }
    public long countdownEndMs() { return countdownEndMs; }
    public long startedMs() { return startedMs; }
    public long elapsedMs(long now) {
        if (phase == Phase.ACTIVE) return Math.max(0, now - startedMs);
        return finishedMs;
    }
    public double distanceM() { return meters; }
    public double peakSpeedKmh() { return maxKmh; }
    public int samples() { return validSamples; }
    public String finishReason() { return finishReason; }
    public double averageSpeedKmh(long now) {
        long elapsed = elapsedMs(now);
        return validSamples < 2 || elapsed <= 0 ? Double.NaN
                : meters * 3_600_000.0 / elapsed;
    }
}
