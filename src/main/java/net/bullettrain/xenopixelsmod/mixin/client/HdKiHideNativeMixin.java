package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.ki.HdKiClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While HD ki is on, the DragonMineZ ki projectiles it redraws are not drawn by their own
 * renderers. Done at the dispatcher, so DragonMineZ's renderer classes are not touched at all;
 * with HD ki off this changes nothing.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class HdKiHideNativeMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private <E extends Entity> void xenopixels$hideRedrawnKi(E entity, Frustum frustum, double camX, double camY,
                                                             double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (HdKiClient.hides(entity)) cir.setReturnValue(false);
    }
}
