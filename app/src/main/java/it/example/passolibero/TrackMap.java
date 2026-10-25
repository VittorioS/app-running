package it.example.passolibero;

import android.content.Context;
import android.graphics.Color;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Mappa OSM: soltanto punti locali della sessione richiesta, mai tracce precedenti. */
public final class TrackMap {
    private final MapView view;
    private Polyline path;
    private final Marker currentPosition;
    private final List<Polyline> segments = new ArrayList<>();
    private final List<GeoPoint> points = new ArrayList<>();
    private final List<GeoPoint> allPoints = new ArrayList<>();
    private boolean hasFix;
    private boolean autoFollowCurrentPosition;
    private long lastPointTime;
    private GeoPoint previousPoint;
    private boolean firstPoint = true;
    private long pointCursor;
    private long sessionId = -1;

    public TrackMap(Context context) {
        IConfigurationProvider config = Configuration.getInstance();
        // User Agent identificabile: obbligatorio per le tessere OSM ufficiali.
        config.setUserAgentValue("PassoLibero/2.0 (it.example.passolibero)");
        File base = context.getExternalFilesDir(null);
        if (base != null) {
            File root = new File(base, "osmdroid");
            File cache = new File(root, "tiles");
            if (!cache.isDirectory()) cache.mkdirs();
            config.setOsmdroidBasePath(root);
            config.setOsmdroidTileCache(cache);
        }
        view = new MapView(context);
        // Solo server ufficiale HTTPS e soltanto tessere visibili all'utente.
        view.setTileSource(new XYTileSource("OpenStreetMap", 0, 19, 256,
                ".png", new String[]{"https://tile.openstreetmap.org/"},
                "© OpenStreetMap contributors"));
        view.setMultiTouchControls(true);
        view.getController().setZoom(5.0);
        view.getController().setCenter(new GeoPoint(41.8, 12.6));
        path = newSegment();
        currentPosition = new Marker(view);
        currentPosition.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        currentPosition.setTitle("Posizione attuale");
        // Il marker compare soltanto dopo l'arrivo di una posizione valida.
    }

    private Polyline newSegment() {
        Polyline line = new Polyline();
        line.setColor(Color.rgb(0, 125, 115));
        line.setWidth(Ui.dp(view.getContext(), 5));
        view.getOverlays().add(line);
        segments.add(line);
        return line;
    }

    public MapView view() { return view; }

    /** Per le sessioni attive segue il GPS; lo storico resta liberamente esplorabile. */
    public void setAutoFollowCurrentPosition(boolean enabled) {
        autoFollowCurrentPosition = enabled;
    }

    private void followCurrentPosition() {
        if (!autoFollowCurrentPosition || !hasFix || currentPosition.getPosition() == null) return;
        // Zoom ravvicinato: circa il livello della strada, senza pulsanti di ricentramento.
        if (Math.abs(view.getZoomLevelDouble() - 18.5) > 0.01)
            view.getController().setZoom(18.5);
        view.getController().setCenter(currentPosition.getPosition());
    }

    public void clearForSession(long id) {
        if (sessionId == id) return;
        sessionId = id;
        pointCursor = 0;
        points.clear();
        allPoints.clear();
        hasFix = false;
        view.getOverlays().removeAll(segments);
        segments.clear();
        path = newSegment();
        previousPoint = null;
        lastPointTime = 0;
        view.getOverlays().remove(currentPosition);
        firstPoint = true;
        view.invalidate();
    }

    public int update(TrackerDb db, long id) {
        clearForSession(id);
        if (id <= 0) return 0;
        List<TrackerDb.TrackPoint> additional = db.pointsAfter(id, pointCursor, 500);
        for (TrackerDb.TrackPoint p : additional) {
            pointCursor = p.id;
            GeoPoint gp = new GeoPoint(p.lat, p.lon);
            if (previousPoint != null && RouteSegmenter.isGap(lastPointTime,
                    previousPoint.getLatitude(), previousPoint.getLongitude(),
                    p.time, p.lat, p.lon)) {
                points.clear();
                path = newSegment();
            }
            points.add(gp);
            allPoints.add(gp);
            hasFix = true;
            previousPoint = gp;
            lastPointTime = p.time;
            currentPosition.setPosition(gp);
            if (!view.getOverlays().contains(currentPosition)) view.getOverlays().add(currentPosition);
            if (firstPoint) {
                view.getController().setZoom(autoFollowCurrentPosition ? 18.5 : 17.0);
                view.getController().setCenter(gp);
                firstPoint = false;
            }
        }
        if (!additional.isEmpty()) {
            path.setPoints(new ArrayList<>(points));
            followCurrentPosition();
            view.invalidate();
        }
        return additional.size();
    }

    /** Posizione precisa visibile anche fra i punti salvati nel DB. */
    public void updateLivePosition(double lat, double lon, boolean valid) {
        if (!valid || sessionId <= 0) return;
        GeoPoint gp = new GeoPoint(lat, lon);
        currentPosition.setPosition(gp);
        hasFix = true;
        if (!view.getOverlays().contains(currentPosition)) view.getOverlays().add(currentPosition);
        if (firstPoint) {
            view.getController().setZoom(17.0);
            view.getController().setCenter(gp);
            firstPoint = false;
        }
        followCurrentPosition();
        view.invalidate();
    }

    public void zoomToRoute() {
        if (allPoints.isEmpty()) return;
        if (allPoints.size() == 1) {
            view.getController().setCenter(allPoints.get(0));
            view.getController().setZoom(17.0);
            return;
        }
        double north = -90, south = 90, east = -180, west = 180;
        for (GeoPoint p : allPoints) {
            north = Math.max(north, p.getLatitude());
            south = Math.min(south, p.getLatitude());
            east = Math.max(east, p.getLongitude());
            west = Math.min(west, p.getLongitude());
        }
        view.zoomToBoundingBox(new BoundingBox(north + 0.0001, east + 0.0001,
                south - 0.0001, west - 0.0001), true, Ui.dp(view.getContext(), 25));
    }

    public void onResume() { view.onResume(); }
    public void onPause() { view.onPause(); }
    public void onDestroy() { view.onDetach(); }
}
