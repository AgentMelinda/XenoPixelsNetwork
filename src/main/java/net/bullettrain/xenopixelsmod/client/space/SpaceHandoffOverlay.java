package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.platform.NativeImage;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.HandoffOverlayTiming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Holds the last rendered frame over vanilla's receiving-level screen, then reveals the destination. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class SpaceHandoffOverlay {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(
            XenoPixelsMod.MOD_ID, "space_handoff_frame");
    private static final long ARM_TIMEOUT_NANOS = 5_000_000_000L;

    private static boolean armed;
    private static boolean captured;
    private static boolean fading;
    private static long armedAtNanos;
    private static long capturedAtNanos;
    private static long fadeStartedAtNanos;
    private static ClientLevel sourceLevel;
    private static int textureWidth;
    private static int textureHeight;

    private SpaceHandoffOverlay() {}

    /** Called from the client-thread task enqueued by the clientbound addon packet handler. */
    public static void arm() {
        Minecraft minecraft = Minecraft.getInstance();
        release(minecraft);
        sourceLevel = minecraft.level;
        armedAtNanos = System.nanoTime();
        armed = true;
    }

    /** Invoked after vanilla moves packet handling onto the client thread, before it replaces ClientLevel. */
    public static void captureBeforeRespawn() {
        if (!armed) return;
        long now = System.nanoTime();
        if (now - armedAtNanos > ARM_TIMEOUT_NANOS) {
            armed = false;
            sourceLevel = null;
            return;
        }
        armed = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) { sourceLevel = null; return; }
        ClientLevel oldLevel = minecraft.level;
        if (oldLevel == null || minecraft.getMainRenderTarget() == null) { sourceLevel = null; return; }
        NativeImage image = null;
        try {
            image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
            if (image.getWidth() < 1 || image.getHeight() < 1) {
                image.close();
                sourceLevel = null;
                return;
            }
            textureWidth = image.getWidth();
            textureHeight = image.getHeight();
            minecraft.getTextureManager().register(FRAME, new DynamicTexture(image));
            image = null; // DynamicTexture owns the NativeImage now.
            sourceLevel = oldLevel;
            capturedAtNanos = System.nanoTime();
            fadeStartedAtNanos = 0;
            fading = false;
            captured = true;
        } catch (RuntimeException error) {
            XenoPixelsMod.LOGGER.warn("Could not capture the frame before space handoff", error);
            if (image != null) image.close();
            captured = false;
            sourceLevel = null;
        }
    }

    /** A disconnected client may stop rendering before the held-frame timeout can release its texture. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        armed = false;
        release(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onGuiPost(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) render(event.getGuiGraphics(), minecraft);
    }

    @SubscribeEvent
    public static void onScreenPost(ScreenEvent.Render.Post event) {
        render(event.getGuiGraphics(), Minecraft.getInstance());
    }

    private static void render(GuiGraphics graphics, Minecraft minecraft) {
        long now = System.nanoTime();
        if (armed && now - armedAtNanos > ARM_TIMEOUT_NANOS) {
            armed = false;
            sourceLevel = null;
        }
        if (!captured) return;

        boolean destinationReady = minecraft.level != null && minecraft.level != sourceLevel
                && minecraft.player != null && minecraft.level.hasChunkAt(minecraft.player.blockPosition());
        if (!fading && HandoffOverlayTiming.shouldStartFade(destinationReady, now, capturedAtNanos)) {
            fading = true;
            fadeStartedAtNanos = now;
        }
        float alpha = fading
                ? HandoffOverlayTiming.alpha((now - fadeStartedAtNanos) / 1_000_000L)
                : 1.0f;
        if (alpha <= 0.0f) {
            release(minecraft);
            return;
        }

        graphics.setColor(1.0f, 1.0f, 1.0f, alpha);
        try {
            int width = minecraft.getWindow().getGuiScaledWidth();
            int height = minecraft.getWindow().getGuiScaledHeight();
            graphics.blit(FRAME, 0, 0, width, height, 0.0f, 0.0f,
                    textureWidth, textureHeight, textureWidth, textureHeight);
        } finally {
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static void release(Minecraft minecraft) {
        if (captured) minecraft.getTextureManager().release(FRAME);
        captured = false;
        fading = false;
        fadeStartedAtNanos = 0;
        sourceLevel = null;
        textureWidth = 0;
        textureHeight = 0;
    }
}
