package net.bullettrain.xenopixelsmod.client.combat;

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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws a soft aura / ring around the local player while charge-attacking.
 * Bright flash when fully charged.
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class ChargeAttackGlowRenderer {
    private ChargeAttackGlowRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!XenoClientConfig.bt3ChargeGlow) return;
        if (!Bt3CombatClient.isCharging()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        float progress = Bt3CombatClient.getChargeProgress();
        boolean full = Bt3CombatClient.isFullyCharged();
        float partial = event.getPartialTick();

        Vec3 cam = event.getCamera().getPosition();
        double x = Mth.lerp(partial, player.xo, player.getX()) - cam.x;
        double y = Mth.lerp(partial, player.yo, player.getY()) - cam.y + player.getBbHeight() * 0.55;
        double z = Mth.lerp(partial, player.zo, player.getZ()) - cam.z;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(x, y, z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

        // Color: fist=orange/red, kick=magenta, dragon=gold
        float r, g, b;
        if (Bt3CombatClient.isDragonCharge()) {
            r = 1.0f; g = 0.75f; b = 0.15f;
        } else if (Bt3CombatClient.isKickCharge()) {
            r = 0.95f; g = 0.25f; b = 0.85f;
        } else {
            r = 1.0f; g = 0.35f; b = 0.1f;
        }

        float pulse = full ? (0.75f + 0.25f * Mth.sin((player.tickCount + partial) * 0.8f)) : (0.35f + 0.45f * progress);
        float radius = 0.55f + progress * 0.85f + (full ? 0.25f : 0f);
        int segs = 28;
        Matrix4f mat = pose.last().pose();

        // Horizontal ring
        for (int i = 0; i < segs; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segs);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segs);
            float x0 = Mth.cos(a0) * radius;
            float z0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius;
            float z1 = Mth.sin(a1) * radius;
            float alpha = pulse * (full ? 0.95f : 0.55f);
            vc.vertex(mat, x0, 0, z0).color(r, g, b, alpha).endVertex();
            vc.vertex(mat, x1, 0, z1).color(r, g, b, alpha).endVertex();
            vc.vertex(mat, x1 * 0.7f, 0.05f, z1 * 0.7f).color(r, g, b, alpha * 0.3f).endVertex();
            vc.vertex(mat, x0 * 0.7f, 0.05f, z0 * 0.7f).color(r, g, b, alpha * 0.3f).endVertex();
        }

        // Vertical halo when fully charged
        if (full) {
            float hr = radius * 0.9f;
            for (int i = 0; i < segs; i++) {
                float a0 = (float) (i * Math.PI * 2.0 / segs);
                float a1 = (float) ((i + 1) * Math.PI * 2.0 / segs);
                float y0 = Mth.cos(a0) * hr * 0.7f;
                float z0 = Mth.sin(a0) * hr;
                float y1 = Mth.cos(a1) * hr * 0.7f;
                float z1 = Mth.sin(a1) * hr;
                float alpha = 0.45f * pulse;
                vc.vertex(mat, 0.02f, y0, z0).color(r, g, b, alpha).endVertex();
                vc.vertex(mat, 0.02f, y1, z1).color(r, g, b, alpha).endVertex();
                vc.vertex(mat, -0.02f, y1, z1).color(r, g, b, alpha * 0.4f).endVertex();
                vc.vertex(mat, -0.02f, y0, z0).color(r, g, b, alpha * 0.4f).endVertex();
            }
        }

        buffers.endBatch(RenderType.lightning());
        pose.popPose();
    }
}
