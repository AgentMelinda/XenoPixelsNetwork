package net.bullettrain.xenopixelsmod.space.math;

/** Pure policy joining the server permission and client's cosmetic density setting. */
public final class FallenStarVisibility {
    private FallenStarVisibility() {}

    public static int effectiveDensity(boolean serverEnabled, int clientDensity) {
        return serverEnabled ? Math.max(0, Math.min(100, clientDensity)) : 0;
    }

    /** Normalizes the game's 0..0.5 vanilla star curve to full streak alpha while preserving dawn/dusk fade. */
    public static float nightVisibility(float starBrightness) {
        return nightVisibility(starBrightness, 0.0f);
    }

    /** Matches vanilla's weather attenuation for stars while sanitizing both engine inputs. */
    public static float nightVisibility(float starBrightness, float rainLevel) {
        if (!Float.isFinite(starBrightness) || !Float.isFinite(rainLevel)) return 0.0f;
        float stars = Math.max(0.0f, Math.min(1.0f, starBrightness * 2.0f));
        float rain = Math.max(0.0f, Math.min(1.0f, rainLevel));
        return stars * (1.0f - rain);
    }

    /** Sanitizes an already-computed visibility multiplier without re-normalizing the star curve. */
    public static float clampVisibility(float visibility) {
        if (!Float.isFinite(visibility)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, visibility));
    }
}
