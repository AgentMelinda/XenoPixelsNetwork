package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

/** Emissive DMZ-colored halo for non-Full humanoid NPC appearance modes. */
public final class NpcHaloLayer<T extends LivingEntity, M extends EntityModel<T> & HeadedModel>
        extends RenderLayer<T, M> {
    public NpcHaloLayer(RenderLayerParent<T, M> parent) { super(parent); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        NpcAppearanceClient.State state = NpcAppearanceClient.get(entity.getUUID());
        if (state == null) return;
        NpcCombatProfile visual = new NpcCombatProfile();
        visual.applyVisualOptions(state.visualOptions());
        if (!visual.haloOn) return;
        pose.pushPose();
        // Translate to the head pivot but do NOT take its rotation. ModelPart.translateAndRotate
        // applies both, which left the halo pitching and swinging with wherever the NPC was
        // looking instead of hanging level above its head -- the "wrong looking halo" on plain
        // humanoid NPCs. A halo is world-up, not head-relative. {@link NpcGeckoHaloLayer} already
        // gets this right via RenderUtil.translateToPivotPoint, which is translation only; this is
        // the same thing spelled out for a vanilla ModelPart, whose coordinates are 16 per block.
        ModelPart head = getParentModel().getHead();
        pose.translate(head.x / 16.0f, head.y / 16.0f, head.z / 16.0f);
        // DMZ's raceparts halo sits 12 model pixels above the player head pivot
        // (the head top is 8px above it, followed by a 4px air gap). Vanilla model
        // coordinates use 16px per block and negative Y for "up", so -12/16 is
        // the matching head-relative position. Keep DMZ's 8px outer diameter too;
        // the old additional 1.25 scale made the NPC halo almost twice player size.
        pose.translate(0.0, -0.75, 0.0);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer out = buffers.getBuffer(RenderType.lines());
        ring(out, matrix, 0.25f, 0.0f);
        ring(out, matrix, 0.1875f, 0.015f);
        pose.popPose();
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
