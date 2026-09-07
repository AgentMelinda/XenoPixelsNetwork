package net.bullettrain.xenopixelsmod.client.compat.npc;

import net.bullettrain.xenopixelsmod.mixin.client.DmzLockOnAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Applies a server-forced DragonMineZ lock-on.
 *
 * <p>See {@code DmzLockOnPacket} for why this cannot go through DMZ's own {@code toggleLock()}:
 * that method picks its own target and is gated on the {@code kisense} skill, which a scripted
 * lock has no business requiring.
 */
public final class DmzLockOnClient {
    private DmzLockOnClient() {}

    /** Locks onto the entity with this id, or clears the lock when it is negative or unknown. */
    public static void apply(int targetEntityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (targetEntityId < 0) {
            clear();
            return;
        }
        Entity entity = mc.level.getEntity(targetEntityId);
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living == mc.player) {
            clear();
            return;
        }
        try {
            DmzLockOnAccessor.xenopixels$setLockedTarget(living);
            // unlock() clears both fields and the HUD layer needs both, so a target written
            // without this would lock with no visible reticle until the next client tick.
            DmzLockOnAccessor.xenopixels$setMarkerVisible(true);
        } catch (Throwable ignored) {
            // DMZ absent or its field renamed: a forced lock is a nicety, never a hard failure.
        }
    }

    public static void clear() {
        try {
            DmzLockOnAccessor.xenopixels$setLockedTarget(null);
            DmzLockOnAccessor.xenopixels$setMarkerVisible(false);
        } catch (Throwable ignored) {
        }
    }
}
