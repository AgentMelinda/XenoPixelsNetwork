package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.minecraft.core.BlockPos;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Short-lived, server-thread reservations correlated to exact block positions. When the global position bound is
 * saturated, a body that already owns an edited-body slot accepts the first changed position that tick as its
 * commit. This bounded fallback can pin that admitted body after an unrelated same-tick change, but it cannot admit
 * an additional body or truncate an already-admitted blast.
 */
final class SmallPlanetEditReservationLedger {
    static final int MAX_PENDING_POSITIONS = 65_536;
    private final Map<UUID, LongOpenHashSet> pendingByBody = new HashMap<>();
    private final Set<UUID> committedBodies = new HashSet<>();
    private final Set<UUID> saturatedBodies = new HashSet<>();
    /**
     * Edited-body slots retained while snapshot placements await their final post-event state check.
     * Unlike position reservations, these holds intentionally survive {@link #beginTick(long)}.
     */
    private final Map<UUID, Integer> placementHolds = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private int pendingPositions;

    boolean reserve(long currentTick, UUID body, BlockPos position, int pinnedCount, int cap, boolean alreadyPinned) {
        if (body == null || position == null) return false;
        beginTick(currentTick);
        if (alreadyPinned || committedBodies.contains(body)) return true;
        long key = position.asLong();
        LongOpenHashSet positions = pendingByBody.get(body);
        if (positions != null && positions.contains(key)) return true;
        if (positions == null && !placementHolds.containsKey(body)
                && !SmallPlanetEditedBodyPolicy.canPin(pinnedCount + reservedBodyCount(), cap, false))
            return false;
        if (positions == null) {
            positions = new LongOpenHashSet();
            pendingByBody.put(body, positions);
        }
        // Never truncate a blast after the reservation body has passed the strict edited-body cap.
        // The bounded fallback commits on the first same-tick changed position for this admitted body.
        if (pendingPositions >= MAX_PENDING_POSITIONS) {
            saturatedBodies.add(body);
            return true;
        }
        positions.add(key);
        pendingPositions++;
        return true;
    }

    boolean consume(long currentTick, UUID body, BlockPos position) {
        return remove(currentTick, body, position, true);
    }

    boolean release(long currentTick, UUID body, BlockPos position) {
        return remove(currentTick, body, position, false);
    }

    /** Fast precheck for the LevelChunk RETURN hook; no NBT or plot work is needed for unreserved writes. */
    boolean mayCommit(long currentTick, UUID body, BlockPos position) {
        beginTick(currentTick);
        if (body == null || position == null || committedBodies.contains(body)) return false;
        if (saturatedBodies.contains(body)) return true;
        LongOpenHashSet positions = pendingByBody.get(body);
        return positions != null && positions.contains(position.asLong());
    }

    int pendingCount(long currentTick) {
        beginTick(currentTick);
        return pendingByBody.size();
    }

    int pendingPositionCount(long currentTick) {
        beginTick(currentTick);
        return pendingPositions;
    }

    void holdPlacement(UUID body) {
        if (body != null) placementHolds.merge(body, 1, Integer::sum);
    }

    void releasePlacement(UUID body) {
        if (body == null) return;
        Integer count = placementHolds.get(body);
        if (count == null) return;
        if (count <= 1) placementHolds.remove(body);
        else placementHolds.put(body, count - 1);
    }

    void releaseAllPlacements(UUID body) {
        if (body != null) placementHolds.remove(body);
    }

    private int reservedBodyCount() {
        int count = pendingByBody.size();
        for (UUID body : placementHolds.keySet()) if (!pendingByBody.containsKey(body)) count++;
        return count;
    }

    private boolean remove(long currentTick, UUID body, BlockPos position, boolean committed) {
        beginTick(currentTick);
        if (body == null || position == null) return false;
        LongOpenHashSet positions = pendingByBody.get(body);
        boolean removedPosition = positions != null && positions.remove(position.asLong());
        boolean saturated = saturatedBodies.contains(body);
        if (!removedPosition && !(committed && saturated)) return false;
        if (removedPosition) pendingPositions--;
        if (committed) {
            committedBodies.add(body);
            saturatedBodies.remove(body);
            if (positions != null) {
                pendingPositions -= positions.size();
                pendingByBody.remove(body);
            }
        } else if (positions != null && positions.isEmpty() && !saturated) pendingByBody.remove(body);
        return true;
    }

    private void beginTick(long currentTick) {
        if (tick == currentTick) return;
        tick = currentTick;
        pendingByBody.clear();
        committedBodies.clear();
        saturatedBodies.clear();
        pendingPositions = 0;
    }
}
