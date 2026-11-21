package it.example.passolibero.platform.location;

import android.location.Location;
import java.util.function.Consumer;

/** Android ingress for session GPS callbacks. No scheduling or fix-quality policy is owned here. */
public final class GpsFixCallbackIngress {
    private GpsFixCallbackIngress() {}

    /** Preserve the Service's original active-session and accuracy guard, with no async hop. */
    public static void dispatch(
            boolean sessionActive, Location location, Consumer<Location> onAccepted) {
        if (!sessionActive || !location.hasAccuracy()) {
            return;
        }
        onAccepted.accept(location);
    }
}
