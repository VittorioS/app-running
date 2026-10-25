package it.example.passolibero;

/** Non unire su mappa i punti separati da buco GPS o teletrasporto. */
public final class RouteSegmenter {
    private RouteSegmenter() { }
    public static boolean isGap(long previousTime, double lastLat, double lastLon,
                                long time, double lat, double lon) {
        if (previousTime <= 0) return false;
        long dt = time - previousTime;
        if (dt <= 0 || dt > 30_000) return true;
        double meters = RunMath.distanceMeters(lastLat, lastLon, lat, lon);
        return meters > Math.max(50, dt / 1000.0 * 12);
    }
}
