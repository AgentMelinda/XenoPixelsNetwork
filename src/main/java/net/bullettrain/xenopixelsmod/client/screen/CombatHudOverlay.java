package net.bullettrain.xenopixelsmod.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Combat HUD overlay showing counter windows, sparking meter, and guard status.
 * Rendered during combat situations.
 */
public class CombatHudOverlay {
    private static final ResourceLocation COMBAT_ICONS = 
        new ResourceLocation(XenoPixelsMod.MOD_ID, "textures/gui/combat_icons.png");
    
    private int counterWindowTimer = 0;
    private boolean isPerfectCounter = false;
    private float sparkingMeter = 0f;
    private boolean isGuarding = false;
    private boolean isSparking = false;
    private int sparkingRemainingTicks = 0;
    
    public CombatHudOverlay() {}
    
    /**
     * Updates the counter window display state.
     */
    public void setCounterWindow(boolean active, boolean isPerfect) {
        this.counterWindowTimer = active ? (isPerfect ? 10 : 6) : 0;
        this.isPerfectCounter = isPerfect;
    }
    
    /**
     * Updates the sparking meter value (0-100).
     */
    public void setSparkingMeter(float meter) {
        this.sparkingMeter = Math.max(0f, Math.min(100f, meter));
    }
    
    /**
     * Sets whether player is currently guarding.
     */
    public void setGuarding(boolean guarding) {
        this.isGuarding = guarding;
    }
    
    /**
     * Sets sparking activation state.
     */
    public void setSparking(boolean sparking, int remainingTicks) {
        this.isSparking = sparking;
        this.sparkingRemainingTicks = remainingTicks;
    }
    
    /**
     * Renders the combat HUD elements.
     */
    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        int yOffset = 10;
        
        // Render Counter Window Indicator
        if (counterWindowTimer > 0) {
            renderCounterWindow(graphics, screenWidth, screenHeight, yOffset);
            yOffset += 40;
        }
        
        // Render Sparking Meter
        renderSparkingMeter(graphics, screenWidth, screenHeight, yOffset);
        yOffset += 30;
        
        // Render Guard Status
        if (isGuarding) {
            renderGuardStatus(graphics, screenWidth, screenHeight, yOffset);
            yOffset += 25;
        }
        
        // Render Sparking Active Status
        if (isSparking) {
            renderSparkingActive(graphics, screenWidth, screenHeight, yOffset);
        }
        
        // Tick down counter window timer
        if (counterWindowTimer > 0) {
            counterWindowTimer--;
        }
    }
    
    /**
     * Renders the counter window indicator.
     */
    private void renderCounterWindow(GuiGraphics graphics, int screenWidth, int screenHeight, int yOffset) {
        int x = screenWidth / 2;
        int y = screenHeight / 2 + yOffset;
        
        // Flash effect based on timer
        float alpha = (counterWindowTimer % 4 < 2) ? 1.0f : 0.7f;
        
        if (isPerfectCounter) {
            // Perfect counter - golden/orange glow
            graphics.fill(x - 80, y - 15, x + 80, y + 15, (int)(alpha * 255) << 24 | 0xFFAA00);
            graphics.drawCenteredString(Minecraft.getInstance().font, 
                "§6§lPERFECT COUNTER!", x, y - 4, 0xFFFFAA00);
            graphics.drawCenteredString(Minecraft.getInstance().font, 
                "§ePress Attack Now!", x, y + 6, 0xFFFFFF);
        } else {
            // Normal counter - blue glow
            graphics.fill(x - 60, y - 12, x + 60, y + 12, (int)(alpha * 255) << 24 | 0x0088FF);
            graphics.drawCenteredString(Minecraft.getInstance().font, 
                "§bCOUNTER WINDOW", x, y - 4, 0xFF00AAFF);
            graphics.drawCenteredString(Minecraft.getInstance().font, 
                "§7Attack to counter!", x, y + 6, 0xFFFFFF);
        }
        
        // Progress bar showing remaining time
        int barWidth = 100;
        int barHeight = 4;
        int barX = x - barWidth / 2;
        int barY = y + 20;
        
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        
        float progress = counterWindowTimer / (isPerfectCounter ? 10f : 6f);
        int filledWidth = (int)(barWidth * progress);
        int color = isPerfectCounter ? 0xFFFFAA00 : 0xFF00AAFF;
        graphics.fill(barX, barY, barX + filledWidth, barY + barHeight, color);
    }
    
    /**
     * Renders the sparking meter.
     */
    private void renderSparkingMeter(GuiGraphics graphics, int screenWidth, int screenHeight, int yOffset) {
        int x = screenWidth - 120;
        int y = yOffset + 10;
        
        // Background
        graphics.fill(x, y, x + 100, y + 12, 0xFF222222);
        
        // Meter fill
        int filledWidth = (int)(100 * (sparkingMeter / 100f));
        
        // Color gradient based on meter level
        int color;
        if (sparkingMeter >= 100f) {
            color = 0xFFFFAA00; // Gold when full
        } else if (sparkingMeter >= 75f) {
            color = 0xFF00FF00; // Green
        } else if (sparkingMeter >= 50f) {
            color = 0xFFFFFF00; // Yellow
        } else if (sparkingMeter >= 25f) {
            color = 0xFFFF8800; // Orange
        } else {
            color = 0xFF0088FF; // Blue
        }
        
        graphics.fill(x, y, x + filledWidth, y + 12, color);
        
        // Border
        graphics.drawString(Minecraft.getInstance().font, "SPARKING", x + 5, y + 2, 0xFFFFFF);
        
        // Text overlay
        String meterText = Math.round(sparkingMeter) + "%";
        graphics.drawString(Minecraft.getInstance().font, meterText, 
            x + 105 - Minecraft.getInstance().font.width(meterText), y + 2, 0xFFFFFF);
        
        // Full meter indicator
        if (sparkingMeter >= 100f) {
            graphics.drawCenteredString(Minecraft.getInstance().font, 
                "§6§lPRESS [KEY] TO ACTIVATE!", screenWidth / 2, y, 0xFFFFAA00);
        }
    }
    
    /**
     * Renders guard status indicator.
     */
    private void renderGuardStatus(GuiGraphics graphics, int screenWidth, int screenHeight, int yOffset) {
        int x = 20;
        int y = yOffset + 10;
        
        graphics.fill(x, y, x + 60, y + 18, 0x880066CC);
        graphics.drawString(Minecraft.getInstance().font, "§bGUARDING", x + 5, y + 5, 0xFFFFFF);
    }
    
    /**
     * Renders sparking active status.
     */
    private void renderSparkingActive(GuiGraphics graphics, int screenWidth, int screenHeight, int yOffset) {
        int x = screenWidth / 2;
        int y = yOffset + 10;
        
        // Pulsing effect
        float pulse = (Minecraft.getInstance().player.tickCount % 20) / 20f;
        int alpha = (int)(128 + 127 * Math.sin(pulse * Math.PI));
        
        graphics.fill(x - 100, y - 15, x + 100, y + 15, alpha << 24 | 0xFFAA00);
        graphics.drawCenteredString(Minecraft.getInstance().font, 
            "§6§lSPARKING ACTIVE!", x, y - 4, 0xFFFFAA00);
        
        String timeLeft = String.format("%.1fs", sparkingRemainingTicks / 20f);
        graphics.drawCenteredString(Minecraft.getInstance().font, 
            "§e" + timeLeft, x, y + 6, 0xFFFFFF);
    }
    
    /**
     * Called each tick to update internal state.
     */
    public void tick() {
        if (sparkingRemainingTicks > 0) {
            sparkingRemainingTicks--;
            if (sparkingRemainingTicks <= 0) {
                isSparking = false;
            }
        }
    }
}
