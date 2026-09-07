package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.bullettrain.xenopixelsmod.client.combat.ClientLockState;
import net.bullettrain.xenopixelsmod.client.combat.XenoTargetingControls;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.render.WorldToScreenCache;
import net.bullettrain.xenopixelsmod.combat.targeting.LeadCalculator;
import net.bullettrain.xenopixelsmod.combat.targeting.LockOnQuality;
import net.bullettrain.xenopixelsmod.combat.targeting.LockOnValidator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * War Thunder-style seat crosshair: a fixed center reticle whose color and shape communicate
 * lock state, plus lock brackets, an off-screen arrow and a lead marker projected from
 * {@link WorldToScreenCache}.
 *
 * <p>Every fact this draws — whether a lock exists, its quality and progress, and the target's
 * velocity for the lead marker — comes from {@link ClientLockState}, which is only ever written
 * by an authoritative server packet. The one thing computed client-side is
 * {@link XenoTargetingControls#cachedCandidate()}, the nearest-in-cone player used purely for the
 * hover-state color before any lock exists; it decides nothing server-side and a hostile client
 * reporting a fake candidate id gains nothing, since {@code LockOnValidator} re-derives
 * eligibility from scratch on the server for every request.
 *
 * <p>Cached across frames wherever the underlying value has not changed, mirroring
 * {@link XenoFlightHudOverlay}'s discipline: this renders every frame but the snapshot it reads
 * updates only a few times a second.
 */
public final class XenoCrosshairHudLayer {

    private static final float ARM_LENGTH_MUL = 1.6f;
    private static final int GAP_PX = 3;
    private static final int BRACKET_SIZE = 7;
    private static final int BRACKET_HALF_BOX = 14;

    /**
     * Hides vanilla's own crosshair while the seat reticle is up.
     *
     * <p>Both are drawn dead-centre, so without this the pilot sees this layer's reticle with
     * vanilla's cross sitting inside it — and vanilla's version also carries the attack-strength
     * indicator, which means nothing in a cockpit. Cancelling the layer is the supported way to
     * suppress one vanilla HUD element without touching any of the others.
     */
    @net.neoforged.fml.common.EventBusSubscriber(
            modid = net.bullettrain.xenopixelsmod.XenoPixelsMod.MOD_ID,
            value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class VanillaCrosshairSuppressor {
        private VanillaCrosshairSuppressor() {
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void onRenderLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
            if (!event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR)) return;
            if (!XenoClientConfig.crosshairEnabled) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getVehicle() instanceof XenoPilotSeatEntity) {
                event.setCanceled(true);
            }
        }
    }

    private String cachedInfoLine = "";
    private int cachedInfoTargetId = Integer.MIN_VALUE;
    private double cachedInfoDistance = -1.0;

    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!XenoClientConfig.crosshairEnabled) return;
        if (mc.screen != null) return;
        if (mc.player.isSpectator() || !mc.player.isAlive()) return;
        if (!(mc.player.getVehicle() instanceof XenoPilotSeatEntity)) return;

        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;

        boolean locked = ClientLockState.hasLock();
        LockOnQuality quality = ClientLockState.quality();
        LockOnValidator.Reason recentError = ClientLockState.recentError();
        Entity hoverCandidate = locked ? null : XenoTargetingControls.cachedCandidate();

        int color = pickColor(locked, quality, hoverCandidate, recentError);
        float alpha = XenoClientConfig.crosshairOpacity;
        int argb = applyAlpha(color, alpha);

        drawReticle(graphics, centerX, centerY, argb, locked && quality != LockOnQuality.HARD);

        Entity target = locked ? ClientLockState.resolveTarget() : null;
        if (target != null) {
            renderLockedTarget(graphics, mc, centerX, centerY, target, quality);
        } else if (recentError != null) {
            drawErrorText(graphics, mc, centerX, centerY, recentError);
        }
    }

    private int pickColor(boolean locked, LockOnQuality quality, @Nullable Entity hoverCandidate,
                          @Nullable LockOnValidator.Reason recentError) {
        if (locked) {
            return quality == LockOnQuality.HARD
                    ? XenoClientConfig.crosshairColorLocked
                    : XenoClientConfig.crosshairColorLocking;
        }
        if (recentError != null) return XenoClientConfig.crosshairColorInvalid;
        if (hoverCandidate != null) return XenoClientConfig.crosshairColorLocking;
        return XenoClientConfig.crosshairColorNormal;
    }

    /** Four short arms with a center gap; locking state also draws a thin progress tick under it. */
    private void drawReticle(GuiGraphics graphics, int cx, int cy, int argb, boolean showProgress) {
        int len = Math.round(XenoClientConfig.crosshairSize * ARM_LENGTH_MUL);
        int thick = Math.max(1, Math.round(XenoClientConfig.crosshairThickness));
        int half = thick / 2;

        // Top / bottom
        HudDraw.fillRect(graphics, cx - half, cy - GAP_PX - len, thick, len, argb);
        HudDraw.fillRect(graphics, cx - half, cy + GAP_PX, thick, len, argb);
        // Left / right
        HudDraw.fillRect(graphics, cx - GAP_PX - len, cy - half, len, thick, argb);
        HudDraw.fillRect(graphics, cx + GAP_PX, cy - half, len, thick, argb);

        if (showProgress && XenoClientConfig.showLockProgress) {
            int barWidth = Math.round(XenoClientConfig.crosshairSize * 3.2f);
            int barY = cy + GAP_PX + len + 4;
            int filled = Mth.clamp(ClientLockState.progressPercent(), 0, 100) * barWidth / 100;
            HudDraw.fillRect(graphics, cx - barWidth / 2, barY, barWidth, 2, applyAlpha(0x000000, 0.35f));
            HudDraw.fillRect(graphics, cx - barWidth / 2, barY, filled, 2, argb);
        }
    }

    private void renderLockedTarget(GuiGraphics graphics, Minecraft mc, int centerX, int centerY,
                                    Entity target, LockOnQuality quality) {
        Vec3 aimPoint = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        WorldToScreenCache.ScreenPoint screen = WorldToScreenCache.project(aimPoint,
                graphics.guiWidth(), graphics.guiHeight());
        if (screen == null) return;

        int bracketColor = applyAlpha(quality == LockOnQuality.HARD
                ? XenoClientConfig.crosshairColorLocked : XenoClientConfig.crosshairColorLocking,
                XenoClientConfig.crosshairOpacity);

        if (screen.onScreen() && !screen.behindCamera()) {
            drawBrackets(graphics, Math.round(screen.x()), Math.round(screen.y()), bracketColor);
            drawTargetInfo(graphics, mc, Math.round(screen.x()), Math.round(screen.y()), target);
            if (quality == LockOnQuality.HARD) {
                drawLeadMarker(graphics, mc, target, aimPoint);
            }
        } else if (XenoClientConfig.showOffscreenArrow) {
            drawOffscreenArrow(graphics, centerX, centerY, screen, bracketColor);
        }
    }

    private void drawBrackets(GuiGraphics graphics, int x, int y, int argb) {
        int half = BRACKET_HALF_BOX;
        int s = BRACKET_SIZE;
        int t = 1;
        // Four corners, each an L-shape made of two short bars.
        HudDraw.fillRect(graphics, x - half, y - half, s, t, argb);
        HudDraw.fillRect(graphics, x - half, y - half, t, s, argb);
        HudDraw.fillRect(graphics, x + half - s, y - half, s, t, argb);
        HudDraw.fillRect(graphics, x + half - t, y - half, t, s, argb);
        HudDraw.fillRect(graphics, x - half, y + half - t, s, t, argb);
        HudDraw.fillRect(graphics, x - half, y + half - s, t, s, argb);
        HudDraw.fillRect(graphics, x + half - s, y + half - t, s, t, argb);
        HudDraw.fillRect(graphics, x + half - t, y + half - s, t, s, argb);
    }

    private void drawTargetInfo(GuiGraphics graphics, Minecraft mc, int x, int y, Entity target) {
        if (!XenoClientConfig.showTargetName && !XenoClientConfig.showTargetDistance) return;
        double distance = mc.player == null ? 0.0 : mc.player.distanceTo(target);
        if (target.getId() != cachedInfoTargetId || Math.abs(distance - cachedInfoDistance) > 0.5) {
            cachedInfoTargetId = target.getId();
            cachedInfoDistance = distance;
            String name = XenoClientConfig.showTargetName ? target.getDisplayName().getString() : null;
            cachedInfoLine = XenoClientConfig.showTargetDistance
                    ? String.format(Locale.ROOT, "%s%s%.0fm", name == null ? "" : name,
                            name == null ? "" : "  ", distance)
                    : (name == null ? "" : name);
        }
        if (!cachedInfoLine.isEmpty()) {
            int textX = x - mc.font.width(cachedInfoLine) / 2;
            graphics.drawString(mc.font, cachedInfoLine, textX, y + BRACKET_HALF_BOX + 4, 0xFFE8F4FF, true);
        }
    }

    private void drawLeadMarker(GuiGraphics graphics, Minecraft mc, Entity target, Vec3 targetAimPoint) {
        if (!XenoTargetingControls.leadMarkerVisible()) return;
        Vec3 velocity = ClientLockState.velocity();
        if (velocity == null || mc.player == null) return;

        Vec3 shooterVelocity = mc.player.getDeltaMovement()
                .scale(XenoClientConfig.leadAssistShooterVelocityInheritance);
        LeadCalculator.LeadResult lead = LeadCalculator.withGravityDrop(mc.player.getEyePosition(),
                targetAimPoint, velocity, shooterVelocity,
                XenoClientConfig.leadAssistProjectileSpeed,
                XenoClientConfig.leadAssistGravity);
        if (!lead.solvable()) return;

        WorldToScreenCache.ScreenPoint screen = WorldToScreenCache.project(lead.aimPoint(),
                graphics.guiWidth(), graphics.guiHeight());
        if (screen == null || !screen.onScreen() || screen.behindCamera()) return;

        int argb = applyAlpha(XenoClientConfig.crosshairColorLead, XenoClientConfig.crosshairOpacity);
        int x = Math.round(screen.x());
        int y = Math.round(screen.y());
        int r = 2;
        HudDraw.fillRect(graphics, x - r, y - r, r * 2 + 1, 1, argb);
        HudDraw.fillRect(graphics, x - r, y + r, r * 2 + 1, 1, argb);
        HudDraw.fillRect(graphics, x - r, y - r, 1, r * 2 + 1, argb);
        HudDraw.fillRect(graphics, x + r, y - r, 1, r * 2 + 1, argb);
    }

    /**
     * A small triangle at the screen edge, rotated to point toward an off-screen target.
     *
     * <p>Built from the unclamped NDC direction (which still points the right way even past
     * &plusmn;1) rather than from screen pixel deltas, since a point behind the camera would
     * otherwise point exactly backwards.
     */
    private void drawOffscreenArrow(GuiGraphics graphics, int centerX, int centerY,
                                    WorldToScreenCache.ScreenPoint screen, int argb) {
        // No behind-camera correction here: WorldToScreenCache.project already returns a
        // direction-correct (if far off-viewport) point for w <= 0, so this is simply the
        // direction from the reticle to the target in every case.
        double dx = screen.x() - centerX;
        double dy = screen.y() - centerY;
        if (Math.abs(dx) < 1.0e-4 && Math.abs(dy) < 1.0e-4) return;
        double angleRad = Math.atan2(dy, dx);

        int margin = 20;
        int radius = Math.min(centerX, centerY) - margin;
        int arrowX = centerX + (int) Math.round(Math.cos(angleRad) * radius);
        int arrowY = centerY + (int) Math.round(Math.sin(angleRad) * radius);

        graphics.pose().pushPose();
        graphics.pose().translate(arrowX, arrowY, 0);
        graphics.pose().mulPose(new org.joml.Quaternionf().rotateZ((float) angleRad + Mth.HALF_PI));
        int s = 5;
        HudDraw.fillRect(graphics, -1, -s, 2, s * 2, argb);
        HudDraw.fillRect(graphics, -s, 0, s * 2, 1, argb);
        graphics.pose().popPose();
    }

    private void drawErrorText(GuiGraphics graphics, Minecraft mc, int centerX, int centerY,
                               LockOnValidator.Reason reason) {
        String text = ClientLockState.describe(reason);
        if (text.isEmpty()) return;
        int textX = centerX - mc.font.width(text) / 2;
        graphics.drawString(mc.font, text, textX, centerY + 16, 0xFFFF8080, true);
    }

    private static int applyAlpha(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255f), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}
