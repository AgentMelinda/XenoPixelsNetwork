package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import net.bullettrain.xenopixelsmod.combat.technique.BarrageMastery;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@code KiBlastEntity#fireHability} / {@code tick}
 * Reason: stock volley dies at {@code 50 × charge}. Duration must live in its
 *         own mixin — redirects on pellet spawn cannot be allowed to skip this.
 * Version: NeoForge 1.21.1 / DMZ 2.1.3
 * Side: common. Type 9 only.
 */
@Mixin(value = KiBlastEntity.class, remap = false)
public abstract class KiBlastBarrageDurationMixin {

    private static final int VOLLEY_TYPE = 9;

    @ModifyVariable(method = "fireHability", at = @At("HEAD"), argsOnly = true)
    private int xenopixels$durationWindow(int finalMaxLife) {
        KiBlastEntity self = (KiBlastEntity) (Object) this;
        if (self.getKiRenderType() != VOLLEY_TYPE) return finalMaxLife;
        if (!(self.getOwner() instanceof ServerPlayer player)) return finalMaxLife;
        return BarrageMastery.extendLife(finalMaxLife, CombatSkills.level(player, CombatSkills.BARRAGE));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void xenopixels$forceDuration(CallbackInfo ci) {
        KiBlastEntity self = (KiBlastEntity) (Object) this;
        if (self.level().isClientSide || !self.isFiring() || self.getKiRenderType() != VOLLEY_TYPE) {
            return;
        }
        if (XenoServerConfig.barrageDurationTicks <= 0) return;
        if (!(self.getOwner() instanceof ServerPlayer player)) return;
        int fireTick = self.getFireTick();
        if (fireTick < 0) fireTick = self.tickCount;
        int want = fireTick + BarrageMastery.extendLife(
                XenoServerConfig.barrageDurationTicks,
                CombatSkills.level(player, CombatSkills.BARRAGE));
        if (self.getMaxLife() != want) {
            self.setMaxLife(want);
        }
    }
}
