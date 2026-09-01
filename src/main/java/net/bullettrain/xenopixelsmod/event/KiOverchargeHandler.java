package net.bullettrain.xenopixelsmod.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiExplosionEntity;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * Scales DMZ KI projectiles when the owner's <em>power release</em> is above the overcharge
 * threshold (default 175%). Size, damage, and explosion radius grow per excess percent,
 * multiplied by {@link XenoServerConfig#kiOverchargeMultiplier}.
 *
 * <p>This is not the charge-cap feature. Holding a technique past DMZ's 175% charge lives in
 * {@code combat.overcharge.ChargeOverchargeManager}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class KiOverchargeHandler {
    private KiOverchargeHandler() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onKiProjectileJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!XenoServerConfig.kiOverchargeEnabled) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof AbstractKiProjectile ki)) return;

        Entity owner = ki.getOwner();
        if (!(owner instanceof ServerPlayer player)) return;

        int release = readPowerRelease(player);
        if (release <= XenoServerConfig.kiOverchargeThreshold) return;

        float sizeScale = XenoServerConfig.kiOverchargeSizeScale(release);
        float speedScale = XenoServerConfig.kiOverchargeSpeedScale(release);
        float dmgScale = XenoServerConfig.kiOverchargeDamageScale(release);
        float boomScale = XenoServerConfig.kiOverchargeExplosionScale(release);

        if (sizeScale <= 1.001f && speedScale <= 1.001f && dmgScale <= 1.001f && boomScale <= 1.001f) return;

        try {
            float size = XenoServerConfig.clampKiSize(ki.getSize() * sizeScale);
            float speed = XenoServerConfig.clampKiSpeed(ki.getKiSpeed() * speedScale);
            float dmg = ki.getKiDamage() * dmgScale;
            ki.setSize(size);
            ki.setKiSpeed(speed);
            ki.setKiDamage(dmg);

            var motion = ki.getDeltaMovement();
            if (motion.lengthSqr() > 1.0E-8D) ki.setDeltaMovement(motion.normalize().scale(speed));

            if (ki instanceof KiExplosionEntity explosion) {
                float radius = explosion.getMaxRadius() * boomScale;
                // Also grow visual size for explosions
                explosion.setMaxRadius(radius);
                explosion.setSize(Math.max(explosion.getSize(), size));
            }

            // Mild block destruction boost with overcharge
            if (boomScale > 1.05f) {
                ki.setBlockDestructionEnabled(true);
            }
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.debug("KI overcharge scale skipped: {}", t.toString());
        }
    }

    private static int readPowerRelease(ServerPlayer player) {
        try {
            var opt = com.dragonminez.common.stats.StatsProvider.get(
                    com.dragonminez.common.stats.StatsCapability.INSTANCE, player);
            if (!opt.isPresent()) return 0;
            var data = opt.orElse(null);
            if (data == null || !data.isDataLoaded()) return 0;
            Resources res = data.getResources();
            return res != null ? res.getPowerRelease() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }
}
