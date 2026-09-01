package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.compat.npc.NpcHairBridge;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;

/**
 * DMZ hair on humanoid ("Steve") CustomNPCs. Runs as a real render layer on the head bone
 * so it inherits the model's live head/body rotation, unlike the old
 * {@code RenderLivingEvent.Post} hook it replaces (that event's pose stack has already had
 * body yaw and head rotation popped off by the time it fires).
 */
public final class NpcHumanoidHairLayer<T extends LivingEntity, M extends EntityModel<T> & HeadedModel>
        extends RenderLayer<T, M> {

    public NpcHumanoidHairLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                        float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                        float netHeadYaw, float headPitch) {
        NpcHairVis.Spec spec = NpcHairVis.spec(entity);
        if (spec == null || !spec.enabled() || spec.code() == null || spec.code().isBlank()) {
            return;
        }
        if (NpcHairBridge.data(entity) != null) {
            return;
        }
        poseStack.pushPose();
        this.getParentModel().getHead().translateAndRotate(poseStack);
        // LivingEntityRenderer.setupRotations() applies a global scale(-1,-1,1) mirror before
        // any model/layer renders (an old vanilla convention GeckoLib does not replicate).
        // HairRenderer's strand math was authored for DMZ's GeckoLib player layer, which never
        // sees that mirror, so calling it as-is here flips the hair's vertical direction --
        // spikes that should read as growing up from the scalp instead drape down over the
        // chest. Vanilla's own CustomHeadLayer hits the same problem attaching skulls to the
        // head bone and counters it with its own compensating negative scale; this cancels the
        // same ambient mirror back out before handing off to hair code that expects "normal"
        // (GeckoLib) handedness.
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        NpcHairVis.render(poseStack, entity, buffer, partialTick, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
