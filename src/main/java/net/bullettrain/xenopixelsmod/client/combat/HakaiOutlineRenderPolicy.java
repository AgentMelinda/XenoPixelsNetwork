package net.bullettrain.xenopixelsmod.client.combat;

/** Pure policy for hiding the full-body outline while a progressive Hakai clip is active. */
public final class HakaiOutlineRenderPolicy {
    private HakaiOutlineRenderPolicy() {}

    public static boolean suppressOutline(boolean sweepActive, boolean vanillaGlowing, boolean auraRim) {
        return sweepActive && (vanillaGlowing || auraRim);
    }
}
