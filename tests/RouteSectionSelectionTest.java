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
        System.out.println("PASS: selezione esclusiva e ripristino dei sei tratti.");
    }
}
