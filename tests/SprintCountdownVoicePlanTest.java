import it.example.passolibero.SprintCountdownVoicePlan;

public final class SprintCountdownVoicePlanTest {
    private static void check(boolean yes, String label) {
        if (!yes) throw new AssertionError(label);
    }

    private static void verify(int n) {
        SprintCountdownVoicePlan plan = new SprintCountdownVoicePlan(123_000, n);
        check(plan.steps() == n + 1, "Annuncia ogni numero e via");
        for (int step = 0; step < n; step++) {
            check(plan.phrase(step).equals(Integer.toString(n - step)),
                    "Numero del conto alla rovescia " + step);
            check(plan.dueAt(step) == 123_000 + step * 1000L,
                    "Un numero al secondo, senza dipendere dal GPS");
            check(!plan.isStale(step, plan.dueAt(step)), "Annuncio puntuale");
            check(plan.isStale(step, plan.dueAt(step) + 700),
                    "Non pronunciare un numero vecchio in ritardo");
        }
        check("Via, sprint".equals(plan.phrase(n)), "Annuncio di partenza");
        check(plan.dueAt(n) == 123_000 + n * 1000L,
                "Partenza sincronizzata con la fine del countdown");
    }

    public static void main(String[] args) {
        verify(5);
        verify(6);
        verify(7);
        verify(8);
        verify(9);
        verify(10);
        boolean rejected = false;
        try { new SprintCountdownVoicePlan(0, 4); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Non consente meno di cinque secondi");
        rejected = false;
        try { new SprintCountdownVoicePlan(0, 11); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Non consente piu' di dieci secondi");
        System.out.println("PASS: annuncio completo sprint 5-10 fino a via, clock monotono.");
    }
}
