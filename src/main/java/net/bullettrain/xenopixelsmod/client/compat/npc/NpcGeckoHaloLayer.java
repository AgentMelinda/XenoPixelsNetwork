package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Halo for NPCs rendered through the gecko custom-model addon.
 *
 * <p>{@link NpcHaloLayer} is a vanilla {@code RenderLayer} and is only ever attached to
 * CustomNPCs' humanoid renderer, so an NPC using a custom model from another mod had no halo at
 * all. This is the same drawing, hung off the gecko head bone the way {@link NpcDmzHairLayer}
 * hangs the DMZ hair.
 */
public final class NpcGeckoHaloLayer extends GeoRenderLayer<EntityCustomModel> {
    /**
     * Head-relative lift. {@link NpcHaloLayer} uses -0.75 against the vanilla head pivot (12
     * model pixels above it, negative-Y-is-up). A gecko bone pivot is already translated into
     * world-up space by {@code translateToPivotPoint}, so the sign flips; the magnitude is a
     * starting point and is worth re-checking against a real custom model.
     */
    private static final float HALO_HEIGHT = 0.75f;

    public NpcGeckoHaloLayer(GeoRenderer<EntityCustomModel> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, EntityCustomModel surrogate, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight,
                              int packedOverlay) {
        LivingEntity owner = surrogate instanceof NpcGeckoOwner linked
                ? linked.xenopixels$getNpcOwner() : null;
        if (owner == null) {
            return;
        }
        NpcAppearanceClient.State state = NpcAppearanceClient.get(owner.getUUID());
        if (state == null) {
            return;
        }
        NpcCombatProfile visual = new NpcCombatProfile();
        visual.applyVisualOptions(state.visualOptions());
        if (!visual.haloOn) {
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
        poseStack.translate(0.0, HALO_HEIGHT, 0.0);
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer out = bufferSource.getBuffer(RenderType.lines());
        ring(out, matrix, 0.25f, 0.0f);
        ring(out, matrix, 0.1875f, 0.015f);
        poseStack.popPose();
    }

    private static void ring(VertexConsumer out, Matrix4f matrix, float radius, float y) {
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0 * i / segments;
            double b = Math.PI * 2.0 * (i + 1) / segments;
            vertex(out, matrix, (float) Math.cos(a) * radius, y, (float) Math.sin(a) * radius);
            vertex(out, matrix, (float) Math.cos(b) * radius, y, (float) Math.sin(b) * radius);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, float x, float y, float z) {
        out.addVertex(matrix, x, y, z).setColor(255, 244, 97, 220).setNormal(0.0f, 1.0f, 0.0f);
    }
}
