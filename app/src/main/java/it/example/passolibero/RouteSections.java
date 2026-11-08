package it.example.passolibero;

import java.util.ArrayList;
import java.util.List;

/**
 * Segmentazione offline/incrementale dei punti GPS gia' classificati.
 * Non modifica il riconoscimento di andatura ne' la contabilita' della sessione.
 */
public final class RouteSections {
    public enum Kind { WALK, RUN, SPRINT, OTHER }

    private RouteSections() { }

    public static Kind kind(String movement) {
        if ("WALK".equals(movement)) return Kind.WALK;
        if ("RUN".equals(movement)) return Kind.RUN;
        if ("SPRINT".equals(movement)
                || (movement != null && movement.startsWith("SPRINT:")))
            return Kind.SPRINT;
        return Kind.OTHER;
    }

    public static String label(Kind kind) {
        switch (kind) {
            case WALK: return "Camminata";
            case RUN: return "Corsa";
            case SPRINT: return "Sprint";
            default: return "Fermo / non classificato";
        }
    }

    /** Finestra stimata per dati sprint registrati prima delle etichette GPS dedicate. */
    public static final class SprintWindow {
        private final long from, until;

        public SprintWindow(long endWallMs, long elapsedMs) {
            if (endWallMs <= 0 || elapsedMs <= 0 || elapsedMs > 180_000
                    || endWallMs < elapsedMs)
                throw new IllegalArgumentException("Prova sprint storica non valida");
            // Tolleranza per il ritardo fra fine prova e scrittura in DB.
            this.from = endWallMs - elapsedMs - 1_000L;
            this.until = endWallMs + 500L;
        }

        public boolean includes(long wallMs) {
            return wallMs >= from && wallMs <= until;
        }

        public long fromWallMs() { return from; }
        public long untilWallMs() { return until; }
    }

    public static final class Fix {
        public final long id, time;
        public final double lat, lon, deltaM, speedKmh, accuracyM;
        public final String movement;
        public final boolean estimatedSprint;

        public Fix(long id, long time, double lat, double lon,
                   double deltaM, double speedKmh, double accuracyM, String movement) {
            this(id, time, lat, lon, deltaM, speedKmh, accuracyM, movement, false);
        }

        public Fix(long id, long time, double lat, double lon,
                   double deltaM, double speedKmh, double accuracyM,
                   String movement, boolean estimatedSprint) {
            this.id = id;
            this.time = time;
            this.lat = lat;
            this.lon = lon;
            this.deltaM = deltaM;
            this.speedKmh = speedKmh;
            this.accuracyM = accuracyM;
            this.movement = movement;
            this.estimatedSprint = estimatedSprint;
        }
    }

    public static final class Section {
        public final int number;
        public final Kind kind;
        public final List<Fix> geometry = new ArrayList<>();
        public long startedAt, endedAt;
        public double distanceM;
        public double maxKmh = Double.NaN;
        public int samples;
        public boolean approximateSprint;

        private Section(int number, Kind kind, long startedAt) {
            this.number = number;
            this.kind = kind;
            this.startedAt = startedAt;
            this.endedAt = startedAt;
        }

        public long durationMs() { return Math.max(0, endedAt - startedAt); }

        /** Distanza in metri e durata in ms: km/h = metri * 3600 / ms. */
        public double averageKmh() {
            if (distanceM <= 0 || durationMs() <= 0) return Double.NaN;
            return distanceM * 3_600.0 / durationMs();
        }

        private void add(Fix current, boolean connected) {
            geometry.add(current);
            endedAt = current.time;
            samples++;
            if (current.estimatedSprint) approximateSprint = true;
            if (connected && Double.isFinite(current.deltaM) && current.deltaM > 0
                    && current.deltaM < 100) {
                distanceM += current.deltaM;
            }
            if (Double.isFinite(current.speedKmh) && current.speedKmh > 0
                    && current.speedKmh <= 40
                    && Double.isFinite(current.accuracyM)
                    && current.accuracyM >= 0 && current.accuracyM <= 25) {
                maxKmh = Double.isFinite(maxKmh)
                        ? Math.max(maxKmh, current.speedKmh) : current.speedKmh;
            }
        }
    }

    public static final class Builder {
        private final List<Section> sections = new ArrayList<>();
        private Fix previous;
        private Section current;
        private long lastId = -1;

        public List<Section> sections() { return sections; }

        public void reset() {
            sections.clear();
            previous = null;
            current = null;
            lastId = -1;
        }

        /**
         * Aggiunge un solo campione in ordine d'inserimento.
         * Un cambio di andatura o una perdita GPS apre una nuova sezione.
         * La prima linea della nuova andatura puo' usare l'ultimo punto prima
         * della transizione come ancora, senza includerlo nei campioni.
         */
        public Section accept(Fix fix) {
            if (fix == null || fix.id <= lastId) return null;
            lastId = fix.id;
            if (fix.time <= 0 || !Double.isFinite(fix.lat) || !Double.isFinite(fix.lon)
                    || Math.abs(fix.lat) > 90 || Math.abs(fix.lon) > 180) {
                previous = null;
                current = null;
                return null;
            }
            boolean connected = previous != null
                    && !RouteSegmenter.isGap(previous.time, previous.lat, previous.lon,
                    fix.time, fix.lat, fix.lon);
            Kind kind = kind(fix.movement);
            // Sprint consecutivi sono prove diverse anche senza punti di altra
            // andatura nell\u0027intervallo fra due countdown.
            boolean differentSprint = kind == Kind.SPRINT && previous != null
                    && !java.util.Objects.equals(previous.movement, fix.movement);
            if (!connected || current == null || current.kind != kind || differentSprint) {
                Section next = new Section(sections.size() + 1, kind,
                        connected ? previous.time : fix.time);
                if (connected) next.geometry.add(previous); // soltanto per il disegno
                sections.add(next);
                current = next;
            }
            current.add(fix, connected);
            previous = fix;
            return current;
        }
    }
}
