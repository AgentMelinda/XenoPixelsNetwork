package net.bullettrain.xenopixelsmod.aero;

import net.bullettrain.xenopixelsmod.block.custom.ShipThrusterBlock;
import net.bullettrain.xenopixelsmod.block.entity.ShipThrusterBlockEntity;
import net.bullettrain.xenopixelsmod.compat.thruster.ExternalThrusterCompat;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
     * Visit every loaded block entity within {@code radius} of {@code origin}, cheaply.
     *
     * <p>The obvious way to find "the nearest X block" is to walk every position in a cube and
     * ask the level what is there. That cost is cubic in the radius and completely divorced from
     * how much is actually out there: a radius of 48 is nine hundred thousand lookups to find a
     * handful of block entities, which on a right-click is a visible server stall. Chunks already
     * keep a map of exactly the block entities they contain, so walking those maps costs what the
     * answer costs instead of what the search volume costs.
     *
     * <p>Unloaded chunks are skipped rather than generated — a search for something to link to
     * has no business dragging terrain into memory.
     *
     * <p>The visitor must not place or break blocks: it is iterating a chunk's live block-entity
     * map, and mutating the world during that walk is a concurrent modification. Collect the
     * positions and act afterwards, as {@code AeroFlightCore} does for the same reason.
     */
    public static void forEachNearbyBlockEntity(@Nullable Level level, BlockPos origin, int radius,
                                                java.util.function.BiConsumer<BlockPos, BlockEntity> visitor) {
        if (level == null || radius < 0) return;
        int minY = origin.getY() - radius;
        int maxY = origin.getY() + radius;
        int minChunkX = (origin.getX() - radius) >> 4;
        int maxChunkX = (origin.getX() + radius) >> 4;
        int minChunkZ = (origin.getZ() - radius) >> 4;
        int maxChunkZ = (origin.getZ() + radius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!(level.getChunk(chunkX, chunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false)
                        instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) {
                    continue;
                }
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (pos.getY() < minY || pos.getY() > maxY) continue;
                    if (Math.abs(pos.getX() - origin.getX()) > radius) continue;
                    if (Math.abs(pos.getZ() - origin.getZ()) > radius) continue;
                    visitor.accept(pos, entry.getValue());
                }
            }
        }
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
     * Claim every compatible engine near {@code owner} that is on the same ship.
     *
     * <p>The same-ship test is what stops a controller on one hull grabbing the engines of a
     * vessel parked beside it. When the owner is not on a ship at all the test is skipped, so
     * a rig built on the ground still pairs.
     *
     * <p>Ownership is recorded on the engine as the owner's block position, which is why any
     * host with a position — a flight controller or a pilot seat — can own engines.
     *
     * @param paired mutated in place with the newly claimed positions
     * @return how many engines were newly claimed
     */
    public static int pairNearby(@Nullable Level level, BlockPos owner, int radius,
                                 Collection<BlockPos> paired) {
        if (level == null || level.isClientSide) return 0;
        int added = 0;
        int r = Math.max(radius, 12);
        long ownerShipId = shipIdAt(level, owner);

        for (BlockPos candidate : BlockPos.betweenClosed(
                owner.offset(-r, -8, -r), owner.offset(r, 12, r))) {
            BlockState candidateState = level.getBlockState(candidate);
            BlockEntity candidateEntity = level.getBlockEntity(candidate);
            ShipThrusterBlockEntity thruster =
                    candidateEntity instanceof ShipThrusterBlockEntity own ? own : null;
            if (thruster == null && !ExternalThrusterCompat.isCompatible(candidateState)) continue;
            if (ownerShipId >= 0 && shipIdAt(level, candidate) != ownerShipId) continue;

            BlockPos immutable = candidate.immutable();
            if (paired.add(immutable)) added++;
            if (thruster != null) thruster.setPairedGuidance(owner);
        }
        return added;
    }

    /**
     * Link exactly one engine to this owner — the ship target tool's explicit per-block linker,
     * as opposed to {@link #pairNearby}'s radius sweep.
     *
     * @return true if the target is a compatible engine and was newly added to {@code paired}
     */
    public static boolean pairOne(@Nullable Level level, BlockPos owner, BlockPos target,
                                  Collection<BlockPos> paired) {
        if (level == null || level.isClientSide) return false;
        BlockPos immutable = target.immutable();
        if (level.getBlockEntity(immutable) instanceof ShipThrusterBlockEntity thruster) {
            boolean added = paired.add(immutable);
            thruster.setPairedGuidance(owner);
            return added;
        }
        if (ExternalThrusterCompat.isCompatible(level.getBlockState(immutable))) {
            return paired.add(immutable);
        }
        return false;
    }

    /**
     * Unlink exactly one engine from this owner, matching the ownership check
     * {@link #clearPaired} uses — a thruster already reclaimed by another controller is not
     * released out from under it.
     *
     * @return true if the target was paired and is now removed
     */
    public static boolean unpairOne(@Nullable Level level, BlockPos owner, BlockPos target,
                                    Collection<BlockPos> paired) {
        if (!paired.remove(target)) return false;
        if (level != null && !level.isClientSide) {
            try {
                if (level.getBlockEntity(target) instanceof ShipThrusterBlockEntity thruster) {
                    if (owner.equals(thruster.getPairedGuidance())) {
                        thruster.setPairedGuidance(null);
                        thruster.setGuidanceOwned(false);
                    }
                } else {
                    ExternalThrusterCompat.setThrottle(level, target, 0.0);
                }
            } catch (Throwable ignored) {
                // The pairing is already dropped above; a bad neighbor must not undo that.
            }
        }
        return true;
    }

    /**
     * Release every engine this owner claimed and forget them.
     *
     * <p>Only engines still pointing at this owner are released: a thruster that has since been
     * claimed by another controller belongs to that one now, and stealing it back would leave
     * the other controller commanding an engine it no longer owns.
     *
     * @return how many pairings were dropped
     */
    public static int clearPaired(@Nullable Level level, BlockPos owner, Collection<BlockPos> paired) {
        int count = paired.size();
        if (level != null && !level.isClientSide) {
            for (BlockPos pos : new ArrayList<>(paired)) {
                try {
                    if (!level.hasChunkAt(pos)) continue;
                    if (level.getBlockEntity(pos) instanceof ShipThrusterBlockEntity thruster) {
                        if (owner.equals(thruster.getPairedGuidance())) {
                            thruster.setPairedGuidance(null);
                            thruster.setGuidanceOwned(false);
                        }
                    } else {
                        ExternalThrusterCompat.setThrottle(level, pos, 0.0);
                    }
                } catch (Throwable ignored) {
                    // One bad neighbor must not abort the sweep.
                }
            }
        }
        paired.clear();
        return count;
    }

    /** Sub-level id at a position, or -1 when it is not on a ship (or Sable is unhappy). */
    private static long shipIdAt(Level level, BlockPos pos) {
        try {
            if (level instanceof ServerLevel serverLevel) {
                ServerSubLevel loaded = VsShipHelper.getLoadedShipAt(serverLevel, pos);
                if (loaded != null) return VsShipHelper.getShipId(loaded);
            }
            SubLevelAccess any = VsShipHelper.getShipAt(level, pos);
            return any == null ? -1L : VsShipHelper.getShipId(any);
        } catch (Throwable ignored) {
            return -1L;
        }
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
