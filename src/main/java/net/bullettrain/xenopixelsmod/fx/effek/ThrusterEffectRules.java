package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.phys.Vec3;

/**
 * Where and how big the ship thruster block's Effekseer plume plays (pure, for tests). The plume
 * (tools/effekseer/efkgen/effects/thruster.py) is authored along local +Z from the nozzle, and the
 * game sends the exhaust direction as its forward.
 */
public final class ThrusterEffectRules {
    /** Ticks between plume pulses; each pulse lasts about 0.5 s and the smoke lingers. */
    public static final int PULSE_TICKS = 8;
    /** Below this power the thruster is dark (the same threshold as its lit blockstate). */
    public static final double LIT_POWER = 0.05;

    private ThrusterEffectRules() {
    }

    /** Just outside the exhaust face of a block centred at {@code center}. */
    public static Vec3 nozzle(Vec3 center, Vec3 exhaust) {
        return center.add(exhaust.scale(0.55));
    }

    /** 0.4 at idle to 1.0 at full throttle. */
    public static float scale(double power) {
        double p = Double.isFinite(power) ? Math.max(0.0, Math.min(1.0, power)) : 0.0;
        return (float) (0.4 + 0.6 * p);
    }

    public static boolean pulseDue(long gameTime, double power) {
        return power > LIT_POWER && gameTime % PULSE_TICKS == 0;
    }

    /**
     * Whether the client draws its own vanilla flame and smoke plume: only when the server says
     * the Effekseer plume is off. Before the first config sync (and in single player, where the
     * server's settings are these ones) the Effekseer plume is assumed.
     */
    public static boolean clientDrawsVanilla(XenoServerConfig.Data synced) {
        if (synced == null) return false;
        return !synced.effekseerEnabled || !synced.effekseerShipThrusters;
    }
}
