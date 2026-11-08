import it.example.passolibero.SprintTrial;
import it.example.passolibero.SprintProgressVoicePlan;

public final class SprintProgressVoicePlanTest {
    static void equalsMessage(String expected, String actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
    }
    static void assertTrue(boolean yes, String label) {
        if (!yes) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        SprintProgressVoicePlan distance = new SprintProgressVoicePlan(
                SprintTrial.Mode.DISTANCE, 50);
        equalsMessage(null, distance.onDistanceM(0), "Non annunciare all'avvio");
        equalsMessage(null, distance.onDistanceM(9.99), "Prima di 20%");
        equalsMessage("Mancano 40 metri alla fine dello sprint",
                distance.onDistanceM(10), "Distanza: 20%");
        equalsMessage(null, distance.onDistanceM(10.1), "Nessuna ripetizione");
        equalsMessage("Mancano 30 metri alla fine dello sprint",
                distance.onDistanceM(20), "Distanza: 40%");
        equalsMessage("Mancano 20 metri alla fine dello sprint",
                distance.onDistanceM(30), "Distanza: 60%");
        equalsMessage("Mancano 10 metri alla fine dello sprint",
                distance.onDistanceM(40), "Distanza: 80%");
        equalsMessage(null, distance.onDistanceM(50), "100% e' fine sprint");
        equalsMessage(null, distance.onDistanceM(49), "Non riemette dopo finish");
        assertTrue(distance.announcedMilestone() == 4, "4 traguardi massimi");

        SprintProgressVoicePlan time = new SprintProgressVoicePlan(SprintTrial.Mode.TIME, 10);
        for (int i = 1; i <= 4; i++) {
            equalsMessage("Mancano " + (10 - 2*i) + " secondi alla fine dello sprint",
                    time.onElapsedMs(i * 2_000L), "Tempo: " + (i * 20) + "%");
            equalsMessage(null, time.onElapsedMs(i * 2_000L + 50), "Non ripete timer");
            assertTrue(time.dueAt(100_000, i) == 100_000 + i * 2_000L,
                    "Scadenze sul clock monotono");
        }
        equalsMessage(null, time.onElapsedMs(10_000), "Niente avviso a 100%");
        SprintProgressVoicePlan leap = new SprintProgressVoicePlan(
                SprintTrial.Mode.DISTANCE, 50);
        equalsMessage("Mancano 20 metri alla fine dello sprint",
                leap.onDistanceM(31), "Salto di 3 soglie: un solo avviso attuale");
        equalsMessage(null, leap.onDistanceM(20), "Non tornare a soglie vecchie");
        equalsMessage("Mancano 10 metri alla fine dello sprint",
                leap.onDistanceM(45), "Ultima soglia dopo campione valido");
        SprintProgressVoicePlan finishedLeap = new SprintProgressVoicePlan(
                SprintTrial.Mode.DISTANCE, 50);
        equalsMessage(null, finishedLeap.onDistanceM(51),
                "Se un fix termina la prova non annunciare valori superati");
        SprintProgressVoicePlan invalid = new SprintProgressVoicePlan(
                SprintTrial.Mode.DISTANCE, 50);
        equalsMessage(null, invalid.onDistanceM(Double.NaN), "GPS non valido");
        equalsMessage(null, invalid.onDistanceM(Double.POSITIVE_INFINITY), "GPS infinito");
        equalsMessage(null, invalid.onDistanceM(-1), "Distanza negativa");
        equalsMessage("Mancano 40 metri alla fine dello sprint",
                invalid.onDistanceM(10), "Errore GPS non consuma soglia");
        SprintProgressVoicePlan nextTrial = new SprintProgressVoicePlan(
                SprintTrial.Mode.TIME, 5);
        equalsMessage("Mancano 4 secondi alla fine dello sprint",
                nextTrial.onElapsedMs(1000), "Nuova prova con reset indipendente");
        equalsMessage("Mancano 1 secondo alla fine dello sprint",
                nextTrial.onElapsedMs(4000), "Singolare e salto soglie");
        SprintProgressVoicePlan uneven = new SprintProgressVoicePlan(
                SprintTrial.Mode.DISTANCE, 55);
        equalsMessage("Mancano 44 metri alla fine dello sprint",
                uneven.onDistanceM(11), "Obiettivo non multiplo di 50");
        System.out.println("PASS: annunci sprint 20/40/60/80%, tempo e distanza, GPS e duplicati.");
    }
}
