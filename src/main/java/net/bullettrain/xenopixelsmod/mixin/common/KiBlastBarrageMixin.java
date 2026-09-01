package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.combat.technique.KiGuidance;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@code KiBlastEntity#fireHability} / {@code onKiTick}
 * Reason: stock barrage dies at 50 × charge ticks; Volley Mastery lengthens the emitter
 *         and Ki Guidance tags pellets with the lock-on target.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x
 * Side: common. Owner lookups are server-only.
 */
@Mixin(value = KiBlastEntity.class, remap = false)
public abstract class KiBlastBarrageMixin {

    private static final int VOLLEY_TYPE = 9;
    private static final int MASTERY_TICKS = 40;

    @Redirect(method = "onKiTick", at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/init/entities/ki/KiBlastEntity;setupKiSmall(Lnet/minecraft/world/entity/LivingEntity;FFI)V"))
    private void xenopixels$setupPellet(KiBlastEntity pellet, net.minecraft.world.entity.LivingEntity owner,
                                        float damage, float speed, int color) {
        pellet.setupKiSmall(owner, damage, speed, color);
        KiGuidance.tagBarragePellet((KiBlastEntity) (Object) this, pellet);
    }

    @Redirect(method = "onKiTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            remap = true))
    private boolean xenopixels$guidePellet(Level level, Entity entity) {
        KiBlastEntity self = (KiBlastEntity) (Object) this;
        if (self.getKiRenderType() == VOLLEY_TYPE && entity instanceof KiBlastEntity pellet) {
            KiGuidance.tagBarragePellet(self, pellet);
        }
        return level.addFreshEntity(entity);
    }

    @Inject(method = "onKiTick", at = @At("HEAD"))
    private void xenopixels$volleyUpkeep(CallbackInfo ci) {
        KiBlastEntity self = (KiBlastEntity) (Object) this;
        if (self.level().isClientSide || !self.isFiring() || self.getKiRenderType() != VOLLEY_TYPE) {
            return;
        }
        if (!(self.getOwner() instanceof ServerPlayer player)) return;

        float drain = XenoServerConfig.barrageKiPerTick;
        if (drain > 0f) {
            Resources res = resources(player);
            if (res != null) {
                if (res.getCurrentEnergy() < drain) {
                    self.discard();
                    return;
                }
                res.removeEnergy(drain);
            }
        }

        int mastery = CombatSkills.level(player, CombatSkills.BARRAGE);
        if (mastery < 3 && self.tickCount % MASTERY_TICKS == 0) {
            CombatSkills.awardBarrageProgress(player);
        }
    }

    private static Resources resources(ServerPlayer player) {
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            return data != null ? data.getResources() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
