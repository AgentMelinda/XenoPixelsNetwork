package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.init.entities.ki.KiExplosionVisualEntity;
import net.bullettrain.xenopixelsmod.fx.effek.KiImpactRules;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Tags only the wave's visual spawn so AAA can undo DMZ 2.1.3's -0.5 Y offset. */
@Mixin(value = KiWaveEntity.class, remap = false)
public abstract class KiWaveImpactOriginMixin {
    @ModifyArg(method = "explodeAndDie", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            remap = true), index = 0)
    private Entity xenopixels$markWaveVisual(Entity visual) {
        if (visual instanceof KiExplosionVisualEntity) {
            visual.getPersistentData().putBoolean(KiImpactRules.WAVE_VISUAL, true);
            net.bullettrain.xenopixelsmod.combat.v3.ki.V3NativeKi.markVisual(
                    ((KiWaveEntity)(Object)this).getTechniqueId(), visual);
        }
        return visual;
    }
}
