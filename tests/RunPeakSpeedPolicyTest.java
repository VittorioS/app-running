import it.example.passolibero.RunPeakSpeedPolicy;

public final class RunPeakSpeedPolicyTest {
    private static void check(boolean good, String message) {
        if (!good) throw new AssertionError(message);
    }

    private static double sample(double max, String movement, boolean moving,
                                 boolean fresh, double accuracy, double delta, double speed) {
        return RunPeakSpeedPolicy.update(max, movement, moving, fresh, accuracy, delta, speed);
    }

    public static void main(String[] args) {
        double empty = Double.NaN;
        check(Double.isNaN(sample(empty, "WALK", true, true, 5, 3, 7)), "Ignora camminata");
        check(Double.isNaN(sample(empty, "STILL", true, true, 5, 3, 7)), "Ignora fermo");
        check(Double.isNaN(sample(empty, "UNKNOWN", true, true, 5, 3, 7)), "Ignora ignoto");
        check(Double.isNaN(sample(empty, "RUN", false, true, 5, 3, 8)), "Richiede movimento corroborato");
        check(Double.isNaN(sample(empty, "RUN", true, false, 5, 3, 8)), "GPS vecchio");
        check(Double.isNaN(sample(empty, "RUN", true, true, 16, 3, 8)), "Precisione scarsa");
        check(Double.isNaN(sample(empty, "RUN", true, true, 5, 0, 8)), "Nessuna distanza ammessa");
        check(Double.isNaN(sample(empty, "RUN", true, true, 5, 3, 0)), "Nessuna velocita'");
        check(Double.isNaN(sample(empty, "RUN", true, true, 5, 3, Double.NaN)), "NaN scartato");
        check(Double.isNaN(sample(empty, "RUN", true, true, 5, 3, 44)), "Salto velocita' scartato");
        double max = sample(empty, "RUN", true, true, 7, 3, 12.7);
        check(max == 12.7, "Prima misura di corsa");
        max = sample(max, "RUN", true, true, 9, 4, 15.8);
        check(max == 15.8, "Nuovo massimo");
        max = sample(max, "RUN", true, true, 6, 2, 9.5);
        check(max == 15.8, "La corsa piu' lenta non modifica il record");
        max = sample(max, "WALK", true, true, 3, 7, 22);
        check(max == 15.8, "La camminata non altera il massimo");
        check(sample(14.0, "RUN", true, true, 15, 3, 12.7) == 14.0, "Precisione al limite");
        check(sample(max, "RUN", true, true, Double.NaN, 3, 25) == max, "Precisione assente");
        System.out.println("PASS: velocita' massima attribuita solo a corsa GPS affidabile");
    }
}
