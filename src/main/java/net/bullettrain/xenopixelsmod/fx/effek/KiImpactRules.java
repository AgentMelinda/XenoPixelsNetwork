package net.bullettrain.xenopixelsmod.fx.effek;

import net.minecraft.world.phys.Vec3;

/** How big the punch impact that replaces a ki explosion is (pure, for tests). */
public final class KiImpactRules {
    public static final String WAVE_VISUAL = "xenopixelsmod.ki_wave_visual";
    private KiImpactRules() {
    }

    /** A quarter of DragonMineZ's explosion visual size ({@code getMaxSize}), at least 0.5. */
    public static float size(float dmzMaxSize) {
        float s = Float.isFinite(dmzMaxSize) ? dmzMaxSize * 0.25f : 1.0f;
        return Math.max(0.5f, s);
    }

    /** DMZ positions wave visuals half a block below the impact; AAA uses the actual impact. */
    public static Vec3 position(Vec3 visualPosition, boolean waveVisual) {
        return waveVisual ? visualPosition.add(0, 0.5, 0) : visualPosition;
    }
}
