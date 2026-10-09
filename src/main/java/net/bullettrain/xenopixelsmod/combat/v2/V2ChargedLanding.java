package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.combat.Bt3Landing;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Charged kicks land behind the body; charged punches land in front. */
public final class V2ChargedLanding {
    private V2ChargedLanding() {}

    public static Vec3 find(Entity fighter, LivingEntity target, boolean kick) {
        if (fighter.level() != target.level() || !target.isAlive()) return null;
        double yaw = Math.toRadians(target.yBodyRot);
        double side = kick ? -1 : 1;
        double gap = Math.max(1.6, (target.getBbWidth() + fighter.getBbWidth()) * 0.5 + 0.5);
        Vec3 dest = target.position().add(-Math.sin(yaw) * side * gap, 0, Math.cos(yaw) * side * gap);
        return Bt3Landing.isSpotOpen(fighter, dest) && fighter.level().getWorldBorder().isWithinBounds(
                fighter.getBoundingBox().move(dest.subtract(fighter.position()))) ? dest : null;
    }
}
