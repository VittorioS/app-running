import it.example.passolibero.RouteSections;

public final class RouteSectionsTest {
    private static void yes(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static RouteSections.Fix p(int i, long time, double lat, double lon,
                                       double delta, double speed, String kind) {
        return new RouteSections.Fix(i, time, lat, lon, delta, speed, 5, kind);
    }
    public static void main(String[] args) {
        RouteSections.Builder b = new RouteSections.Builder();
        // 6 fasi: WALK, RUN, SPRINT, WALK, SPRINT, RUN.
        b.accept(p(1,1000,40,14,0,4,"WALK"));
        b.accept(p(2,2000,40.00001,14,1,5,"WALK"));
        b.accept(p(3,3000,40.00002,14,1,11,"RUN"));
        b.accept(p(4,4000,40.00003,14,1,13,"RUN"));
        b.accept(p(5,5000,40.00004,14,1,24,"SPRINT"));
        b.accept(p(6,6000,40.00005,14,1,27,"SPRINT"));
        b.accept(p(7,7000,40.00006,14,1,4,"WALK"));
        b.accept(p(8,8000,40.00007,14,1,6,"WALK"));
        b.accept(p(9,9000,40.00008,14,1,22,"SPRINT"));
        b.accept(p(10,10000,40.00009,14,1,29,"SPRINT"));
        b.accept(p(11,11000,40.00010,14,1,12,"RUN"));
        b.accept(p(12,12000,40.00011,14,1,13,"RUN"));
        yes(b.sections().size() == 6, "Sei sezioni separate");
        RouteSections.Kind[] expected = {RouteSections.Kind.WALK, RouteSections.Kind.RUN,
            RouteSections.Kind.SPRINT, RouteSections.Kind.WALK,
            RouteSections.Kind.SPRINT, RouteSections.Kind.RUN};
        for (int i=0; i<expected.length; i++) {
            RouteSections.Section section = b.sections().get(i);
            yes(section.kind == expected[i] && section.number == i+1, "Ordine sezione " + i);
            yes(section.samples == 2 && section.geometry.size() >= 2,
                    "Statistiche e geometria sezione " + i);
            yes(section.durationMs() > 0 && section.distanceM > 0,
                    "Distanza e tempo della sezione " + i);
        }
        yes(b.sections().get(2).maxKmh == 27, "Picco del primo sprint");
        yes(b.sections().get(4).maxKmh == 29, "Picco del secondo sprint indipendente");
        yes(Math.abs(b.sections().get(0).averageKmh()-3.6) < .001,
                "Conversione distanza/tempo corretta");
        int before = b.sections().size();
        b.accept(p(12,12000,40.00011,14,1,13,"RUN"));
        yes(b.sections().size() == before && b.sections().get(5).samples == 2,
                "Ricaricamento incrementale senza duplicati");

        RouteSections.Builder gaps = new RouteSections.Builder();
        gaps.accept(p(1,1000,40,14,0,6,"WALK"));
        gaps.accept(p(2,2000,40.00001,14,1,7,"WALK"));
        gaps.accept(p(3,45000,40.00002,14,9,7,"WALK"));
        yes(gaps.sections().size() == 2, "Buco GPS apre una nuova sezione");
        yes(gaps.sections().get(1).distanceM == 0, "Non attribuisce delta oltre il buco");
        gaps.accept(p(4,46000,40.00003,14,1,0,"STILL"));
        gaps.accept(p(5,47000,40.00004,14,1,0,"UNKNOWN"));
        yes(gaps.sections().size() == 3, "Stati non classificati raggruppati in grigio");
        gaps.accept(p(6,48000,40.00005,14,1,8,"WALK"));
        yes(gaps.sections().size() == 4, "Ripresa camminata nuovo segmento");
        gaps.accept(p(7,49000,41.9,14,80,20,"WALK"));
        yes(gaps.sections().size() == 5, "Salto GPS non collega le linee");
        yes(gaps.sections().get(4).distanceM == 0, "Salto senza metri inventati");
        gaps.reset();
        yes(gaps.sections().isEmpty(), "Cambio sessione: niente tratti precedenti");
        yes(RouteSections.kind("SPRINT") == RouteSections.Kind.SPRINT,
                "Etichetta sprint registrata nel DB");
        RouteSections.SprintWindow old = new RouteSections.SprintWindow(70_000, 10_000);
        yes(old.includes(60_000) && old.includes(70_000), "Sprint del vecchio storico");
        yes(!old.includes(58_000) && !old.includes(72_000),
                "Non attribuire movimenti lontani dalla prova");
        RouteSections.Builder oldRoute = new RouteSections.Builder();
        oldRoute.accept(new RouteSections.Fix(1, 60_000, 40, 14, 0, 8, 5, "RUN", true));
        oldRoute.accept(new RouteSections.Fix(2, 61_000, 40.00001, 14, 1, 14, 5, "SPRINT", true));
        yes(oldRoute.sections().get(1).approximateSprint,
                "Sezione storica segnala ricostruzione approssimativa");
        System.out.println("PASS: sei sezioni, GPS gap, statistiche, sprint ripetuti e recupero storico.");
    }
}
