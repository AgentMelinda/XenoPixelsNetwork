package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import net.bullettrain.xenopixelsmod.client.combat.SparkingAura;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.BakedGeoModel;

/**
 * Records whose aura DragonMineZ is drawing, for {@link DmzAuraColorSparkingMixin}.
 *
 * <p>The colour helper that mixin overrides is static and is handed only two colour names, so on
 * its own it cannot tell one player from another. This layer is the nearest place that does have
 * the player, so it brackets its own render call with the answer.
 *
 * <p>Cleared on the way out rather than left set, so an aura colour resolved outside any layer —
 * DMZ's GUI aura preview, for one — is never tinted by whoever happened to be drawn last.
 */
@Mixin(targets = "com.dragonminez.client.render.layer.DMZAuraLayer", remap = false)
public abstract class DmzAuraLayerSparkingMixin {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/player/AbstractClientPlayer;"
                    + "Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;"
                    + "Lnet/minecraft/client/renderer/RenderType;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;FII)V",
            at = @At("HEAD"),
            require = 0)
    private void xenopixels$markAuraOwner(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                          AbstractClientPlayer player, BakedGeoModel model, RenderType renderType,
                                          MultiBufferSource bufferSource, VertexConsumer consumer, float partialTick,
                                          int packedLight, int packedOverlay, CallbackInfo ci) {
        SparkingAura.setRendering(player);
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/player/AbstractClientPlayer;"
                    + "Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;"
                    + "Lnet/minecraft/client/renderer/RenderType;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;FII)V",
            at = @At("RETURN"),
            require = 0)
    private void xenopixels$clearAuraOwner(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                           AbstractClientPlayer player, BakedGeoModel model, RenderType renderType,
                                           MultiBufferSource bufferSource, VertexConsumer consumer, float partialTick,
                                           int packedLight, int packedOverlay, CallbackInfo ci) {
        SparkingAura.setRendering(null);
    }
}
