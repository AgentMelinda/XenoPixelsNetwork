package net.bullettrain.xenopixelsmod.mixin.common;

import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetGravity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes vanilla's scalar down-Y term when the player travel hook supplies a radial force. */
@Mixin(Entity.class)
public abstract class EntitySmallPlanetGravityOverrideMixin {
    @Inject(method = "getGravity", at = @At("RETURN"), cancellable = true)
    private void xenopixels$suppressVanillaGravity(CallbackInfoReturnable<Double> cir) {
        double vanillaScale = SmallPlanetGravity.vanillaGravityScale((Entity) (Object) this);
        if (vanillaScale < 1.0) cir.setReturnValue(cir.getReturnValue() * vanillaScale);
    }
}
