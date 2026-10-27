package net.bullettrain.xenopixelsmod.client.combat;

import java.util.LinkedHashMap;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.combat.HakaiSweepSync;
import net.minecraft.resources.ResourceLocation;

/** Connection-scoped revision memory, independent of a client's replaceable world/render state. */
final class HakaiSweepSyncHistory {
    private record Target(ResourceLocation dimension, UUID uuid) {}
    private final LinkedHashMap<Target, Long> revisions = new LinkedHashMap<>();
    private static final int MAX_TARGETS = 8192;

    boolean accept(HakaiSweepSync packet) {
        Target target = new Target(packet.dimension(), packet.target());
        Long previous = revisions.get(target);
        if (previous != null && packet.revision() <= previous) return false;
        revisions.remove(target);
        revisions.put(target, packet.revision());
        if (revisions.size() > MAX_TARGETS) revisions.pollFirstEntry();
        return true;
    }

    void clear() { revisions.clear(); }
}
