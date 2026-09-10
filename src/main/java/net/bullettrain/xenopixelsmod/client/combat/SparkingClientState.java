package net.bullettrain.xenopixelsmod.client.combat;

import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which players this client should draw a Sparking aura on.
 *
 * <p>Needed because Minecraft does not sync a player's mob effects to the clients tracking them —
 * only to that player. Asking {@code hasEffect(SPARKING)} therefore answers correctly for yourself
 * and always {@code false} for everyone else, which would leave the aura invisible in exactly the
 * situation it matters most.
 *
 * <p>Keyed by entity id rather than UUID because the render path has the entity in hand, and the id
 * is what {@code sendToTrackingEntityAndSelf} scopes on.
 */
public final class SparkingClientState {

    private static final Set<Integer> SPARKING = ConcurrentHashMap.newKeySet();

    private SparkingClientState() {
    }

    public static void set(int entityId, boolean sparking) {
        if (sparking) {
            SPARKING.add(entityId);
        } else {
            SPARKING.remove(entityId);
        }
    }

    public static boolean isSparking(Entity entity) {
        return entity != null && SPARKING.contains(entity.getId());
    }

    /** Dropped on disconnect so a rejoin cannot inherit a stale aura. */
    public static void clear() {
        SPARKING.clear();
    }
}
