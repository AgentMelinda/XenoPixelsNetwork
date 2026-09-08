package net.bullettrain.xenopixelsmod.client.pad;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.rumble.BasicRumbleEffect;
import dev.isxander.controlify.rumble.RumbleSource;

/**
 * Controller vibration for combat impacts.
 *
 * <p><b>Stability caveat.</b> {@link BasicRumbleEffect} lives in
 * {@code dev.isxander.controlify.rumble}, outside Controlify's {@code api} package, so unlike
 * every other Controlify type this mod touches it carries no stability promise and could change
 * shape in a future Controlify release. It is used anyway because {@code playRumbleEffect} — which
 * <em>is</em> public API — needs a {@code RumbleEffect}, and the api package exposes no way to
 * build one. If a Controlify update breaks this, rumble is the only thing that stops working.
 *
 * <p>Like everything in this package, this class must never be loaded when Controlify is absent.
 * Callers go through {@link XenoPadInput}.
 */
final class XenoPadRumble {

    /** Strong (low-frequency) motor share. Impacts are a thump, so it carries most of the weight. */
    private static final float STRONG_SHARE = 1.0f;
    /** Weak (high-frequency) motor share, for the sharp edge of the hit. */
    private static final float WEAK_SHARE = 0.55f;

    private XenoPadRumble() {
    }

    /**
     * A single impact buzz.
     *
     * @param strength already faded by distance by the caller, so a fight across the arena is felt
     *                 faintly and one in your face is felt properly
     * @param ticks    how long the buzz lasts
     */
    static void impact(float strength, int ticks) {
        float clamped = Math.max(0f, Math.min(1f, strength));
        if (clamped <= 0.02f) return;
        ControlifyApi.get().playRumbleEffect(RumbleSource.PLAYER,
                BasicRumbleEffect.constant(clamped * STRONG_SHARE, clamped * WEAK_SHARE,
                        Math.max(1, ticks)));
    }
}
