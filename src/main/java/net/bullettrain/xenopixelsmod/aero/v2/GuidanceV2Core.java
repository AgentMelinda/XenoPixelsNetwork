package net.bullettrain.xenopixelsmod.aero.v2;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.aero.AeroBus;
import net.bullettrain.xenopixelsmod.aero.AeroLinkManager;
import net.bullettrain.xenopixelsmod.aero.control.AeroFlightCore;
import net.bullettrain.xenopixelsmod.aero.control.VectorMixer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Set;

/**
 * V2 flight tick. Reuses the verified v1 force hooks (panel deflection, Sable lift,
 * {@code VectorMixer}) but can force throttle to zero for {@link GuidanceV2SurfaceMode#FLAPS_ONLY}.
 */
public final class GuidanceV2Core {
    private final AeroFlightCore core = new AeroFlightCore();

    public boolean tick(AeroBus bus, ServerSubLevel ship, Level level,
                        List<AeroLinkManager.Link> links,
                        Vector3dc bodyNose, Vector3dc bodyUp, Vector3dc bodyThrust,
                        double yawDeg, double pitchDeg, double rollDeg, double throttle,
                        Vector3dc worldVelocity, double deltaSeconds, Set<BlockPos> linkedPanels,
                        GuidanceV2SurfaceMode mode) {
        double applied = appliedThrottle(mode, throttle);
        boolean stalled = core.tick(bus, ship, level, links,
                bodyNose, bodyUp, bodyThrust, yawDeg, pitchDeg, rollDeg, applied,
                worldVelocity, deltaSeconds, linkedPanels);
        if (mode != null && mode.flapsOnly()) {
            VectorMixer.shutdown(level, links);
        }
        return stalled;
    }

    public static void release(ServerSubLevel ship) {
        AeroFlightCore.release(ship);
    }

    /** True only when v2 is allowed to write thruster force this tick. */
    public static boolean requestsThrusterForce(GuidanceV2SurfaceMode mode, double throttle) {
        return mode != null && !mode.flapsOnly() && throttle > 0.0;
    }

    public static double appliedThrottle(GuidanceV2SurfaceMode mode, double throttle) {
        if (mode == null || mode.flapsOnly()) {
            return 0.0;
        }
        return throttle;
    }
}
