package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.aura.AuraRimGlow;
import net.bullettrain.xenopixelsmod.client.combat.HakaiOutlineRenderPolicy;
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
        // Vanilla's glowing outline is a full-body silhouette, while HakaiClipMultiBufferSource clips
        // only the inner body pass. Keeping that outline during an active sweep masks the progressive
        // cut; when the server releases its glow lease on cancel, the already-cut body appears at once.
        boolean auraRim = !cir.getReturnValueZ() && entity != null
                && AuraRimGlow.colour(entity.getId()) != null;
        boolean sweepActive = entity != null
                && net.bullettrain.xenopixelsmod.client.combat.HakaiSweepFx.active(entity);
        if (HakaiOutlineRenderPolicy.suppressOutline(sweepActive, cir.getReturnValueZ(), auraRim)) {
            if (net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.hakaiSweepDebug)
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info("HAKAI_SWEEP suppress_outline id={} class={}",
                        entity.getId(), entity.getClass().getSimpleName());
            cir.setReturnValue(false);
            return;
        }
        if (auraRim) {
            cir.setReturnValue(true);
        }
    }
}
