package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Owns movement along the finisher's throw arc while the victim remains held. */
public final class UltimateFinisherThrow {
    private UltimateFinisherThrow() {}

    public static boolean step(LivingEntity target, Vec3 origin, Vec3 direction, int tick) {
        if (!CombatKnockback.canKnockBack(target)) return false;
        double[] offset = UltimateFinisherRules.throwOffset(tick);
        Vec3 next = origin.add(direction.scale(offset[0])).add(0, offset[1], 0);
        Vec3 velocity = next.subtract(target.position());
        if (!target.level().noCollision(target, target.getBoundingBox().expandTowards(velocity))) return false;
        CombatKnockback.set(target, velocity);
        if (target instanceof Mob mob && mob.isNoAi()) {
            // NoAI also disables vanilla travel, so the held mob cannot consume this velocity.
            // Move once with collision handling; clear the impulse so later ticks cannot move twice.
            target.move(MoverType.SELF, velocity);
            CombatKnockback.set(target, Vec3.ZERO);
        }
        target.resetFallDistance();
        return true;
    }
}
