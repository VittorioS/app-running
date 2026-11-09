import it.example.passolibero.RouteSectionSelection;
import it.example.passolibero.RouteSections;

public final class RouteSectionSelectionTest {
    private static void verify(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        RouteSections.Kind[] sequence = {
                RouteSections.Kind.WALK, RouteSections.Kind.RUN,
                RouteSections.Kind.SPRINT, RouteSections.Kind.WALK,
                RouteSections.Kind.SPRINT, RouteSections.Kind.RUN
        };
        int[] original = new int[sequence.length];
        for (int i = 0; i < sequence.length; i++) {
            original[i] = RouteSectionSelection.color(sequence[i], i + 1, 0);
            verify(RouteSectionSelection.widthDp(i + 1, 0) == 7,
                    "Senza selezione: larghezza normale");
        }
        verify(original[0] == original[3], "Camminate dello stesso colore");
        verify(original[1] == original[5], "Corse dello stesso colore");
        verify(original[2] == original[4], "Sprint dello stesso colore");
        verify(original[0] != original[1] && original[1] != original[2]
                && original[0] != original[2], "Intensità con colori distinti");
        for (int selected = 1; selected <= sequence.length; selected++) {
            for (int i = 0; i < sequence.length; i++) {
                int color = RouteSectionSelection.color(sequence[i], i + 1, selected);
                verify((i + 1 == selected) == (color == original[i]),
                        "Solo la sezione selezionata mantiene il suo colore");
                verify(RouteSectionSelection.widthDp(i + 1, selected)
                                == (i + 1 == selected ? 10 : 7),
                        "Solo il tratto scelto è più spesso");
            }
        }
        verify(RouteSectionSelection.color(RouteSections.Kind.OTHER, 10, 0)
                != RouteSectionSelection.color(RouteSections.Kind.OTHER, 10, 1),
                "Tratti non classificati vengono attenuati come gli altri");
        // Regression: i tratti possono sovrapporsi sulla stessa strada.
        // Deve essere disegnata per ultima proprio la sezione scelta.
        checkOrder(RouteSectionSelection.drawOrder(6, 0),
                new int[]{0, 1, 2, 3, 4, 5}, "Ordine normale");
        checkOrder(RouteSectionSelection.drawOrder(6, 2),
                new int[]{0, 2, 3, 4, 5, 1}, "Seconda sezione in primo piano");
        checkOrder(RouteSectionSelection.drawOrder(6, 6),
                new int[]{0, 1, 2, 3, 4, 5}, "Ultima sezione davanti");
        checkOrder(RouteSectionSelection.drawOrder(6, 1),
                new int[]{1, 2, 3, 4, 5, 0}, "Prima sezione davanti");
        // Durante una sessione live, il nuovo tratto non deve coprire
        // la sezione precedentemente selezionata.
        checkOrder(RouteSectionSelection.drawOrder(7, 2),
                new int[]{0, 2, 3, 4, 5, 6, 1},
                "Nuovo segmento live sotto la sezione selezionata");
        checkOrder(RouteSectionSelection.drawOrder(7, 0),
                new int[]{0, 1, 2, 3, 4, 5, 6},
                "Mostra tutte ripristina l'ordine cronologico");
        checkOrder(RouteSectionSelection.drawOrder(0, 0),
                new int[]{}, "Mappa senza tracce");
        System.out.println("PASS: selezione esclusiva, z-order sovrapposizioni e ripristino.");
    }

    private static void checkOrder(int[] actual, int[] expected, String message) {
        verify(actual.length == expected.length, message + ": lunghezza");
        boolean[] seen = new boolean[expected.length];
        for (int i = 0; i < expected.length; i++) {
            verify(actual[i] == expected[i], message + ": ordine al passo " + i);
            verify(actual[i] >= 0 && actual[i] < seen.length && !seen[actual[i]],
                    message + ": indice duplicato");
            seen[actual[i]] = true;
        }
    }
}
