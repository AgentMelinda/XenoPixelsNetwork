package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.combat.HakaiFade;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hakai outline colour. Vanilla glowing uses {@code Entity.getTeamColor()}; there is no other
 * public hook for the stencil colour.
 */
@Mixin(Entity.class)
public abstract class EntityHakaiGlowColorMixin {
    @Inject(method = "getTeamColor()I", at = @At("RETURN"), cancellable = true)
    private void xeno$hakaiOutlineColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (!HakaiFade.targeted(self)) return;
        cir.setReturnValue(XenoServerConfig.hakaiGlowColor & 0xFFFFFF);
    }
}
