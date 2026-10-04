package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.aura.AuraRimGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The aura-off rim glow: while a form is on and the aura is off (HD aura chosen), the player is
 * drawn with vanilla's outline glow on this client only, in their aura colour
 * ({@link EntityAuraRimGlowColorMixin}).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAuraRimGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"),
            cancellable = true)
    private void xeno$auraRimGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && entity != null && AuraRimGlow.colour(entity.getId()) != null) {
            cir.setReturnValue(true);
        }
    }
}
