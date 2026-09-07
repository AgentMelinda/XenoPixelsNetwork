package net.bullettrain.xenopixelsmod.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Write access to DragonMineZ's client-side lock-on target.
 *
 * <p>{@code LockOnEvent} keeps {@code lockedTarget} private and exposes only
 * {@code toggleLock()} — which picks its own target by scanning in front of the player and
 * requires the {@code kisense} skill — and {@code unlock()}. There is no way to say "lock onto
 * <em>this</em> entity", and no server-side entry point at all, so forcing a player's reticle
 * onto a chosen target needs this accessor plus an S2C packet.
 *
 * <p>{@code markerVisible} is written too: {@code unlock()} clears both, and the HUD layer draws
 * only when both are set, so a target written without it would lock silently with no reticle
 * until the next client tick recomputed the marker.
 */
@Mixin(targets = "com.dragonminez.client.events.LockOnEvent", remap = false)
public interface DmzLockOnAccessor {
    @Accessor("lockedTarget")
    static void xenopixels$setLockedTarget(LivingEntity target) {
        throw new AssertionError();
    }

    @Accessor("lockedTarget")
    static LivingEntity xenopixels$getLockedTarget() {
        throw new AssertionError();
    }

    @Accessor("markerVisible")
    static void xenopixels$setMarkerVisible(boolean visible) {
        throw new AssertionError();
    }
}
