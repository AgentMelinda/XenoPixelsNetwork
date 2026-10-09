package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.combat.Bt3Landing;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Cross the target on the approach line, including at melee range and at a different height. */
public final class V2DragonDashLanding {
    private V2DragonDashLanding() {}

    public static Vec3 find(Entity fighter, LivingEntity target, double range) {
        if (fighter.level() != target.level() || !target.isAlive() || fighter.distanceTo(target) > range) return null;
        Vec3 direction = target.position().subtract(fighter.position()).multiply(1, 0, 1);
        if (direction.lengthSqr() < 1e-6) {
            double yaw = Math.toRadians(target.yBodyRot);
            direction = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
        }
        double gap = Math.max(1.6, (fighter.getBbWidth() + target.getBbWidth()) * 0.5 + 0.5);
        Vec3 landing = target.position().add(direction.normalize().scale(gap));
        var box = fighter.getBoundingBox().move(landing.subtract(fighter.position()));
        return fighter.level().getWorldBorder().isWithinBounds(box) && Bt3Landing.isSpotOpen(fighter, landing)
                ? landing : null;
    }
}
