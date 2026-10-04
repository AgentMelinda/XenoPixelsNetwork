package net.bullettrain.xenopixelsmod.mixin.compat.cnpcgecko;

import com.goodbird.cnpcgeckoaddon.client.renderer.RenderCustomModel;
import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcGeckoOwner;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcDmzHairLayer;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcGeckoHaloLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.renderer.GeoRenderer;

@Mixin(value = RenderCustomModel.class, remap = false)
public abstract class RenderCustomModelHairMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"))
    private void xenopixels$addDmzHairLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
        RenderCustomModel renderer = (RenderCustomModel) (Object) this;
        renderer.addRenderLayer(new NpcDmzHairLayer((GeoRenderer<EntityCustomModel>) renderer));
        // Halo: NpcHaloLayer is a vanilla RenderLayer and only reaches CustomNPCs' humanoid
        // renderer, so a custom-model NPC could not show one at all until this.
        renderer.addRenderLayer(new NpcGeckoHaloLayer((GeoRenderer<EntityCustomModel>) renderer));
    }

    @Inject(method = "defaultRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/goodbird/cnpcgeckoaddon/entity/EntityCustomModel;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFI)V",
            at = @At("HEAD"), cancellable = true)
    private void xenopixels$renderFullDmz(PoseStack pose, EntityCustomModel surrogate,
                                          MultiBufferSource buffers, RenderType renderType,
                                          VertexConsumer vertexConsumer, float renderYaw,
                                          float partialTick, int packedLight, CallbackInfo ci) {
        LivingEntity owner = surrogate instanceof NpcGeckoOwner linked
                ? linked.xenopixels$getNpcOwner() : null;
        if (owner != null && NpcFullDmzRenderer.isFull(owner)) {
            Vec3 from = surrogate.getPosition(partialTick);
            Vec3 to = owner.getPosition(partialTick);
            pose.pushPose();
            pose.translate(to.x - from.x, to.y - from.y, to.z - from.z);
            boolean drew = NpcFullDmzRenderer.render(owner, renderYaw, partialTick, pose, buffers, packedLight);
            pose.popPose();
            if (drew) {
                ci.cancel();
            }
            return;
        }
        if (owner != null && NpcFullDmzRenderer.render(owner, renderYaw, partialTick, pose, buffers, packedLight)) {
            ci.cancel();
        }
    }
}
