package net.bullettrain.xenopixelsmod.aero;

import net.bullettrain.xenopixelsmod.block.custom.ShipThrusterBlock;
import net.bullettrain.xenopixelsmod.block.entity.ShipThrusterBlockEntity;
import net.bullettrain.xenopixelsmod.compat.thruster.ExternalThrusterCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Typed, health-aware view over a controller's paired devices.
 *
 * <p>This deliberately does <b>not</b> own storage. The guidance block entity already
 * persists its pairings under the {@code PairedThrusters} NBT key and the missile code reads
 * that set directly; duplicating it into a second store would break the save contract and
 * risk the two copies diverging. The link manager surveys that existing set and adds the
 * typing, thrust geometry, and health information the flight stack needs.
 */
public final class AeroLinkManager {

    public enum LinkType {
        /** This mod's own thruster block. */
        OWN_THRUSTER,
        /** A thruster from another mod recognized by {@link ExternalThrusterCompat}. */
        EXTERNAL_ENGINE,
        /** Paired position no longer holds a usable engine. */
        MISSING
    }

    /**
     * One paired device.
     *
     * @param pos    block position in the same space as the controller (ship-local on a hull)
     * @param type   what kind of engine is actually there right now
     * @param thrust direction thrust is applied in, or null if unknown
     * @param maxForce per-engine force scale; 0 for external engines whose scale is unknown
     */
    public record Link(BlockPos pos, LinkType type, @Nullable Direction thrust, double maxForce) {
        public boolean healthy() {
            return type != LinkType.MISSING;
        }
    }

    private AeroLinkManager() {
    }

    /**
     * Classify every paired position. Positions in unloaded chunks are reported
     * {@link LinkType#MISSING} rather than force-loading the world — the caller decides
     * whether that is worth acting on.
     */
    public static List<Link> survey(@Nullable Level level, Collection<BlockPos> paired) {
        List<Link> links = new ArrayList<>(paired.size());
        if (level == null) {
            for (BlockPos pos : paired) {
                links.add(new Link(pos.immutable(), LinkType.MISSING, null, 0.0));
            }
            return links;
        }
        for (BlockPos pos : paired) {
            links.add(classify(level, pos.immutable()));
        }
        return links;
    }

    private static Link classify(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return new Link(pos, LinkType.MISSING, null, 0.0);
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof ShipThrusterBlockEntity thruster) {
            BlockState state = thruster.getBlockState();
            Direction thrust = state.hasProperty(ShipThrusterBlock.FACING)
                    ? state.getValue(ShipThrusterBlock.FACING).getOpposite()
                    : null;
            return new Link(pos, LinkType.OWN_THRUSTER, thrust, thruster.getMaxForce());
        }
        BlockState state = level.getBlockState(pos);
        if (ExternalThrusterCompat.isCompatible(state)) {
            Direction facing = ExternalThrusterCompat.thrustDirection(state);
            return new Link(pos, LinkType.EXTERNAL_ENGINE, facing, 0.0);
        }
        return new Link(pos, LinkType.MISSING, null, 0.0);
    }

    /**
     * Survey the paired set and publish the result onto the bus.
     *
     * <p>Lives here rather than on the host block entity so that {@link AeroBus}'s mutators
     * can stay package-private — only the aero package writes controller state.
     *
     * @return the surveyed links, for callers that need the detail (the mixer does)
     */
    public static List<Link> refresh(@Nullable Level level, Collection<BlockPos> paired, AeroBus bus) {
        List<Link> links = survey(level, paired);
        if (bus != null) bus.setLinkCounts(links.size(), healthyCount(links));
        return links;
    }

    public static int healthyCount(Collection<Link> links) {
        int healthy = 0;
        for (Link link : links) {
            if (link.healthy()) healthy++;
        }
        return healthy;
    }

    /**
     * Drop commanded thrust on every link. Used when disengaging flight, losing power, or
     * on emergency stop. External engines are throttled to zero through their compat hook;
     * own thrusters release guidance ownership, which already zeroes their physics entry.
     */
    public static void releaseAll(@Nullable Level level, Collection<BlockPos> paired) {
        if (level == null || level.isClientSide) return;
        for (BlockPos pos : paired) {
            try {
                if (!level.hasChunkAt(pos)) continue;
                if (level.getBlockEntity(pos) instanceof ShipThrusterBlockEntity thruster) {
                    thruster.setGuidanceOwned(false);
                } else {
                    ExternalThrusterCompat.setThrottle(level, pos, 0.0);
                }
            } catch (Throwable ignored) {
                // A single bad neighbor must not abort the release sweep.
            }
        }
    }
}
