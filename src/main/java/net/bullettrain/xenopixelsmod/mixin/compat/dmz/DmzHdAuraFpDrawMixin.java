package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.client.aura.HdAuraClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DragonMineZ {@code AuraRenderer.processFirstPersonAuras} can draw the classic FP sheet
 * without going through {@code PlayerEffectQueue.addFirstPersonAura} (fallback when the local
 * player was not queued). Cancel that draw in HD-only mode so classic DMZ cannot return.
 */
@Mixin(targets = "com.dragonminez.client.render.effects.AuraRenderer", remap = false)
public abstract class DmzHdAuraFpDrawMixin {

    @Inject(method = "renderShaderFirstPersonAura(Lnet/minecraft/world/entity/player/Player;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Minecraft;Lorg/joml/Matrix4f;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$hideDmzFpAura(Player player, float partialTick, PoseStack pose,
                                                 Minecraft mc, Matrix4f projection, CallbackInfo ci) {
        // Same gate as queue mixin: only hide classic FP while HD-only is healthy.
        if (HdAuraClient.replacesDmzAura()) {
            ci.cancel();
        }
    }
}
