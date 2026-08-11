package net.bullettrain.xenopixelsmod.aero.control;

import net.bullettrain.xenopixelsmod.aero.AeroLinkManager;
import net.bullettrain.xenopixelsmod.block.entity.ShipThrusterBlockEntity;
import net.bullettrain.xenopixelsmod.compat.thruster.ExternalThrusterCompat;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Distributes a commanded body-space thrust vector across the linked engines.
 *
 * <p>Each engine contributes along its own facing, so the mixer simply asks how well that
 * facing aligns with what the pilot wants and throttles accordingly. Engines pointing away
 * from the commanded direction are cut rather than reversed — a thruster cannot pull.
 *
 * <p><b>Commands go through {@code setCcPower}</b>, the block entity's existing external
 * control input, rather than writing {@code XenoThrusterControl} entries directly. That reuses
 * the thruster's own physics push, including its stagger, re-push cadence and the
 * {@code XenoPerfConfig} gating that keeps fleets cheap — and avoids two writers fighting over
 * the same force key.
 */
public final class VectorMixer {

    /** Below this throttle delta we leave the engine alone; each push syncs the block entity. */
    private static final double PUSH_EPSILON = 0.02;

    private VectorMixer() {
    }

    /**
     * @param desiredX body-space thrust direction, need not be normalized
     * @param throttle overall commanded throttle, 0..1
     * @return number of engines actually commanded
     */
    public static int apply(Level level, List<AeroLinkManager.Link> links,
                            double desiredX, double desiredY, double desiredZ, double throttle) {
        if (level == null || level.isClientSide || links == null || links.isEmpty()) return 0;

        double length = Math.sqrt(desiredX * desiredX + desiredY * desiredY + desiredZ * desiredZ);
        double clamped = Math.max(0.0, Math.min(1.0, throttle));
        if (length < 1.0e-6 || clamped <= 0.0) {
            return shutdown(level, links);
        }
        double nx = desiredX / length;
        double ny = desiredY / length;
        double nz = desiredZ / length;

        int commanded = 0;
        for (AeroLinkManager.Link link : links) {
            if (!link.healthy()) continue;
            Direction thrust = link.thrust();
            if (thrust == null) continue;

            // Alignment in [-1, 1]; only forward-contributing engines are lit.
            double alignment = thrust.getStepX() * nx + thrust.getStepY() * ny + thrust.getStepZ() * nz;
            double power = alignment <= 0.0 ? 0.0 : Math.min(1.0, alignment * clamped);
            if (command(level, link, power)) commanded++;
        }
        return commanded;
    }

    /** Cut every engine. Used on disengage, power loss and emergency stop. */
    public static int shutdown(Level level, List<AeroLinkManager.Link> links) {
        if (level == null || level.isClientSide || links == null) return 0;
        int stopped = 0;
        for (AeroLinkManager.Link link : links) {
            if (command(level, link, 0.0)) stopped++;
        }
        return stopped;
    }

    private static boolean command(Level level, AeroLinkManager.Link link, double power) {
        try {
            if (level.getBlockEntity(link.pos()) instanceof ShipThrusterBlockEntity thruster) {
                double current = thruster.getCcPower();
                // getCcPower returns -1 when under redstone control; treat that as "unset" so
                // the first flight command always lands.
                if (current >= 0.0 && Math.abs(current - power) < PUSH_EPSILON) return true;
                thruster.setCcPower(power <= 0.001 ? 0.0 : power);
                return true;
            }
            return ExternalThrusterCompat.setThrottle(level, link.pos(), power);
        } catch (Throwable ignored) {
            // One bad engine must not abort the mix.
            return false;
        }
    }
}
