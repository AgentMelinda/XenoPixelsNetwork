package net.bullettrain.xenopixelsmod.client.combat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Budokai Tenkaichi 3 style kick charge animation overlay.
 * Shows charging aura and release flash when kick charge completes.
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public class Bt3KickChargeOverlay {
    
    private static final ResourceLocation KICK_AURA = new ResourceLocation(XenoPixelsMod.MOD_ID, "textures/gui/kick_charge_aura.png");
    
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !Bt3CombatClient.isCharging() || !Bt3CombatClient.isKickCharge()) {
            return;
        }
        
        float progress = Bt3CombatClient.getChargeProgress();
        boolean fullyCharged = Bt3CombatClient.isFullyCharged();
        
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        
        // Center position for kick charge indicator
        int centerX = width / 2;
        int bottomY = height - 80;
        
        // Draw charging aura ring around center
        renderChargeAura(event.getPoseStack(), centerX, bottomY + 40, progress, fullyCharged);
        
        // Draw charge progress bar
        renderChargeBar(event.getPoseStack(), centerX - 50, bottomY, 100, 8, progress, fullyCharged);
        
        // Draw text indicator
        String chargeText = fullyCharged ? "§e[KICK READY - RELEASE!]" : "§bCharging Kick... " + (int)(progress * 100) + "%";
        mc.font.drawShadow(event.getPoseStack(), chargeText, centerX - mc.font.width(chargeText) / 2, bottomY - 12, 0xFFFFFF);
    }
    
    private static void renderChargeAura(PoseStack poseStack, int centerX, int centerY, float progress, boolean fullyCharged) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        
        // Aura radius grows with charge
        float baseRadius = 30f;
        float maxRadius = 50f;
        float currentRadius = baseRadius + (maxRadius - baseRadius) * progress;
        
        // Color shifts from blue to white/yellow when fully charged
        float r, g, b, alpha;
        if (fullyCharged) {
            // Pulsing yellow/white effect
            float pulse = (float)Math.sin(System.currentTimeMillis() * 0.015) * 0.3f + 0.7f;
            r = 1.0f;
            g = 0.9f + pulse * 0.1f;
            b = 0.5f;
            alpha = 0.6f + pulse * 0.2f;
        } else {
            // Blue cyan charge
            r = 0.2f;
            g = 0.6f + progress * 0.3f;
            b = 1.0f;
            alpha = 0.4f + progress * 0.3f;
        }
        
        // Draw circular aura using triangle fan
        RenderSystem.setShaderColor(r, g, b, alpha);
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        
        int segments = 32;
        for (int i = 0; i <= segments; i++) {
            double angle = (2 * Math.PI * i) / segments;
            float x = (float)(centerX + Math.cos(angle) * currentRadius);
            float y = (float)(centerY + Math.sin(angle) * currentRadius);
            buffer.vertex(x, y, 0).endVertex();
        }
        
        tesselator.end();
        
        // Outer glow ring when fully charged
        if (fullyCharged) {
            float glowRadius = currentRadius + 8f;
            float glowAlpha = 0.3f;
            
            RenderSystem.setShaderColor(1.0f, 0.8f, 0.3f, glowAlpha);
            buffer.begin(VertexFormat.Mode.LINE_LOOP, DefaultVertexFormat.POSITION);
            
            for (int i = 0; i <= segments; i++) {
                double angle = (2 * Math.PI * i) / segments;
                float x = (float)(centerX + Math.cos(angle) * glowRadius);
                float y = (float)(centerY + Math.sin(angle) * glowRadius);
                buffer.vertex(x, y, 0).endVertex();
            }
            
            tesselator.end();
        }
        
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    private static void renderChargeBar(PoseStack poseStack, int x, int y, int width, int height, float progress, boolean fullyCharged) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        Matrix4f matrix = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        
        // Background (dark)
        RenderSystem.setShaderColor(0.1f, 0.1f, 0.1f, 0.8f);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.vertex(matrix, x, y + height, 0).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0).endVertex();
        buffer.vertex(matrix, x + width, y, 0).endVertex();
        buffer.vertex(matrix, x, y, 0).endVertex();
        tesselator.end();
        
        // Fill color based on charge state
        float r, g, b;
        if (fullyCharged) {
            // Pulsing gold/yellow
            float pulse = (float)Math.sin(System.currentTimeMillis() * 0.02) * 0.2f + 0.8f;
            r = 1.0f;
            g = pulse;
            b = 0.2f;
        } else {
            // Blue gradient
            r = 0.2f;
            g = 0.5f + progress * 0.4f;
            b = 1.0f;
        }
        
        RenderSystem.setShaderColor(r, g, b, 0.9f);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        int fillWidth = (int)(width * progress);
        buffer.vertex(matrix, x, y + height, 0).endVertex();
        buffer.vertex(matrix, x + fillWidth, y + height, 0).endVertex();
        buffer.vertex(matrix, x + fillWidth, y, 0).endVertex();
        buffer.vertex(matrix, x, y, 0).endVertex();
        tesselator.end();
        
        // Border
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        buffer.begin(VertexFormat.Mode.LINE_LOOP, DefaultVertexFormat.POSITION);
        buffer.vertex(matrix, x, y, 0).endVertex();
        buffer.vertex(matrix, x + width, y, 0).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0).endVertex();
        buffer.vertex(matrix, x, y + height, 0).endVertex();
        tesselator.end();
        
        RenderSystem.disableBlend();
    }
}
