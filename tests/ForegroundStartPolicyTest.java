import it.example.passolibero.domain.ForegroundStartPolicy;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public final class ForegroundStartPolicyTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void compareStart(boolean permission, boolean gps) {
        AtomicInteger permissionCalls = new AtomicInteger();
        AtomicInteger gpsCalls = new AtomicInteger();
        BooleanSupplier fine = () -> {
            permissionCalls.incrementAndGet();
            return permission;
        };
        BooleanSupplier provider = () -> {
            gpsCalls.incrementAndGet();
            return gps;
        };
        boolean actual = ForegroundStartPolicy.canStart(fine, provider);
        // Identical to the legacy expression:
        // !hasLocationPermission() || !locationManager.isProviderEnabled(GPS).
        boolean legacyReject = !permission || !gps;
        check(actual == !legacyReject, "Foreground launch guard must be unchanged");
        check(permissionCalls.get() == 1, "Permission must be checked exactly once");
        check(gpsCalls.get() == (permission ? 1 : 0),
                "GPS must not be queried after a permission failure");
    }

    private static void compareType(int sdk, boolean recognized) {
        AtomicInteger calls = new AtomicInteger();
        BooleanSupplier permission = () -> {
            calls.incrementAndGet();
            return recognized;
        };
        // Representative distinct flag bits; Android constants remain at service boundary.
        int location = 8;
        int health = 256;
        int actual = ForegroundStartPolicy.foregroundServiceType(
                sdk, location, health, permission);
        int legacy = location;
        if (sdk >= 34 && recognized) legacy |= health;
        check(actual == legacy, "Foreground type must equal legacy SDK policy");
        check(calls.get() == (sdk >= 34 ? 1 : 0),
                "Older Android versions must not query health permission");
    }

    public static void main(String[] args) {
        for (boolean fine : new boolean[] {false, true})
            for (boolean gps : new boolean[] {false, true})
                compareStart(fine, gps);
        for (int sdk : new int[] {30, 31, 32, 33, 34, 35, 36})
            for (boolean recognition : new boolean[] {false, true})
                compareType(sdk, recognition);

        Random random = new Random(60062);
        for (int i = 0; i < 1000; i++) {
            compareStart(random.nextBoolean(), random.nextBoolean());
            compareType(25 + random.nextInt(15), random.nextBoolean());
        }
        check(ForegroundStartPolicy.foregroundServiceType(
                30, 8, 256, () -> { throw new AssertionError("Never read health permission on API 30"); })
                == 8, "API 30 is safe without the Android 14 permission");
        System.out.println("PASS: " + checks + " foreground guard/lifecycle assertions.");
    }
}
