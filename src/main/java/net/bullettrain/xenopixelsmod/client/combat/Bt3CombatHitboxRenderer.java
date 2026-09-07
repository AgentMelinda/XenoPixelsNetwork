package net.bullettrain.xenopixelsmod.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Client-only blue attack/hurt-box visualization. It is deliberately visual-only.
 *
 * <p>The toggle is useful on its own and is also the Xeno-side equivalent of DMZ's
 * F3+B combat inspection. Server collision and damage never consult this renderer.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class Bt3CombatHitboxRenderer {
    private static final int RANGE = 24;

    private Bt3CombatHitboxRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || !XenoClientConfig.bt3CombatHitboxes) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        double px = Mth.lerp(partial, player.xo, player.getX());
        double py = Mth.lerp(partial, player.yo, player.getY());
        double pz = Mth.lerp(partial, player.zo, player.getZ());
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(px - camera.x, py - camera.y, pz - camera.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        draw(lines, pose, player.getBoundingBox().move(-player.getX(), -player.getY(), -player.getZ()));
        Vec3 forward = player.getLookAngle().normalize();
        AABB attack = player.getBoundingBox().move(-player.getX(), -player.getY(), -player.getZ())
                .expandTowards(forward.scale(2.6)).inflate(0.18, 0.12, 0.18);
        draw(lines, pose, attack);

        Entity target = player.getLastHurtMob();
        if (target != null && target.distanceToSqr(player) <= RANGE * RANGE) {
            Vec3 targetOffset = target.position().subtract(player.position());
            draw(lines, pose, target.getBoundingBox().move(-target.getX(), -target.getY(), -target.getZ())
                    .move(targetOffset));
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static void draw(VertexConsumer lines, PoseStack pose, AABB box) {
        LevelRenderer.renderLineBox(pose, lines, box, 0.08f, 0.45f, 1.0f, 0.95f);
    }
}
