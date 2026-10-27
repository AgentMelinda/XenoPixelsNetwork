package net.bullettrain.xenopixelsmod.space.physics;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks Sable bodies while PlotBodyBuilder jobs are installing data and owner tags. */
@EventBusSubscriber(modid = net.bullettrain.xenopixelsmod.XenoPixelsMod.MOD_ID)
public final class PlotBodyBuildRegistry {
    private static final Store IN_FLIGHT = new Store();

    private PlotBodyBuildRegistry() {}

    public static void register(UUID bodyId, ServerLevel level) {
        if (level != null) IN_FLIGHT.register(level.getServer(), bodyId, level.dimension());
    }

    public static void unregister(UUID bodyId, ServerLevel level) {
        if (level != null) IN_FLIGHT.unregister(level.getServer(), bodyId);
    }

    public static boolean isInFlight(UUID bodyId, MinecraftServer server) {
        return IN_FLIGHT.contains(server, bodyId);
    }

    /**
     * Drop registrations for bodies which Sable no longer resolves. A census can then report an unexpected
     * still-listed, untagged body as unavailable instead of silently suppressing it forever. A lookup exception
     * keeps the registration: an unreadable container is not evidence that a build body disappeared.
     */
    public static int pruneMissingOrRemoved(ServerLevel level, ServerSubLevelContainer container) {
        if (level == null || container == null || level.getServer() == null) return 0;
        int removed = 0;
        for (var entry : IN_FLIGHT.registrations(level.getServer())) {
            if (!level.dimension().equals(entry.dimension())) continue;
            boolean stale;
            try {
                Object resolved = container.getSubLevel(entry.bodyId());
                stale = !(resolved instanceof ServerSubLevel body) || body.isRemoved();
            } catch (RuntimeException lookupFailure) {
                continue;
            }
            if (stale && IN_FLIGHT.unregister(level.getServer(), entry.bodyId(), entry.dimension())) removed++;
        }
        return removed;
    }

    /** A final safety net for failed/cancelled jobs that outlive their server's normal stop cleanup. */
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        if (event != null) IN_FLIGHT.forget(event.getServer());
    }

    /** Package-private pure state store: identity isolation and stop cleanup can be tested without Minecraft boot. */
    static final class Store {
        private final Map<IdentityKey, Map<UUID, ResourceKey<Level>>> byServer = new ConcurrentHashMap<>();

        void register(Object server, UUID bodyId, ResourceKey<Level> dimension) {
            if (server == null || bodyId == null || dimension == null) return;
            byServer.computeIfAbsent(new IdentityKey(server), ignored -> new ConcurrentHashMap<>())
                    .put(bodyId, dimension);
        }

        void unregister(Object server, UUID bodyId) {
            if (server == null || bodyId == null) return;
            IdentityKey key = new IdentityKey(server);
            Map<UUID, ResourceKey<Level>> registrations = byServer.get(key);
            if (registrations == null) return;
            registrations.remove(bodyId);
        }

        boolean unregister(Object server, UUID bodyId, ResourceKey<Level> dimension) {
            if (server == null || bodyId == null || dimension == null) return false;
            IdentityKey key = new IdentityKey(server);
            Map<UUID, ResourceKey<Level>> registrations = byServer.get(key);
            if (registrations == null || !registrations.remove(bodyId, dimension)) return false;
            return true;
        }

        boolean contains(Object server, UUID bodyId) {
            if (server == null || bodyId == null) return false;
            Map<UUID, ResourceKey<Level>> registrations = byServer.get(new IdentityKey(server));
            return registrations != null && registrations.containsKey(bodyId);
        }

        List<Registration> registrations(Object server) {
            if (server == null) return List.of();
            Map<UUID, ResourceKey<Level>> registrations = byServer.get(new IdentityKey(server));
            if (registrations == null || registrations.isEmpty()) return List.of();
            List<Registration> snapshot = new ArrayList<>(registrations.size());
            registrations.forEach((bodyId, dimension) -> snapshot.add(new Registration(bodyId, dimension)));
            return List.copyOf(snapshot);
        }

        void forget(Object server) {
            if (server != null) byServer.remove(new IdentityKey(server));
        }
    }

    record Registration(UUID bodyId, ResourceKey<Level> dimension) {}

    /** Server references use identity, so two live server objects never share a UUID namespace. */
    private static final class IdentityKey {
        private final Object server;
        private final int hash;

        private IdentityKey(Object server) {
            this.server = server;
            this.hash = System.identityHashCode(server);
        }

        @Override public boolean equals(Object other) {
            return other instanceof IdentityKey key && server == key.server;
        }

        @Override public int hashCode() { return hash; }
    }
}
