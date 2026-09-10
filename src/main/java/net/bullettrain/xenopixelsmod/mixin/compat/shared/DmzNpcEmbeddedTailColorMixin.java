package net.bullettrain.xenopixelsmod.mixin.compat.shared;

import com.dragonminez.client.render.DMZPlayerRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Colors the tail bones embedded in Bio-Android and Frost Demon body models. */
@Mixin(value = GeoEntityRenderer.class, remap = false)
public abstract class DmzNpcEmbeddedTailColorMixin {
    @ModifyVariable(
            method = "renderRecursively(" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/entity/Entity;" +
                    "Lsoftware/bernie/geckolib/cache/object/GeoBone;" +
                    "Lnet/minecraft/client/renderer/RenderType;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;" +
                    "Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 1)
    private int xenopixels$colorEmbeddedRaceTail(
            int packedColor, PoseStack poseStack, Entity animatable, GeoBone bone,
            RenderType renderType, MultiBufferSource buffers,
            com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer,
            boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        if (!((Object) this instanceof DMZPlayerRenderer<?>)) return packedColor;
        float[] color = NpcFullDmzRenderer.tailColorOverride();
        if (color == null || color.length < 3
                || !NpcFullDmzRenderer.isEmbeddedRaceTailBone(bone.getName())) {
            return packedColor;
        }

        int alpha = packedColor & 0xFF000000;
        int red = Math.round(Math.max(0.0f, Math.min(1.0f, color[0])) * 255.0f);
        int green = Math.round(Math.max(0.0f, Math.min(1.0f, color[1])) * 255.0f);
        int blue = Math.round(Math.max(0.0f, Math.min(1.0f, color[2])) * 255.0f);
        return alpha | red << 16 | green << 8 | blue;
    }
}
