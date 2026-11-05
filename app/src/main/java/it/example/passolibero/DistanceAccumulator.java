package it.example.passolibero;

/**
 * Integratore distanza GPS senza dipendenze Android.
 * Se gli aggiornamenti arrivano ogni secondo, uno spostamento di 1-2 metri
 * puo' essere inferiore al rumore GPS: lo accumuliamo fino alla soglia.
 * Quando la velocita' GPS indica un vero arresto, spostiamo l'ancora
 * senza aggiungere distanza per evitare deriva da fermi.
 */
public final class DistanceAccumulator {
    private boolean anchored;
    private double anchorLat, anchorLon, anchorAccuracy;

    public void reset() { anchored = false; }

    /** @return metri effettivamente aggiunti alla sessione. */
    public double accept(double lat, double lon, double accuracyM, boolean moving) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon)
                || !Double.isFinite(accuracyM) || accuracyM <= 0) return 0;
        if (!anchored || !moving) {
            setAnchor(lat, lon, accuracyM);
            return 0;
        }
        double meters = RunMath.distanceMeters(anchorLat, anchorLon, lat, lon);
        // Soglia anti-jitter solo sul percorso accumulato, non fra fix adiacenti.
        double noiseGate = Math.max(2.5, Math.min(8.0, Math.max(accuracyM, anchorAccuracy) * 0.35));
        if (meters < noiseGate) return 0;
        setAnchor(lat, lon, accuracyM);
        return meters;
    }

    private void setAnchor(double lat, double lon, double accuracyM) {
        anchorLat = lat;
        anchorLon = lon;
        anchorAccuracy = accuracyM;
        anchored = true;
    }
}
