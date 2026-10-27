package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.common.init.MainGameRules;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the Xeno edited-small-planet admission to DMZ's shared Ki-grief permission path.
 * Existing DMZ gamerule, structure and protection denials remain authoritative; this
 * hook only denies an otherwise permitted mutation at a tagged small-planet plot.
 */
@Mixin(value = MainGameRules.class, remap = false)
public abstract class SmallPlanetKiGriefCapMixin {
    @Inject(method = "canKiGrief(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private static void xenopixels$smallPlanetEditAdmission(Level level, BlockPos position, Entity source,
                                                              CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !SmallPlanetPhysicalizer.allowKiMutation(level, position))
            cir.setReturnValue(false);
    }
}
