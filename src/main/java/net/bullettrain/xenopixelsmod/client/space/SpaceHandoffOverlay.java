package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.platform.NativeImage;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.HandoffOverlayTiming;
import net.bullettrain.xenopixelsmod.space.HandoffCameraCoast;
import net.bullettrain.xenopixelsmod.space.HandoffTransitionNoticeV4;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
    private static boolean plasmaShellStarted;
    private static long armedAtNanos;
    private static long capturedAtNanos;
    private static long fadeStartedAtNanos;
    private static ClientLevel sourceLevel;
    private static int textureWidth;
    private static int textureHeight;
    private static int fadeDurationMillis = (int) HandoffOverlayTiming.FADE_MILLIS;
    private static boolean plasmaShellOnFade;
    private static boolean coastArmed;
    private static boolean coastClockStarted;
    private static boolean coastBlending;
    private static boolean coastPlayerPositionReceived;
    private static long coastStartedAtNanos;
    private static long coastLastSampleAtNanos;
    private static Vec3 coastDestinationPosition = Vec3.ZERO;
    private static ResourceLocation coastDestinationDimension;
    private static Vec3 coastVelocity = Vec3.ZERO;
    private static Vec3 coastLastPlayerEye = Vec3.ZERO;
    private static Vec3 coastRemainingOffset = Vec3.ZERO;
    private static final long COAST_TIMEOUT_NANOS = 5_000_000_000L;
    private static final long COAST_FORCE_SETTLE_NANOS = 12_000_000_000L;
    private static final long COAST_HARD_END_NANOS = 14_000_000_000L;

    private SpaceHandoffOverlay() {}

    /** Called from the client-thread task enqueued by the clientbound addon packet handler. */
    public static void arm() {
        arm((int) HandoffOverlayTiming.FADE_MILLIS);
    }

    public static void arm(int configuredFadeMillis) {
        arm(configuredFadeMillis, false);
    }

    public static void arm(int configuredFadeMillis, boolean plasmaShell) {
        Minecraft minecraft = Minecraft.getInstance();
        SpaceReentryShellClient.stop();
        release(minecraft);
        fadeDurationMillis = Math.max(0, Math.min(60_000, configuredFadeMillis));
        plasmaShellOnFade = plasmaShell;
        plasmaShellStarted = false;
        sourceLevel = minecraft.level;
        armedAtNanos = System.nanoTime();
        armed = true;
    }

    /** Arms the existing screenshot fallback and the opt-in destination-space coast. */
    public static void armOutboundCoast(HandoffTransitionNoticeV4 notice) {
        arm(notice.overlayMillis(), notice.plasmaShell());
        if (!net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.spaceCameraCoast) return;
        coastDestinationPosition = notice.destinationPosition();
        coastDestinationDimension = notice.destinationDimension();
        coastVelocity = notice.velocityPerTick();
        coastStartedAtNanos = 0L;
        coastClockStarted = false;
        coastLastSampleAtNanos = 0L;
        coastPlayerPositionReceived = false;
        coastLastPlayerEye = Vec3.ZERO;
        coastRemainingOffset = Vec3.ZERO;
        coastBlending = false;
        coastArmed = true;
    }

    /** Called at the tail of ClientPacketListener.handleMovePlayer after vanilla applies the new pose. */
    public static void playerPositionReceived() {
        if (coastArmed) coastPlayerPositionReceived = true;
    }

    /** Camera.setup tail hook. Returns null unless an outbound coast is currently active. */
    public static Vec3 coastCameraPosition(Entity entity, float partialTick) {
        if (!coastArmed || entity == null || entity != Minecraft.getInstance().player) return null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level == sourceLevel
                || !minecraft.level.dimension().location().equals(coastDestinationDimension)) return null;
        long now = System.nanoTime();
        if (HandoffCameraCoast.shouldReleaseSourceFrameAfterPreBlendTimeout(coastClockStarted, coastBlending,
                now - coastStartedAtNanos, COAST_TIMEOUT_NANOS)) {
            release(minecraft);
            return null;
        }
        if (HandoffCameraCoast.shouldReleaseAtHardEnd(coastClockStarted,
                now - coastStartedAtNanos, COAST_HARD_END_NANOS)) {
            release(minecraft);
            return null;
        }

        Vec3 nativeEye = entity.getEyePosition(partialTick);
        Vec3 destinationEye = coastDestinationPosition.add(nativeEye.subtract(entity.position()));
        Vec3 predictedEye = destinationEye.add(HandoffCameraCoast.offset(coastVelocity,
                now - coastStartedAtNanos));
        Vec3 offsetFromPlayer = predictedEye.subtract(nativeEye);
        boolean playerChunkReady = minecraft.level.hasChunkAt(entity.blockPosition());
        if (!HandoffCameraCoast.mayBeginPlayerFollow(coastPlayerPositionReceived, playerChunkReady,
                offsetFromPlayer)) {
            // A fresh LocalPlayer can still have its default pose here. Do not use its (0,0,0)
            // chunk as the readiness signal before the server's position packet has been applied.
            // Keep the bounded projection until the authoritative pose and its chunk are ready.
            return predictedEye;
        }

        if (!coastBlending) {
            coastRemainingOffset = offsetFromPlayer;
            coastLastPlayerEye = nativeEye;
            coastLastSampleAtNanos = now;
            coastBlending = true;
        } else {
            Vec3 observedDisplacement = nativeEye.subtract(coastLastPlayerEye);
            coastLastPlayerEye = nativeEye;
            long sampleNanos = Math.max(0L, now - coastLastSampleAtNanos);
            coastLastSampleAtNanos = now;
            boolean terminalSettle = now - coastStartedAtNanos >= COAST_FORCE_SETTLE_NANOS;
            coastRemainingOffset = HandoffCameraCoast.decayOffset(coastRemainingOffset,
                    observedDisplacement, sampleNanos, terminalSettle);
        }
        if (coastRemainingOffset.lengthSqr() <= 0.0025 || now - coastStartedAtNanos >= COAST_HARD_END_NANOS) {
            coastArmed = false;
            coastBlending = false;
            release(minecraft);
            return null;
        }
        return nativeEye.add(coastRemainingOffset);
    }

    /** Invoked after vanilla moves packet handling onto the client thread, before it replaces ClientLevel. */
    public static void captureBeforeRespawn() {
        if (!armed) return;
        long now = System.nanoTime();
        if (HandoffCameraCoast.shouldExpireArmedTransition(armed, now - armedAtNanos, ARM_TIMEOUT_NANOS)) {
            discardArmedTransition();
            return;
        }
        armed = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            // PacketUtils normally queues this callback onto the client thread. If another caller
            // violates that contract, fail closed instead of leaving a coast with no clock.
            discardArmedTransition();
            return;
        }
        if (coastArmed) {
            coastStartedAtNanos = System.nanoTime();
            coastClockStarted = true;
        }
        ClientLevel oldLevel = minecraft.level;
        sourceLevel = oldLevel;
        if (minecraft.getMainRenderTarget() == null) return;
        NativeImage image = null;
        try {
            image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
            if (image.getWidth() < 1 || image.getHeight() < 1) {
                image.close();
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
        }
    }

    /** A disconnected client may stop rendering before the held-frame timeout can release its texture. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        armed = false;
        coastArmed = false;
        SpaceReentryShellClient.stop();
        release(Minecraft.getInstance());
    }

    /** If screen capture is unavailable, still start the configured shell on a ready destination player. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!plasmaShellOnFade || plasmaShellStarted || captured) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.level == sourceLevel
                || !minecraft.level.hasChunkAt(minecraft.player.blockPosition())) return;
        startPlasmaShell();
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
        if (HandoffCameraCoast.shouldExpireArmedTransition(armed, now - armedAtNanos, ARM_TIMEOUT_NANOS)) {
            discardArmedTransition();
        }
        if (coastArmed && HandoffCameraCoast.shouldReleaseSourceFrameAfterPreBlendTimeout(coastClockStarted,
                coastBlending, now - coastStartedAtNanos, COAST_TIMEOUT_NANOS)) {
            release(minecraft);
            return;
        }
        if (coastArmed && HandoffCameraCoast.shouldReleaseAtHardEnd(coastClockStarted,
                now - coastStartedAtNanos, COAST_HARD_END_NANOS)) {
            release(minecraft);
            return;
        }
        if (!captured) return;

        boolean destinationReady = minecraft.level != null && minecraft.level != sourceLevel
                && minecraft.player != null && minecraft.level.hasChunkAt(minecraft.player.blockPosition());
        if (!fading && HandoffOverlayTiming.shouldStartFade(destinationReady, now, capturedAtNanos)) {
            fading = true;
            fadeStartedAtNanos = now;
            startPlasmaShell();
        }
        long fadeElapsedMillis = fading ? (now - fadeStartedAtNanos) / 1_000_000L : 0L;
        if (!HandoffOverlayTiming.shouldDrawCapturedFrame(captured, fading,
                fadeElapsedMillis, fadeDurationMillis)) {
            if (coastArmed) releaseCapturedFrame(minecraft);
            else release(minecraft);
            return;
        }
        float alpha = fading
                ? HandoffOverlayTiming.alpha(fadeElapsedMillis, fadeDurationMillis)
                : 1.0f;

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

    private static void startPlasmaShell() {
        if (!plasmaShellOnFade || plasmaShellStarted) return;
        plasmaShellStarted = true;
        SpaceReentryShellClient.start();
    }

    private static void release(Minecraft minecraft) {
        releaseCapturedFrame(minecraft);
        fadeDurationMillis = (int) HandoffOverlayTiming.FADE_MILLIS;
        plasmaShellOnFade = false;
        plasmaShellStarted = false;
        clearCoastState();
    }

    /** Releases only the screenshot; an armed camera coast may outlive its one-second cover. */
    private static void releaseCapturedFrame(Minecraft minecraft) {
        if (captured) minecraft.getTextureManager().release(FRAME);
        captured = false;
        fading = false;
        fadeStartedAtNanos = 0;
        sourceLevel = null;
        textureWidth = 0;
        textureHeight = 0;
    }

    /** Discards pre-capture state without touching client texture APIs; safe for a rejected callback. */
    private static void discardArmedTransition() {
        armed = false;
        sourceLevel = null;
        plasmaShellOnFade = false;
        plasmaShellStarted = false;
        clearCoastState();
    }

    private static void clearCoastState() {
        coastArmed = false;
        coastClockStarted = false;
        coastBlending = false;
        coastPlayerPositionReceived = false;
        coastStartedAtNanos = 0L;
        coastLastSampleAtNanos = 0L;
        coastDestinationPosition = Vec3.ZERO;
        coastDestinationDimension = null;
        coastVelocity = Vec3.ZERO;
        coastLastPlayerEye = Vec3.ZERO;
        coastRemainingOffset = Vec3.ZERO;
    }
}
