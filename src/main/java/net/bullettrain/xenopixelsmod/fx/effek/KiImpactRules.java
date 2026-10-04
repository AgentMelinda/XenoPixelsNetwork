package net.bullettrain.xenopixelsmod.fx.effek;

/** How big the punch impact that replaces a ki explosion is (pure, for tests). */
public final class KiImpactRules {
    private KiImpactRules() {
    }

    /** A quarter of DragonMineZ's explosion visual size ({@code getMaxSize}), at least 0.5. */
    public static float size(float dmzMaxSize) {
        float s = Float.isFinite(dmzMaxSize) ? dmzMaxSize * 0.25f : 1.0f;
        return Math.max(0.5f, s);
    }
}
