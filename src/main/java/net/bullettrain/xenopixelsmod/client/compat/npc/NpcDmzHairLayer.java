package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** DMZ hair on a gecko custom-model head bone. Spec comes from the appearance packet. */
public final class NpcDmzHairLayer extends GeoRenderLayer<EntityCustomModel> {
    private static final Set<UUID> MISSING_HEAD_LOGGED = ConcurrentHashMap.newKeySet();

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
        String appearanceBone = "";
        NpcAppearanceClient.State state = NpcAppearanceClient.get(owner.getUUID());
        if (state != null && state.appearance() != null) {
            appearanceBone = state.appearance().activeHeadBone;
        }
        String headBone = NpcGeckoHeadAttach.resolveBone(surrogate.headBoneName, appearanceBone);
        if (!NpcGeckoHeadAttach.matches(headBone, bone.getName())) {
            if (getGeoModel().getBone(headBone).isEmpty()
                    && MISSING_HEAD_LOGGED.add(owner.getUUID())) {
                XenoPixelsMod.LOGGER.info(
                        "NPC gecko hair skipped; no head bone '{}' on {}",
                        headBone, owner.getUUID());
            }
            return;
        }
        poseStack.pushPose();
        // GeckoLib 4.9.2 already called RenderUtil.prepMatrixForBone on this stack
        // (GeoRenderer.renderRecursively). Same attach as DMZHairLayer: sit on the
        // live pivot in that already-rotated bone space. Do not prep/rotate again.
        RenderUtil.translateToPivotPoint(poseStack, bone);
        NpcHairVis.render(poseStack, owner, bufferSource, partialTick, packedLight, packedOverlay);
        if (renderType != null) {
            bufferSource.getBuffer(renderType);
        }
        poseStack.popPose();
    }
}
