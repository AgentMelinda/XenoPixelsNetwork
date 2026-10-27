package net.bullettrain.xenopixelsmod.space;

import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-side collision suppression for the short interval immediately after a dimension handoff. */
public final class HandoffIntangibility {
    /** 0.6 seconds at the normal 20 server ticks per second. */
    public static final long DURATION_TICKS = 12L;

    public record Lease(boolean suppressCollisions, boolean restoreNoPhysics, long expiresAtTick) {}
    private record Active(Entity entity, Lease lease) {}

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private HandoffIntangibility() {}

    static Lease begin(boolean alreadyNoPhysics, long tick) {
        return new Lease(!alreadyNoPhysics, alreadyNoPhysics, tick + DURATION_TICKS);
    }

    static Lease renew(Lease previous, long tick) {
        return begin(previous.restoreNoPhysics(), tick);
    }

    static boolean expired(Lease lease, long tick) {
        return tick >= lease.expiresAtTick();
    }

    /** Mark a newly arrived entity intangible; renews an active lease without losing its original state. */
    public static void mark(Entity entity, long tick) {
        if (entity == null || entity.isRemoved()) return;
        UUID id = entity.getUUID();
        Active active = ACTIVE.get(id);
        Lease lease = active == null ? begin(entity.noPhysics, tick) : renew(active.lease(), tick);
        if (!lease.suppressCollisions()) return;
        entity.noPhysics = true;
        ACTIVE.put(id, new Active(entity, lease));
    }

    /** Restore leases whose 12-tick handoff window has elapsed. Call once per server tick. */
    public static void tick(long tick) {
        Iterator<Map.Entry<UUID, Active>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Active active = iterator.next().getValue();
            if (!expired(active.lease(), tick)) continue;
            restore(active);
            iterator.remove();
        }
    }

    public static void release(Entity entity) {
        if (entity == null) return;
        Active active = ACTIVE.remove(entity.getUUID());
        if (active != null) restore(active);
    }

    /** Restore every still-live entity before a server stops or resets this static manager. */
    public static void clear() {
        for (Active active : ACTIVE.values()) restore(active);
        ACTIVE.clear();
    }

    static int activeCount() { return ACTIVE.size(); }

    private static void restore(Active active) {
        if (!active.entity().isRemoved()) active.entity().noPhysics = active.lease().restoreNoPhysics();
    }
}
