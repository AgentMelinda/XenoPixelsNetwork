package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.client.render.layer.DMZSkinLayer;
import com.dragonminez.common.hair.CustomHair;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoCodeArea;
import net.bullettrain.xenopixelsmod.hair.HairStrandModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Full-body maker preview of the <b>true local-player</b> DMZ model stack.
 *
 * <p>Path matches DMZ {@code HairEditorScreen#renderPlayerModel} and in-repo
 * {@code XenoNeonStatsScreen} / {@code NpcFullDmzRenderer}:
 * {@code EntityPreviewRenderContext#renderEntityInInventory} with
 * {@code DMZSkinLayer.PREVIEW_MODE} so Gecko/DMZ skins actually draw in GUI.
 *
 * <p>Screens feed {@link MakerPreviewAppearance} so edits update the model live;
 * Character is snapshotted and restored every frame (no C2S).
 *
 * <p><b>Not verified in a running game.</b>
 */
public final class MakerPreviewController {
    private static final int GLOW_OUTER = 0x9900C853;
    private static final int GLOW_INNER = 0x5500E676;
    private static final int GLOW_PAD = 3;
    private static final int WELL_FILL = 0xCC0A1210;
    private static final int WELL_EDGE = 0xFF00C853;

    private static final int MUTED = 0xFF8AA090;
    /** Selected strand edge outline (hollow — no filled cube tint). */
    private static final int SEGMENT_EDGE = 0xFF00E676;
    private static final int SEGMENT_EDGE_DIM = 0x9900C853;
    private static final float ZOOM_MIN = 0.55f;
    private static final float ZOOM_MAX = 2.4f;

    public enum GlowTarget {
        NONE,
        RACE_CARD,
        FORM_ROW,
        PART_CATEGORY,
        HAIR_SEGMENT
    }

    private final PreviewDebounce debounce = new PreviewDebounce();

    private Minecraft minecraft;
    private LocalPlayer boundPlayer;
    private GlowTarget glowTarget = GlowTarget.NONE;
    private String glowId = "";
    private String highlightFace = "";
    private int highlightIndex = -1;
    private HairStrandModel highlightStrand;
    private MakerPreviewAppearance appearance = new MakerPreviewAppearance();
    private boolean dirty;
    private float yaw = 180.0f;
    /** Preview zoom multiplier (mouse wheel in the well). */
    private float zoom = 1.0f;
    /** Pan so zoom can stay anchored under the cursor. */
    private float panX;
    private float panY;
    private float lastBaseScale = 60f;
    private int lastWellX;
    private int lastWellY;
    private int lastWellW;
    private int lastWellH;
    private int lastEntityX;
    private int lastEntityY;
    private float lastScale = 1f;
    private static volatile boolean previewErrorLogged;

    public void bindLocalPlayer(Minecraft mc) {
        this.minecraft = mc;
        this.boundPlayer = mc == null ? null : mc.player;
        markDirty();
    }

    public void setGlow(GlowTarget kind, String id) {
        this.glowTarget = kind == null ? GlowTarget.NONE : kind;
        this.glowId = id == null ? "" : id;
        if (this.glowTarget != GlowTarget.HAIR_SEGMENT) {
            this.highlightFace = "";
            this.highlightIndex = -1;
            this.highlightStrand = null;
        }
    }

    /**
     * Per-segment viewport highlight: stores projection data for green <b>edge</b> markers
     * (strand cubes keep their authored colour).
     */
    public void setHairSegmentHighlight(String face, int index, HairStrandModel strand) {
        this.glowTarget = GlowTarget.HAIR_SEGMENT;
        this.highlightFace = face == null ? "" : face.trim();
        this.highlightIndex = index;
        this.highlightStrand = strand == null ? null : strand.copy();
        this.glowId = highlightFace + ":" + highlightIndex
                + (highlightStrand == null ? "" : ":id" + highlightStrand.id());
    }

    public GlowTarget glowTarget() {
        return glowTarget;
    }

    public String glowId() {
        return glowId;
    }

    public String highlightFace() {
        return highlightFace;
    }

    public int highlightIndex() {
        return highlightIndex;
    }

    /** Replace the live-preview appearance payload (hair/colours/form). */
    public void setAppearance(MakerPreviewAppearance next) {
        this.appearance = next == null ? new MakerPreviewAppearance() : next;
        markDirty();
    }

    public MakerPreviewAppearance appearance() {
        return appearance;
    }

    public boolean isBound() {
        return resolvePlayer() != null;
    }

    public boolean isDirty() {
        return dirty || debounce.pending();
    }

    /** Drag / cycle can spin the preview. */
    public void addYaw(float deltaDegrees) {
        yaw = (yaw + deltaDegrees) % 360.0f;
    }

    public float yaw() {
        return yaw;
    }

    /** Zoom toward the well centre (Zoom ± buttons). */
    public void addZoom(float steps) {
        addZoomAt(steps, lastEntityX, lastEntityY);
    }

    /**
     * Zoom toward a UI-space focus point (mouse in the well) so the hair under the cursor
     * stays under the cursor while scale changes.
     */
    public void addZoomAt(float steps, float focusUiX, float focusUiY) {
        float oldZoom = zoom;
        float newZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom + steps * 0.12f));
        if (Math.abs(newZoom - oldZoom) < 1.0e-4f) {
            return;
        }
        float oldScale = Math.max(1f, lastBaseScale * oldZoom);
        float newScale = Math.max(1f, lastBaseScale * newZoom);
        float ex = lastEntityX;
        float ey = lastEntityY;
        if (lastScale > 0.01f && lastWellW > 0) {
            float relX = (focusUiX - ex) / oldScale;
            float relY = (focusUiY - ey) / oldScale;
            float newEx = focusUiX - relX * newScale;
            float newEy = focusUiY - relY * newScale;
            panX += newEx - ex;
            panY += newEy - ey;
        }
        zoom = newZoom;
    }

    public float zoom() {
        return zoom;
    }

    public float panX() {
        return panX;
    }

    public float panY() {
        return panY;
    }

    /** Reset pan (keeps zoom). */
    public void resetPan() {
        panX = 0f;
        panY = 0f;
    }

    /** Hit radius in UI pixels for strand pick — grows when zoomed out, shrinks when zoomed in. */
    public float pickHitRadiusPx() {
        return HairStrandPick.hitRadiusPxForScale(lastScale <= 0.01f ? 60f : lastScale);
    }

    public boolean containsUiPoint(int uiX, int uiY) {
        return uiX >= lastWellX && uiX < lastWellX + lastWellW
                && uiY >= lastWellY && uiY < lastWellY + lastWellH;
    }

    public int lastWellX() {
        return lastWellX;
    }

    public int lastWellY() {
        return lastWellY;
    }

    public int lastWellW() {
        return lastWellW;
    }

    public int lastWellH() {
        return lastWellH;
    }

    /** Inventory feet pivot used on the last {@link #render} (for strand XYZ pick). */
    public int lastEntityX() {
        return lastEntityX;
    }

    public int lastEntityY() {
        return lastEntityY;
    }

    public float lastScale() {
        return lastScale;
    }

    public void markDirty() {
        dirty = true;
        debounce.markChanged(System.currentTimeMillis());
    }

    /**
     * Draws the preview well (always visible), glow, then the live local player.
     * {@code x,y,w,h} are UI-space coords inside an active {@code ScaledScreen} beginUiScale.
     */
    public void render(GuiGraphics g, int x, int y, int w, int h, float partial) {
        if (g == null || w <= 0 || h <= 0) {
            return;
        }
        lastWellX = x;
        lastWellY = y;
        lastWellW = w;
        lastWellH = h;

        long now = System.currentTimeMillis();
        if (debounce.shouldFire(now)) {
            debounce.clear();
            dirty = false;
            refreshBoundPlayer();
        }

        // Always paint a readable well so a failed entity draw is still obvious.
        g.fill(x, y, x + w, y + h, WELL_FILL);
        g.fill(x, y, x + w, y + 1, WELL_EDGE);
        g.fill(x, y + h - 1, x + w, y + h, WELL_EDGE);
        g.fill(x, y, x + 1, y + h, WELL_EDGE);
        g.fill(x + w - 1, y, x + w, y + h, WELL_EDGE);

        // Well-border glow for race/form cards. Hair uses per-segment green markers instead.
        if (glowTarget != GlowTarget.NONE && glowTarget != GlowTarget.HAIR_SEGMENT) {
            drawGlowRect(g, x, y, w, h);
        }

        LocalPlayer player = resolvePlayer();
        Font font = Minecraft.getInstance().font;
        if (player == null) {
            g.drawCenteredString(font, "No local player", x + w / 2, y + h / 2 - 4, MUTED);
            return;
        }

        // Fit tall SSJ hair inside the well; wheel zoom multiplies base scale + pan toward mouse.
        int baseScale = Math.max(40, Math.min(88, Math.round(h * 0.42f)));
        lastBaseScale = baseScale;
        int scale = Math.max(28, Math.min(160, Math.round(baseScale * zoom)));
        int entityX = Math.round(x + w / 2f + panX);
        // Feet near bottom of the well (inventory / HairEditor convention).
        int entityY = Math.round(y + h - Math.max(8, h / 12) + panY);
        lastEntityX = entityX;
        lastEntityY = entityY;
        lastScale = scale;

        float bodyRot = player.yBodyRot;
        float bodyRotO = player.yBodyRotO;
        float yRot = player.getYRot();
        float yRotO = player.yRotO;
        float xRot = player.getXRot();
        float xRotO = player.xRotO;
        float headRot = player.yHeadRot;
        float headRotO = player.yHeadRotO;

        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(0.0f);
        player.xRotO = 0.0f;
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;

        // Neon/HairEditor: rotateZ(PI), then mul camera; empty translation.
        Quaternionf camera = new Quaternionf().rotateX(0.0f);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        pose.mul(camera);
        Vector3f translation = new Vector3f();

        MakerPreviewAppearance.Snapshot snapshot = appearance.snapshot(player);
        boolean oldPreview = DMZSkinLayer.PREVIEW_MODE;
        DMZSkinLayer.PREVIEW_MODE = true;
        // Clip in GUI space via pose-aware scissor (ScaledScreen UI ≠ raw enableScissor).
        // Without this, tall hair paints over Outliner/Inspector and steals clicks.
        XenoCodeArea.scissor(g, x + 1, y + 1, x + w - 1, y + h - 1);
        g.pose().pushPose();
        // HairEditor/Neon use z=150 so the model sits above the well fill.
        g.pose().translate(0.0, 0.0, 150.0);
        boolean drew = false;
        try {
            appearance.apply(player);
            net.bullettrain.xenopixelsmod.dmz.form.MakerFormPreviewContext.draw(
                    MakerPreviewAppearance.characterOf(player), appearance.formData(),
                    () -> EntityPreviewRenderContext.renderEntityInInventory(
                            g, entityX, entityY, (float) scale, translation, pose, camera, player));
            drew = true;
        } catch (RuntimeException | LinkageError ex) {
            g.drawCenteredString(font, "Preview error", x + w / 2, y + h / 2 - 4, 0xFFFF8A80);
            g.drawCenteredString(font, ex.getClass().getSimpleName(),
                    x + w / 2, y + h / 2 + 8, MUTED);
            if (!previewErrorLogged) {
                previewErrorLogged = true;
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn(
                        "MakerPreviewController failed once: {}", ex.toString(), ex);
            }
        } finally {
            try {
                appearance.restore(player, snapshot);
            } catch (RuntimeException | LinkageError restoreEx) {
                if (!previewErrorLogged) {
                    previewErrorLogged = true;
                    net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn(
                            "MakerPreviewController restore failed once: {}", restoreEx.toString());
                }
            }
            g.pose().popPose();
            g.disableScissor();
            DMZSkinLayer.PREVIEW_MODE = oldPreview;
            player.yBodyRot = bodyRot;
            player.yBodyRotO = bodyRotO;
            player.setYRot(yRot);
            player.yRotO = yRotO;
            player.setXRot(xRot);
            player.xRotO = xRotO;
            player.yHeadRot = headRot;
            player.yHeadRotO = headRotO;
        }

        // The well is the 3D model only. Screens own titles above the box so
        // "3D · zoom" never stacks on "Preview" / "Your character".
        if (!drew) {
            g.drawCenteredString(font, "3D failed — see log", x + w / 2, y + 8, 0xFFFF8A80);
        }
    }

    private void refreshBoundPlayer() {
        if (minecraft != null) {
            boundPlayer = minecraft.player;
        }
    }

    private LocalPlayer resolvePlayer() {
        if (boundPlayer != null && !boundPlayer.isRemoved()) {
            return boundPlayer;
        }
        Minecraft mc = minecraft != null ? minecraft : Minecraft.getInstance();
        return mc == null ? null : mc.player;
    }

    /**
     * Green <b>edge outline</b> around the selected hair strand (viewport only).
     * This is not the left Outliner panel — a wireframe sleeve along the strand cubes.
     * Scissor already clips to the well; do not skip points or the outline vanishes.
     */
    private void drawSelectedSegmentMarkers(GuiGraphics g, int entityX, int entityY, float scale) {
        if (highlightStrand == null || !highlightStrand.visible() || highlightFace.isEmpty()) {
            return;
        }
        CustomHair.HairFace face;
        try {
            face = CustomHair.HairFace.valueOf(highlightFace.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return;
        }
        Vector3f basePx = CustomHair.getStrandBasePosition(face, highlightIndex);
        final int samples = 8;
        int[] leftX = new int[samples + 1];
        int[] leftY = new int[samples + 1];
        int[] rightX = new int[samples + 1];
        int[] rightY = new int[samples + 1];
        int valid = 0;
        for (int s = 0; s <= samples; s++) {
            float t = s / (float) samples;
            HairStrandPick.ScreenPoint L = HairStrandPick.projectAlongOffset(
                    basePx, highlightStrand, Math.max(0.001f, t), -1f,
                    entityX, entityY, scale, yaw);
            HairStrandPick.ScreenPoint R = HairStrandPick.projectAlongOffset(
                    basePx, highlightStrand, Math.max(0.001f, t), 1f,
                    entityX, entityY, scale, yaw);
            if (L == null || R == null) {
                continue;
            }
            leftX[valid] = Math.round(L.x());
            leftY[valid] = Math.round(L.y());
            rightX[valid] = Math.round(R.x());
            rightY[valid] = Math.round(R.y());
            valid++;
        }
        if (valid < 2) {
            return;
        }
        // Left edge + right edge + base/tip caps = green outline around the strand.
        for (int i = 1; i < valid; i++) {
            drawEdgeLine(g, leftX[i - 1], leftY[i - 1], leftX[i], leftY[i], SEGMENT_EDGE);
            drawEdgeLine(g, rightX[i - 1], rightY[i - 1], rightX[i], rightY[i], SEGMENT_EDGE);
        }
        drawEdgeLine(g, leftX[0], leftY[0], rightX[0], rightY[0], SEGMENT_EDGE);
        drawEdgeLine(g, leftX[valid - 1], leftY[valid - 1],
                rightX[valid - 1], rightY[valid - 1], SEGMENT_EDGE);
        // Corner ticks so the outline reads even on thin strands.
        drawHollowRect(g, leftX[0], leftY[0], 2, SEGMENT_EDGE);
        drawHollowRect(g, rightX[0], rightY[0], 2, SEGMENT_EDGE);
        drawHollowRect(g, leftX[valid - 1], leftY[valid - 1], 3, SEGMENT_EDGE);
        drawHollowRect(g, rightX[valid - 1], rightY[valid - 1], 3, SEGMENT_EDGE);
    }

    private static void drawHollowRect(GuiGraphics g, int cx, int cy, int r, int color) {
        g.fill(cx - r, cy - r, cx + r + 1, cy - r + 1, color);
        g.fill(cx - r, cy + r, cx + r + 1, cy + r + 1, color);
        g.fill(cx - r, cy - r, cx - r + 1, cy + r + 1, color);
        g.fill(cx + r, cy - r, cx + r + 1, cy + r + 1, color);
    }

    private static void drawEdgeLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps <= 0) {
            g.fill(x0, y0, x0 + 2, y0 + 2, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            g.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static void drawGlowRect(GuiGraphics g, int x, int y, int w, int h) {
        int x0 = x - GLOW_PAD;
        int y0 = y - GLOW_PAD;
        int x1 = x + w + GLOW_PAD;
        int y1 = y + h + GLOW_PAD;
        g.fill(x0, y0, x1, y0 + 2, GLOW_OUTER);
        g.fill(x0, y1 - 2, x1, y1, GLOW_OUTER);
        g.fill(x0, y0, x0 + 2, y1, GLOW_OUTER);
        g.fill(x1 - 2, y0, x1, y1, GLOW_OUTER);
        g.fill(x0 + 2, y0 + 2, x1 - 2, y0 + 3, GLOW_INNER);
        g.fill(x0 + 2, y1 - 3, x1 - 2, y1 - 2, GLOW_INNER);
        g.fill(x0 + 2, y0 + 2, x0 + 3, y1 - 2, GLOW_INNER);
        g.fill(x1 - 3, y0 + 2, x1 - 2, y1 - 2, GLOW_INNER);
    }
}
