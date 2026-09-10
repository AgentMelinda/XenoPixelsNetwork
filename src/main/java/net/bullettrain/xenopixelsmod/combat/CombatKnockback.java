package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProtection;
import net.bullettrain.xenopixelsmod.event.DmzMasterProtection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The one place XenoPixels combat pushes a victim around.
 *
 * <p>DragonMineZ masters are already shielded from being attacked, damaged and knocked back
 * ({@link DmzMasterProtection}) -- but that last guard listens on {@code LivingKnockBackEvent}, which
 * only fires for knockback that goes through vanilla's {@code LivingEntity.knockback}. Our BT3 moves
 * never call it: they write {@code setDeltaMovement} directly for the launch arcs and slams, which
 * fires no event and sailed straight past the guard, so a charged kick still sent a master flying
 * out of its training spot.
 *
 * <p>Routing those writes through here closes that hole once instead of at twenty call sites, and it
 * owns the {@code hurtMarked} / {@code hasImpulse} flags that used to sit loose beside each one, so a
 * blocked impulse cannot leave half of itself applied.
 *
 * <p>Only impulses applied to a <em>victim</em> belong here. Moving the attacker, a clone or a
 * projectile is not knockback and must keep calling {@code setDeltaMovement} directly.
 */
public final class CombatKnockback {

    private CombatKnockback() {
    }

    /**
     * Whether this entity may be pushed by our combat at all.
     *
     * <p>Masters only, and only while the guard is on. Saga NPCs are explicitly not masters --
     * {@link DmzMasterProtection#isDmzMaster} excludes {@code dragonminez:saga_*} -- so quest
     * enemies still take every launcher they always did.
     */
    public static boolean canKnockBack(Entity victim) {
        if (victim == null) return false;
        if (!NpcCombatProtection.isKnockable(victim)) return false;
        if (!XenoServerConfig.protectMastersFromCombatKnockback) return true;
        return !DmzMasterProtection.isDmzMaster(victim);
    }

    /** Replaces the victim's velocity outright, as the launchers and slams do. */
    public static void set(Entity victim, Vec3 velocity) {
        if (!canKnockBack(victim)) return;
        victim.setDeltaMovement(velocity);
        mark(victim);
    }

    /** Adds to the victim's velocity, as the lighter contact hits do. */
    public static void add(Entity victim, Vec3 velocity) {
        if (!canKnockBack(victim)) return;
        victim.setDeltaMovement(victim.getDeltaMovement().add(velocity));
        mark(victim);
    }

    /**
     * Flags a velocity change for the client.
     *
     * <p>{@code hurtMarked} is what makes the server send the new velocity at all; without it a
     * launch is invisible until the next regular position sync.
     */
    private static void mark(Entity victim) {
        victim.hurtMarked = true;
        victim.hasImpulse = true;
    }
}
