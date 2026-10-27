package net.bullettrain.xenopixelsmod.space.smallplanet;

/** Hard admission rule for edited planets that remain force-loaded and orbit-managed. */
public final class SmallPlanetEditedBodyPolicy {
    private SmallPlanetEditedBodyPolicy() {}

    public enum Admission { UNMANAGED, ALLOWED, NOT_ACTIVE, CAP_REACHED, FEATURE_DISABLED }

    public static boolean canPin(int pinnedCount, int configuredCap, boolean alreadyPinned) {
        if (alreadyPinned) return true;
        return pinnedCount >= 0 && configuredCap >= 0 && pinnedCount < configuredCap;
    }

    /** Unrelated explosions proceed; tagged generated planets require an active body with a reserved edit slot. */
    public static boolean allowMutation(Admission admission) {
        return admission == Admission.UNMANAGED || admission == Admission.ALLOWED;
    }

    /** Disabling new physicalization must not leave already-edited planets without a safe hold controller. */
    public static boolean shouldRunOrbitController(boolean physicalizationEnabled, boolean editedPinned) {
        return physicalizationEnabled || editedPinned;
    }

    public static String refusalMessage(Admission admission) {
        return switch (admission) {
            case CAP_REACHED -> "Edited small-planet cap reached; this planet edit was prevented.";
            case FEATURE_DISABLED -> "Small-planet edits are unavailable while space physicalization is disabled.";
            case NOT_ACTIVE -> "Small planet is not ready for edits yet; try again shortly.";
            case UNMANAGED, ALLOWED -> "";
        };
    }
}
