package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.combat.Bt3Landing;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Target-relative landing for v2, after the caller validates lock, protection and visibility. */
public final class V2VanishLanding {
    private V2VanishLanding() {}

    public static double range(V2Config.Values cfg, double lockRange) {
        return cfg.vanishUsesLockRange ? lockRange + LockRules.SLACK : cfg.vanishRange;
    }

    public static Vec3 find(Entity fighter, LivingEntity target, int side, V2Config.Values cfg, double lockRange) {
        if (fighter.level() != target.level() || !target.isAlive()
                || fighter.distanceTo(target) > range(cfg, lockRange)) return null;
        return Bt3Landing.vanishLanding(fighter, target, side);
    }
}
