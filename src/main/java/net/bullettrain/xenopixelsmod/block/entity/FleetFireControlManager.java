package net.bullettrain.xenopixelsmod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Lightweight registry for loaded guidance computers. Channels are scoped to a
 * dimension and only loaded computers participate, so fleet operations never
 * force-load every vessel merely to discover it.
 */
public final class FleetFireControlManager {
    private static final Map<ServerLevel, LevelState> LEVELS = new WeakHashMap<>();

    private FleetFireControlManager() {
    }

    public static void register(ShipVlsGuidanceBlockEntity guidance) {
        if (!(guidance.getLevel() instanceof ServerLevel level)) return;
        state(level).register(guidance);
    }

    public static void unregister(ShipVlsGuidanceBlockEntity guidance) {
        if (!(guidance.getLevel() instanceof ServerLevel level)) return;
        LevelState state = LEVELS.get(level);
        if (state != null) state.unregister(guidance.getBlockPos());
    }

    /** Propagates the source target to every loaded computer on its channel. */
    public static int broadcastTarget(ShipVlsGuidanceBlockEntity source) {
        if (!(source.getLevel() instanceof ServerLevel level) || source.getFleetChannel() <= 0
                || source.getTarget() == null) return 0;
        LevelState state = state(level);
        state.register(source);
        return state.broadcastTarget(source);
    }

    /** Queues one launch per loaded vessel, staggered to avoid a same-tick spike. */
    public static int queueSalvo(ShipVlsGuidanceBlockEntity source) {
        if (!(source.getLevel() instanceof ServerLevel level)) return 0;
        if (source.getFleetChannel() <= 0) return source.firePulse(level) > 0 ? 1 : 0;
        LevelState state = state(level);
        state.register(source);
        return state.queueSalvo(source, level.getGameTime());
    }

    public static void tick(ServerLevel level) {
        LevelState state = LEVELS.get(level);
        if (state != null) state.tick(level);
    }

    // --- Channel ownership (co-op / party) -------------------------------------------
    //
    // Separate from the live vessel registry above: the registry is rebuilt as computers load,
    // but who is *allowed* to steer a channel is a durable fact that must survive a restart, so
    // it lives in {@link FleetChannelSavedData} keyed by dimension.
    // See GuidanceControlPacket for the claim / verify / release policy built on these.

    public static FleetChannelSavedData.ChannelAuthority getChannelAuthority(ServerLevel level, int channel) {
        return FleetChannelSavedData.get(level.getServer()).getAuthority(dimId(level), channel);
    }

    public static void setChannelAuthority(ServerLevel level, int channel, UUID owner, UUID party) {
        FleetChannelSavedData.get(level.getServer()).setAuthority(dimId(level), channel, owner, party);
    }

    public static void clearChannelAuthority(ServerLevel level, int channel) {
        FleetChannelSavedData.get(level.getServer()).clearAuthority(dimId(level), channel);
    }

    private static String dimId(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static LevelState state(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, ignored -> new LevelState());
    }

    private static final class LevelState {
        private final Map<Integer, LinkedHashSet<BlockPos>> channels = new HashMap<>();
        private final Map<BlockPos, Integer> registeredChannels = new HashMap<>();
        private final PriorityQueue<ScheduledLaunch> launches = new PriorityQueue<>(
                Comparator.comparingLong(ScheduledLaunch::dueTick));
        private final Map<Integer, Long> lastSalvoOrderTick = new HashMap<>();
        private long lastTick = Long.MIN_VALUE;

        void register(ShipVlsGuidanceBlockEntity guidance) {
            BlockPos pos = guidance.getBlockPos().immutable();
            int channel = guidance.getFleetChannel();
            Integer old = registeredChannels.put(pos, channel);
            if (old != null && old != channel) removeFromChannel(old, pos);
            if (channel > 0) channels.computeIfAbsent(channel, ignored -> new LinkedHashSet<>()).add(pos);
        }

        void unregister(BlockPos pos) {
            Integer old = registeredChannels.remove(pos);
            if (old != null) removeFromChannel(old, pos);
        }

        int broadcastTarget(ShipVlsGuidanceBlockEntity source) {
            BlockPos target = source.getTarget();
            if (target == null) return 0;
            int changed = 0;
            for (ShipVlsGuidanceBlockEntity guidance : loadedMembers(source)) {
                if (guidance == source || target.equals(guidance.getTarget())) continue;
                guidance.applyFleetTarget(target);
                changed++;
            }
            return changed;
        }

        int queueSalvo(ShipVlsGuidanceBlockEntity source, long now) {
            Long previousOrder = lastSalvoOrderTick.put(source.getFleetChannel(), now);
            if (previousOrder != null && previousOrder == now) return 0;
            broadcastTarget(source);
            List<ShipVlsGuidanceBlockEntity> members = loadedMembers(source);
            members.sort(Comparator.comparingLong(g -> g.getBlockPos().asLong()));

            Set<String> vessels = new HashSet<>();
            int queued = 0;
            int interval = source.getSalvoIntervalTicks();
            for (ShipVlsGuidanceBlockEntity guidance : members) {
                if (guidance.getTarget() == null || guidance.rangeLimitMessage() != null) continue;
                if (!vessels.add(guidance.fleetVesselKey())) continue;
                launches.add(new ScheduledLaunch(now + (long) queued * interval,
                        guidance.getFleetChannel(), guidance.getBlockPos().immutable()));
                queued++;
            }
            return queued;
        }

        List<ShipVlsGuidanceBlockEntity> loadedMembers(ShipVlsGuidanceBlockEntity source) {
            if (!(source.getLevel() instanceof ServerLevel level)) return List.of();
            LinkedHashSet<BlockPos> positions = channels.get(source.getFleetChannel());
            if (positions == null || positions.isEmpty()) return List.of(source);

            List<ShipVlsGuidanceBlockEntity> result = new ArrayList<>(positions.size());
            for (BlockPos pos : List.copyOf(positions)) {
                if (!level.hasChunkAt(pos)) continue;
                if (level.getBlockEntity(pos) instanceof ShipVlsGuidanceBlockEntity guidance
                        && guidance.getFleetChannel() == source.getFleetChannel()) {
                    result.add(guidance);
                } else {
                    unregister(pos);
                }
            }
            if (!result.contains(source)) result.add(source);
            return result;
        }

        void tick(ServerLevel level) {
            long now = level.getGameTime();
            if (lastTick == now) return;
            lastTick = now;
            while (!launches.isEmpty() && launches.peek().dueTick() <= now) {
                ScheduledLaunch launch = launches.poll();
                if (!level.hasChunkAt(launch.pos())) continue;
                if (level.getBlockEntity(launch.pos()) instanceof ShipVlsGuidanceBlockEntity guidance
                        && guidance.getFleetChannel() == launch.channel()
                        && guidance.getTarget() != null
                        && guidance.rangeLimitMessage() == null) {
                    guidance.firePulse(level);
                }
            }
        }

        private void removeFromChannel(int channel, BlockPos pos) {
            if (channel <= 0) return;
            Set<BlockPos> positions = channels.get(channel);
            if (positions == null) return;
            positions.remove(pos);
            if (positions.isEmpty()) channels.remove(channel);
        }
    }

    private record ScheduledLaunch(long dueTick, int channel, BlockPos pos) {
    }
}
