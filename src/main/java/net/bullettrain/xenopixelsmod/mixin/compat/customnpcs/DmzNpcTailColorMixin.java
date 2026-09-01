package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import com.dragonminez.client.render.layer.DMZRacePartsLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import software.bernie.geckolib.cache.object.GeoBone;

/** Applies an NPC-only tail override after DMZ has resolved its normal race/form inheritance. */
@Mixin(value = DMZRacePartsLayer.class, remap = false)
public abstract class DmzNpcTailColorMixin {
    private static final ThreadLocal<float[]> XENOPIXELS_TAIL_COLOR = new ThreadLocal<>();

    @Inject(
            method = "renderTargetedBone(Lsoftware/bernie/geckolib/cache/object/GeoBone;" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;" +
                    "Lnet/minecraft/client/player/AbstractClientPlayer;" +
                    "Lnet/minecraft/client/renderer/RenderType;FFFFFI)V",
            at = @At("HEAD"), require = 1)
    private void xenopixels$captureNpcTailColor(
            GeoBone bone, PoseStack pose, MultiBufferSource buffers,
            AbstractClientPlayer player, RenderType type,
            float red, float green, float blue, float alpha, float partialTick, int light,
            CallbackInfo ci) {
        float[] color = NpcFullDmzRenderer.tailColorOverride();
        if (bone != null && "tailenrolled".equals(bone.getName())
                && color != null && color.length >= 3) {
            XENOPIXELS_TAIL_COLOR.set(color);
        } else {
            XENOPIXELS_TAIL_COLOR.remove();
        }
    }

    @ModifyArgs(
            method = "renderTargetedBone(Lsoftware/bernie/geckolib/cache/object/GeoBone;" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;" +
                    "Lnet/minecraft/client/player/AbstractClientPlayer;" +
                    "Lnet/minecraft/client/renderer/RenderType;FFFFFI)V",
            at = @At(value = "INVOKE", target =
                    "Lcom/dragonminez/client/render/util/RenderBufferUtil;packColor(FFFF)I"),
            require = 1)
    private void xenopixels$useNpcTailColor(Args args) {
        float[] color = XENOPIXELS_TAIL_COLOR.get();
        if (color == null || color.length < 3) return;
        args.set(0, color[0]);
        args.set(1, color[1]);
        args.set(2, color[2]);
    }

    @Inject(
            method = "renderTargetedBone(Lsoftware/bernie/geckolib/cache/object/GeoBone;" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;" +
                    "Lnet/minecraft/client/player/AbstractClientPlayer;" +
                    "Lnet/minecraft/client/renderer/RenderType;FFFFFI)V",
            at = @At("RETURN"), require = 1)
    private void xenopixels$clearNpcTailColor(
            GeoBone bone, PoseStack pose, MultiBufferSource buffers,
            AbstractClientPlayer player, RenderType type,
            float red, float green, float blue, float alpha, float partialTick, int light,
            CallbackInfo ci) {
        XENOPIXELS_TAIL_COLOR.remove();
    }
}
