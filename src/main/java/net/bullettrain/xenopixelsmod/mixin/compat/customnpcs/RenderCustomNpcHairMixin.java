package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.client.compat.npc.NpcHumanoidHairLayer;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcHumanoidAppearanceLayer;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcHaloLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import noppes.npcs.client.renderer.RenderCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import noppes.npcs.entity.EntityCustomNpc;

/**
 * Adds DMZ hair as a real render layer on humanoid ("Steve") CustomNPCs, run inside the
 * renderer's own rotated pose stack (same technique {@code RenderCustomModelHairMixin}
 * uses for gecko custom-model NPCs).
 */
@Mixin(value = RenderCustomNpc.class, remap = false)
public abstract class RenderCustomNpcHairMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>", at = @At("RETURN"))
    private void xenopixels$addHairLayer(EntityRendererProvider.Context context, HumanoidModel model, CallbackInfo ci) {
        RenderCustomNpc renderer = (RenderCustomNpc) (Object) this;
        renderer.addLayer(new NpcHumanoidHairLayer(renderer));
        renderer.addLayer(new NpcHumanoidAppearanceLayer(renderer));
        renderer.addLayer(new NpcHaloLayer(renderer));
    }

    @Inject(method = "render(Lnoppes/npcs/entity/EntityCustomNpc;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void xenopixels$renderFullDmz(EntityCustomNpc npc, float yaw, float partialTick,
                                          PoseStack pose, MultiBufferSource buffers, int packedLight,
                                          CallbackInfo ci) {
        if (net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer.render(
                npc, yaw, partialTick, pose, buffers, packedLight)) {
            ci.cancel();
        }
    }
}
