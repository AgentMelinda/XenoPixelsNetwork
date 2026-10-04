package net.bullettrain.xenopixelsmod.client.combat;

import net.neoforged.fml.common.EventBusSubscriber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;

/**
 * Ground circle a charging fighter stands in: the local player, and any NPC
 * whose server charge clock is live.
 *
 * <p>The old indicator was a chest-height ring plus a vertical disc. From above the disc
 * collapsed to a line. This is one horizontal annulus on the floor, center at the feet.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class ChargeAttackGlowRenderer {
    private static final int SEGMENTS = 32;
    /** Hole the model stands in. */
    private static final float INNER = 0.48f;
    private static final float FEET_Y = 0.04f;

    private ChargeAttackGlowRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!XenoClientConfig.bt3ChargeGlow) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        if (Bt3CombatClient.isCharging()) {
            LocalPlayer player = mc.player;
            if (player != null) {
                float progress = Bt3CombatClient.getChargeProgress();
                boolean full = Bt3CombatClient.isFullyCharged();
                float r, g, b;
                if (Bt3CombatClient.isDragonCharge()) {
                    r = 1.0f; g = 0.75f; b = 0.15f;
                } else if (Bt3CombatClient.isKickCharge()) {
                    r = 0.95f; g = 0.25f; b = 0.85f;
                } else {
                    r = 1.0f; g = 0.35f; b = 0.1f;
                }
                drawFeetRing(pose, buffers, cam, player, partial, progress, full, r, g, b);
            }
        }

        for (var entry : NpcChargeGlowClient.entries()) {
            var glow = entry.getValue();
            if (glow == null) continue;
            net.minecraft.world.entity.Entity entity = null;
            for (net.minecraft.world.entity.Entity candidate : mc.level.entitiesForRendering()) {
                if (entry.getKey().equals(candidate.getUUID())) {
                    entity = candidate;
                    break;
                }
            }
            if (entity == null) continue;
            float progress = NpcChargeGlowClient.progress(glow);
            boolean full = progress >= 0.95f;
            float r = glow.kick() ? 0.95f : 1.0f;
            float g = glow.kick() ? 0.25f : 0.35f;
            float b = glow.kick() ? 0.85f : 0.1f;
            drawFeetRing(pose, buffers, cam, entity, partial, progress, full, r, g, b);
        }
    }

    private static void drawFeetRing(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                     Vec3 cam, net.minecraft.world.entity.Entity entity,
                                     float partial, float progress, boolean full,
                                     float r, float g, float b) {
        double x = Mth.lerp(partial, entity.xo, entity.getX()) - cam.x;
        double y = Mth.lerp(partial, entity.yo, entity.getY()) - cam.y + FEET_Y;
        double z = Mth.lerp(partial, entity.zo, entity.getZ()) - cam.z;

        pose.pushPose();
        pose.translate(x, y, z);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        float pulse = full
                ? (0.80f + 0.20f * Mth.sin((entity.tickCount + partial) * 0.8f))
                : (0.35f + 0.50f * progress);
        float outer = 0.85f + progress * 0.55f + (full ? 0.20f : 0f);
        float inner = Math.min(INNER, outer * 0.62f);
        float alpha = pulse * (full ? 0.90f : 0.50f);
        Matrix4f mat = pose.last().pose();
        ring(vc, mat, inner, outer, r, g, b, alpha);
        ring(vc, mat, outer, inner, r, g, b, alpha * 0.85f);
        buffers.endBatch(RenderType.lightning());
        pose.popPose();
    }

    private static void ring(VertexConsumer vc, Matrix4f mat, float inner, float outer,
                             float r, float g, float b, float alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / SEGMENTS);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
            float ox0 = Mth.cos(a0) * outer;
            float oz0 = Mth.sin(a0) * outer;
            float ox1 = Mth.cos(a1) * outer;
            float oz1 = Mth.sin(a1) * outer;
            float ix0 = Mth.cos(a0) * inner;
            float iz0 = Mth.sin(a0) * inner;
            float ix1 = Mth.cos(a1) * inner;
            float iz1 = Mth.sin(a1) * inner;
            vc.addVertex(mat, ox0, 0, oz0).setColor(r, g, b, alpha);
            vc.addVertex(mat, ox1, 0, oz1).setColor(r, g, b, alpha);
            vc.addVertex(mat, ix1, 0, iz1).setColor(r, g, b, alpha * 0.35f);
            vc.addVertex(mat, ix0, 0, iz0).setColor(r, g, b, alpha * 0.35f);
        }
    }
}
