import it.example.passolibero.SessionTabStats;

/** Controlli indipendenti per dati corsa, camminata e somma delle due. */
public final class SessionTabStatsTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        SessionTabStats.Tab run = SessionTabStats.Tab.RUN;
        SessionTabStats.Tab walk = SessionTabStats.Tab.WALK;
        SessionTabStats.Tab total = SessionTabStats.Tab.TOTAL;
        check(SessionTabStats.meters(run, 1200, 430) == 1200, "Corsa senza metri camminata");
        check(SessionTabStats.meters(walk, 1200, 430) == 430, "Camminata senza metri corsa");
        check(SessionTabStats.meters(total, 1200, 430) == 1630, "Totale = corsa + camminata");
        check(SessionTabStats.milliseconds(run, 360_000, 420_000) == 360_000, "Tempo corsa");
        check(SessionTabStats.milliseconds(walk, 360_000, 420_000) == 420_000, "Tempo camminata");
        check(SessionTabStats.milliseconds(total, 360_000, 420_000) == 780_000, "Tempo combinato");
        check(SessionTabStats.steps(run, 500, 310) == 500, "Passi corsa");
        check(SessionTabStats.steps(walk, 500, 310) == 310, "Passi camminata");
        check(SessionTabStats.steps(total, 500, 310) == 810, "Passi attribuiti combinati");
        check(Math.abs(SessionTabStats.averageSpeedKmh(360_000, 1200) - 12) < .001, "Velocita' media corsa");
        check(Math.abs(SessionTabStats.averageSpeedKmh(420_000, 430) - 3.685714) < .001, "Velocita' media camminata");
        check(Math.abs(SessionTabStats.averageCadence(360_000, 500) - 83.33333) < .001, "Cadenza media");
        check(Double.isNaN(SessionTabStats.averageSpeedKmh(0, 0)), "Nessuna velocita' inventata");
        check(Double.isNaN(SessionTabStats.averageCadence(0, 0)), "Nessuna cadenza inventata");
        check(SessionTabStats.hasCurrentSpeed(run, "RUN"), "Ritmo istantaneo corsa");
        check(!SessionTabStats.hasCurrentSpeed(run, "WALK"), "Non mescolare velocita' camminata nella corsa");
        check(!SessionTabStats.hasCurrentSpeed(walk, "RUN"), "Non mescolare velocita' corsa nella camminata");
        check(!SessionTabStats.hasCurrentSpeed(total, "MOVING_UNKNOWN"), "Andatura ignota non appartiene alle due");
        check(SessionTabStats.hasCurrentSpeed(total, "WALK"), "Totali prendono la camminata corrente");
        check(SessionTabStats.meters(total, 0, 0) == 0, "Avvio senza campioni");
        System.out.println("PASS: 19 controlli di isolamento tra tab corsa, camminata e totali");
    }
}
