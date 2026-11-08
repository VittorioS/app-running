package it.example.passolibero;

import android.content.Context;
import android.graphics.Color;
import android.app.AlertDialog;

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
import java.util.Date;
import java.text.DateFormat;
import java.util.Locale;

/** Mappa OSM: soltanto punti locali della sessione richiesta, mai tracce precedenti. */
public final class TrackMap {
    private final MapView view;
    private final Marker currentPosition;
    private final List<Polyline> segments = new ArrayList<>();
    private final List<GeoPoint> allPoints = new ArrayList<>();
    private final RouteSections.Builder routeBuilder = new RouteSections.Builder();
    private RouteSections.Section visibleSection;
    private Polyline visibleLine;
    private boolean hasFix;
    private boolean autoFollowCurrentPosition;
    private boolean trackingMapVisible;
    private GeoPoint lastCenteredPosition;
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
        currentPosition = new Marker(view);
        currentPosition.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        currentPosition.setTitle("Posizione attuale");
        // Il marker compare soltanto dopo l'arrivo di una posizione valida.
    }

    private Polyline newSegment(RouteSections.Section section) {
        Polyline line = new Polyline(view);
        switch (section.kind) {
            case WALK: line.setColor(Color.rgb(30, 153, 84)); break;
            case RUN: line.setColor(Color.rgb(40, 99, 224)); break;
            case SPRINT: line.setColor(Color.rgb(222, 53, 64)); break;
            default: line.setColor(Color.rgb(127, 133, 141)); break;
        }
        line.setWidth(Ui.dp(view.getContext(), 7));
        line.setOnClickListener((polyline, mapView, touched) -> {
            showSectionDetails(section);
            return true;
        });
        view.getOverlays().add(line);
        segments.add(line);
        if (view.getOverlays().remove(currentPosition)) view.getOverlays().add(currentPosition);
        return line;
    }

    private void showSectionDetails(RouteSections.Section section) {
        if (section == null) return;
        String clockStart = DateFormat.getTimeInstance(
                DateFormat.SHORT, Locale.ITALY).format(new Date(section.startedAt));
        String clockEnd = DateFormat.getTimeInstance(
                DateFormat.SHORT, Locale.ITALY).format(new Date(section.endedAt));
        double average = section.averageKmh();
        String averageText = Double.isFinite(average)
                ? RunMath.velocity(average) + " km/h" : "—";
        String maxText = Double.isFinite(section.maxKmh)
                ? RunMath.velocity(section.maxKmh) + " km/h" : "—";
        String information = "Andatura: " + RouteSections.label(section.kind)
                + "\nOrario: " + clockStart + " – " + clockEnd
                + "\nDurata: " + RunMath.duration(section.durationMs())
                + "\nDistanza: " + String.format(Locale.ITALY, "%.1f m", section.distanceM)
                + "\nVelocità media: " + averageText
                + "\nVelocità massima: " + maxText
                + "\nCampioni GPS: " + section.samples
                + "\n\nValori stimati dai campioni GPS registrati per questo tratto; "
                + "possono differire dai totali dell'allenamento.";
        new AlertDialog.Builder(view.getContext())
                .setTitle("Sezione " + section.number + " · "
                        + RouteSections.label(section.kind))
                .setMessage(information)
                .setPositiveButton("Chiudi", null)
                .show();
    }

    public MapView view() { return view; }

    /** Per le sessioni attive segue il GPS; lo storico resta liberamente esplorabile. */
    public void setAutoFollowCurrentPosition(boolean enabled) {
        autoFollowCurrentPosition = enabled;
        currentPosition.setTitle(enabled ? "Posizione attuale" : "Fine percorso");
        trackingMapVisible = false;
        syncMapNetworkAccess();
    }

    /** Pause tile fetching when the live map card is offscreen. */
    public void setTrackingMapVisible(boolean visible) {
        if (!autoFollowCurrentPosition || trackingMapVisible == visible) return;
        trackingMapVisible = visible;
        syncMapNetworkAccess();
    }

    private void syncMapNetworkAccess() {
        if (!autoFollowCurrentPosition) return;
        boolean useNetwork = trackingMapVisible && hasFix;
        if (view.useDataConnection() != useNetwork) {
            view.setUseDataConnection(useNetwork);
            if (useNetwork) view.invalidate();
        }
    }

    private void followCurrentPosition() {
        if (!autoFollowCurrentPosition || !trackingMapVisible || !hasFix
                || currentPosition.getPosition() == null) return;
        GeoPoint p = currentPosition.getPosition();
        if (lastCenteredPosition != null &&
                RunMath.distanceMeters(lastCenteredPosition.getLatitude(),
                        lastCenteredPosition.getLongitude(),
                        p.getLatitude(), p.getLongitude()) < 3.0) return;
        // Keep close follow, but avoid repeated camera recentering from GPS jitter.
        if (Math.abs(view.getZoomLevelDouble() - 18.5) > 0.01)
            view.getController().setZoom(18.5);
        view.getController().setCenter(p);
        lastCenteredPosition = p;
    }

    public void clearForSession(long id) {
        if (sessionId == id) return;
        sessionId = id;
        pointCursor = 0;
        allPoints.clear();
        routeBuilder.reset();
        visibleSection = null;
        visibleLine = null;
        hasFix = false;
        lastCenteredPosition = null;
        syncMapNetworkAccess();
        view.getOverlays().removeAll(segments);
        segments.clear();
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
            RouteSections.Section section = routeBuilder.accept(new RouteSections.Fix(
                    p.id, p.time, p.lat, p.lon, p.deltaM, p.speedKmh,
                    p.accuracyM, p.movement));
            if (section == null) continue;
            if (visibleSection != section) {
                visibleSection = section;
                visibleLine = newSegment(section);
                if (section.geometry.size() > 1) {
                    RouteSections.Fix anchor = section.geometry.get(0);
                    visibleLine.addPoint(new GeoPoint(anchor.lat, anchor.lon));
                }
            }
            visibleLine.addPoint(gp);
            allPoints.add(gp);
            hasFix = true;
            syncMapNetworkAccess();
            currentPosition.setPosition(gp);
            if (!view.getOverlays().contains(currentPosition)) view.getOverlays().add(currentPosition);
            if (firstPoint) {
                view.getController().setZoom(autoFollowCurrentPosition ? 18.5 : 17.0);
                view.getController().setCenter(gp);
                lastCenteredPosition = gp;
                firstPoint = false;
            }
        }
        if (!additional.isEmpty()) {
            followCurrentPosition();
            view.invalidate();
        }
        return additional.size();
    }

    /** Posizione precisa visibile anche fra i punti salvati nel DB. */
    public void updateLivePosition(double lat, double lon, boolean valid) {
        if (!valid || sessionId <= 0) return;
        GeoPoint gp = new GeoPoint(lat, lon);
        GeoPoint prior = currentPosition.getPosition();
        if (hasFix && prior != null &&
                RunMath.distanceMeters(prior.getLatitude(), prior.getLongitude(), lat, lon) < 2.5)
            return; // Do not redraw for repeated positions and tiny GPS noise.
        currentPosition.setPosition(gp);
        hasFix = true;
        syncMapNetworkAccess();
        if (!view.getOverlays().contains(currentPosition)) view.getOverlays().add(currentPosition);
        if (firstPoint) {
            view.getController().setZoom(17.0);
            view.getController().setCenter(gp);
            lastCenteredPosition = gp;
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
