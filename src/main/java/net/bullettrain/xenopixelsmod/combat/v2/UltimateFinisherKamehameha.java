package net.bullettrain.xenopixelsmod.combat.v2;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.TechniqueDispatcher;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;

/** The finisher's owned Kamehameha charge and release. */
public final class UltimateFinisherKamehameha {
    private static final String MARKER = "xenopixelsmod.ultimate_finisher_wave";
    private UltimateFinisherKamehameha() {}

    public static KiWaveEntity start(LivingEntity player, StatsData stats, KiAttackData technique) {
        if (player.level().isClientSide || stats == null || technique == null
                || !"kamehameha".equals(technique.getId()) || !charging(player).isEmpty()) return null;
        // The native dispatcher sets WAVE type, Kamehameha rendering, colors and utility flags.
        if (!TechniqueDispatcher.executeKiAttack(player, player.level(), technique, stats, 0.01f)) return null;
        for (AbstractKiProjectile projectile : charging(player)) {
            if (projectile instanceof KiWaveEntity wave && technique.getId().equals(wave.getTechniqueId())) {
                wave.getPersistentData().putBoolean(MARKER, true);
                return wave;
            }
        }
        return null;
    }

    public static boolean release(LivingEntity player, StatsData stats, KiAttackData technique, KiWaveEntity wave) {
        if (player.level().isClientSide || stats == null || technique == null || wave == null || !owns(wave)) return false;
        List<AbstractKiProjectile> charging = charging(player);
        // DMZ releases all charging projectiles; never release an unrelated charge with ours.
        if (charging.size() != 1 || charging.getFirst() != wave) return false;
        return TechniqueDispatcher.executeKiAttack(player, player.level(), technique, stats, 2.0f) && wave.isFiring();
    }

    public static boolean owns(KiWaveEntity wave) {
        return wave instanceof UltimateFinisherWaveTarget target && target.xenopixels$finisherTarget() >= 0
                || wave.getPersistentData().getBoolean(MARKER);
    }

    public static void target(KiWaveEntity wave, LivingEntity target) {
        if (wave instanceof UltimateFinisherWaveTarget marked) marked.xenopixels$finisherTarget(target.getId());
    }

    /** Called after DMZ's position update, before it tests beam collisions; applies on both sides. */
    public static void align(KiWaveEntity wave) {
        if (!wave.isFiring() || !(wave instanceof UltimateFinisherWaveTarget marked)
                || marked.xenopixels$finisherTarget() < 0 || !(wave.getOwner() instanceof LivingEntity owner)
                || !(wave.level().getEntity(marked.xenopixels$finisherTarget()) instanceof LivingEntity target)
                || !target.isAlive()) return;
        align(wave, target);
    }

    public static void align(KiWaveEntity wave, LivingEntity target) {
        if (wave == null || !wave.isFiring() || target == null || !target.isAlive()
                || !(wave.getOwner() instanceof LivingEntity owner)) return;
        var aim = UltimateFinisherBeamAim.toward(owner.getEyePosition(), target.getBoundingBox().getCenter(), owner.getLookAngle());
        wave.setContinuousFollow(false);
        wave.setCastOffsets(0, 0, 0);
        wave.setPos(aim.origin());
        wave.setYRot(aim.yaw()); wave.setXRot(aim.pitch());
        if (wave instanceof net.bullettrain.xenopixelsmod.combat.technique.KiFixedAim fixed)
            fixed.xenopixels$setFixedAim(aim.yaw(), aim.pitch());
    }

    private static List<AbstractKiProjectile> charging(LivingEntity player) {
        return player.level().getEntitiesOfClass(AbstractKiProjectile.class, player.getBoundingBox().inflate(30),
                projectile -> !projectile.isFiring() && projectile.getOwner() != null
                        && player.getUUID().equals(projectile.getOwner().getUUID()));
    }
}
