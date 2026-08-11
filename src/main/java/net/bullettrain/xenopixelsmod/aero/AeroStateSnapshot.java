package net.bullettrain.xenopixelsmod.aero;

import net.minecraft.core.BlockPos;

import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable wire view of an {@link AeroBus}, sent server → client for the controller GUI.
 *
 * <p>Kept as a plain record with no Minecraft client types so the dedicated server can load
 * it, matching the existing {@code ClientScreens.GuidanceOpenData} convention.
 *
 * <p>Enabled subsystems travel as a bitmask over {@link AeroSubsystem#ordinal()} rather than a
 * list, which keeps the packet fixed-size. That does mean subsystem <b>declaration order is a
 * wire contract</b>: append new entries at the end, never reorder.
 */
public record AeroStateSnapshot(
        BlockPos controllerPos,
        ControllerMode mode,
        int enabledMask,
        double throttle,
        double yawDeg,
        double pitchDeg,
        double rollDeg,
        boolean flightEngaged,
        AeroAutopilotMode autopilotMode,
        int waypointIndex,
        int waypointCount,
        double targetDistance,
        double actualSpeed,
        AeroBus.PowerTier powerTier,
        int storedEnergy,
        int drawFePerTick,
        int linkCount,
        int healthyLinkCount,
        String status
) {
    public static AeroStateSnapshot of(BlockPos pos, AeroBus bus) {
        return new AeroStateSnapshot(
                pos,
                bus.mode(),
                maskOf(bus.enabledSubsystems()),
                bus.throttle(),
                bus.yawDeg(),
                bus.pitchDeg(),
                bus.rollDeg(),
                bus.isFlightEngaged(),
                bus.autopilotMode(),
                bus.waypointIndex(),
                bus.waypointCount(),
                bus.targetDistance(),
                bus.actualSpeed(),
                bus.powerTier(),
                bus.storedEnergy(),
                bus.drawFePerTick(),
                bus.linkCount(),
                bus.healthyLinkCount(),
                bus.status());
    }

    public static int maskOf(Set<AeroSubsystem> subsystems) {
        int mask = 0;
        for (AeroSubsystem subsystem : subsystems) {
            mask |= 1 << subsystem.ordinal();
        }
        return mask;
    }

    public boolean isEnabled(AeroSubsystem subsystem) {
        return subsystem != null && (enabledMask & (1 << subsystem.ordinal())) != 0;
    }

    public Set<AeroSubsystem> enabledSubsystems() {
        EnumSet<AeroSubsystem> set = EnumSet.noneOf(AeroSubsystem.class);
        for (AeroSubsystem subsystem : AeroSubsystem.values()) {
            if (isEnabled(subsystem)) set.add(subsystem);
        }
        return set;
    }
}
