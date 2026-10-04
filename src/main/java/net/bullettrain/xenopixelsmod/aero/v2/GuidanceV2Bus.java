package net.bullettrain.xenopixelsmod.aero.v2;

import net.bullettrain.xenopixelsmod.aero.AeroBus;
import net.bullettrain.xenopixelsmod.aero.AeroControlHost;
import net.bullettrain.xenopixelsmod.aero.AeroStateSnapshot;
import net.bullettrain.xenopixelsmod.block.custom.PanelRole;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelBlock;
import net.bullettrain.xenopixelsmod.block.entity.PilotSeatBlockEntity;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Versioned v2 snapshot: requested vs applied throttle, attitude, flap, surface mode, power,
 * and which control-surface roles are actually linked.
 */
public record GuidanceV2Bus(
        BlockPos hostPos,
        GuidanceV2SurfaceMode mode,
        double requestedThrottle,
        double appliedThrottle,
        double yawDeg,
        double pitchDeg,
        double rollDeg,
        double flap,
        double flapTarget,
        boolean autoFlap,
        boolean airBrake,
        AeroBus.PowerTier powerTier,
        int storedEnergy,
        boolean hasPitch,
        boolean hasRoll,
        boolean hasYaw,
        boolean hasBrake,
        int linkedSurfaces
) {
    public static GuidanceV2Bus of(AeroControlHost host) {
        GuidanceV2SurfaceMode mode = GuidanceV2SurfaceMode.THRUST_AND_FLAPS;
        if (host instanceof ShipVlsGuidanceBlockEntity computer) {
            mode = computer.guidanceV2().surfaceMode();
        } else if (host instanceof PilotSeatBlockEntity seat) {
            mode = seat.guidanceV2().surfaceMode();
        }
        AeroStateSnapshot snap = AeroStateSnapshot.of(host.hostPos(), host.aeroBus());
        return of(snap, mode, scan(host.getLevel(), host.getLinkedPanels()));
    }

    public static GuidanceV2Bus of(AeroStateSnapshot snap, GuidanceV2SurfaceMode mode, Flags flags) {
        if (snap == null) {
            return empty(BlockPos.ZERO, mode, flags);
        }
        Flags safe = flags == null ? Flags.EMPTY : flags;
        GuidanceV2SurfaceMode resolved = mode == null ? GuidanceV2SurfaceMode.THRUST_AND_FLAPS : mode;
        return new GuidanceV2Bus(
                snap.controllerPos(),
                resolved,
                snap.throttle(),
                GuidanceV2Core.appliedThrottle(resolved, snap.throttle()),
                snap.yawDeg(),
                snap.pitchDeg(),
                snap.rollDeg(),
                snap.flap(),
                snap.flapTarget(),
                snap.autoFlap(),
                snap.airBrake(),
                snap.powerTier(),
                snap.storedEnergy(),
                safe.hasPitch(),
                safe.hasRoll(),
                safe.hasYaw(),
                safe.hasBrake(),
                safe.count()
        );
    }

    public static GuidanceV2Bus empty(BlockPos pos, GuidanceV2SurfaceMode mode, Flags flags) {
        Flags safe = flags == null ? Flags.EMPTY : flags;
        GuidanceV2SurfaceMode resolved = mode == null ? GuidanceV2SurfaceMode.THRUST_AND_FLAPS : mode;
        return new GuidanceV2Bus(
                pos, resolved, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, false,
                AeroBus.PowerTier.OFFLINE, 0,
                safe.hasPitch(), safe.hasRoll(), safe.hasYaw(), safe.hasBrake(), safe.count());
    }

    public static Flags scan(Level level, Set<BlockPos> linked) {
        if (level == null || linked == null || linked.isEmpty()) {
            return Flags.EMPTY;
        }
        boolean pitch = false;
        boolean roll = false;
        boolean yaw = false;
        boolean brake = false;
        int count = 0;
        for (BlockPos pos : linked) {
            if (pos == null || !level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(WingPanelBlock.ROLE)) {
                continue;
            }
            count++;
            PanelRole role = state.getValue(WingPanelBlock.ROLE);
            switch (role) {
                case PITCH -> pitch = true;
                case ROLL -> roll = true;
                case YAW -> yaw = true;
                case BRAKE -> brake = true;
                default -> {
                }
            }
        }
        return new Flags(pitch, roll, yaw, brake, count);
    }

    public boolean flapsOnly() {
        return mode != null && mode.flapsOnly();
    }

    public boolean missingAttitudeSurfaces() {
        return !hasPitch || !hasRoll || !hasYaw;
    }

    public boolean cannotBleedSpeed() {
        return flapsOnly() && !hasBrake;
    }

    public record Flags(boolean hasPitch, boolean hasRoll, boolean hasYaw, boolean hasBrake, int count) {
        public static final Flags EMPTY = new Flags(false, false, false, false, 0);
    }
}
