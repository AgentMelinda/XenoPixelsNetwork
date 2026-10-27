package net.bullettrain.xenopixelsmod.space.event;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Per-player delivered-snapshot baseline; failed recipients remain pending without rebroadcasting to healthy peers. */
public final class StellarSnapshotDeliveryLedger<T> {
    private final Map<UUID, T> delivered = new HashMap<>();

    public boolean needsDelivery(UUID playerId, T current) {
        return playerId != null && !Objects.equals(delivered.get(playerId), current);
    }

    public void markDelivered(UUID playerId, T current) {
        if (playerId == null || current == null) throw new IllegalArgumentException("player and snapshot are required");
        delivered.put(playerId, current);
    }

    public boolean hasPending(Collection<UUID> players, T current) {
        for (UUID player : players) if (needsDelivery(player, current)) return true;
        return false;
    }

    public void retainOnline(Collection<UUID> onlinePlayers) {
        delivered.keySet().removeIf(player -> !onlinePlayers.contains(player));
    }

    public void remove(UUID playerId) {
        if (playerId != null) delivered.remove(playerId);
    }
}
