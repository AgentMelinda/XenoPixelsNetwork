package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/** DMZ hair on a gecko custom-model head bone. Spec comes from the appearance packet. */
public final class NpcDmzHairLayer extends GeoRenderLayer<EntityCustomModel> {
    public NpcDmzHairLayer(GeoRenderer<EntityCustomModel> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, EntityCustomModel surrogate, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight,
                              int packedOverlay) {
        LivingEntity owner = surrogate instanceof NpcGeckoOwner linked
                ? linked.xenopixels$getNpcOwner() : null;
        NpcHairVis.Spec spec = NpcHairVis.spec(owner);
        if (owner == null || spec == null || !spec.enabled()) {
            return;
        }
        String headBone = surrogate.headBoneName;
        if (headBone == null || headBone.isBlank()) {
            headBone = "head";
        }
        if (!headBone.equals(bone.getName())) {
            return;
        }
        poseStack.pushPose();
        RenderUtil.translateToPivotPoint(poseStack, bone);
        NpcHairVis.render(poseStack, owner, bufferSource, partialTick, packedLight, packedOverlay);
        if (renderType != null) {
            bufferSource.getBuffer(renderType);
        }
        poseStack.popPose();
    }
}
