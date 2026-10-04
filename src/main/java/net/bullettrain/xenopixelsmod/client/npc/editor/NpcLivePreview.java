package net.bullettrain.xenopixelsmod.client.npc.editor;

import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.bullettrain.xenopixelsmod.client.screen.StudioViewport;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The live NPC visualizer, as a piece any native screen can place.
 *
 * <p>This was inline in {@code XenoNpcEditorScreen}, which is why the appearance screen and the DMZ
 * sub-screens had no preview at all - they are separate {@code ScaledScreen}s and there was nothing
 * to reuse. Everything the preview needs now lives here: the panel art, the orbit state, the hit
 * test and the fallback messages.
 *
 * <h2>Native on purpose</h2>
 * {@code client/compat/npc/gui/NpcPreviewPanel} does a similar job and is deliberately <em>not</em>
 * used. That class belongs to the MyNPCs and CustomNPCs integration and assumes those mods are
 * present; the native NPC system must not depend on that tree. {@link NpcFullDmzRenderer} is a
 * different matter - it is a DragonMineZ integration, and DragonMineZ is a hard dependency.
 */
public final class NpcLivePreview {

    private static final String PANEL = "mynpcs_small_panel";

    private static final int GOLD = 0xFFFFD27F;
    private static final int MUTED = 0xFF9AA6C0;
    private static final int WARN = 0xFFFFB366;

    /** Orbit and zoom, kept per placement so two screens do not share a camera. */
    private final StudioViewport viewport = new StudioViewport();

    private int x;
    private int y;
    private int width;
    private int height;

    /** Whether the aura is drawn. The editor exposes this as a toggle under the panel. */
    private boolean showAura = true;

    /** Places the panel. Call from {@code init()}, before the first render. */
    public void place(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean showAura() {
        return showAura;
    }

    public void showAura(boolean show) {
        this.showAura = show;
    }

    /** Returns the camera to its default angle and zoom. */
    public void reset() {
        viewport.reset();
    }

    /**
     * Whether a <em>canvas-space</em> point is over the panel.
     *
     * <p>Canvas units, not window units: the caller converts through {@code toUiX}/{@code toUiY}
     * first. This is not a widget, so {@code ScaledScreen} does not convert for it - see
     * {@code docs/atlas-ui-doco.md}.
     */
    public boolean contains(double uiX, double uiY) {
        return uiX >= x && uiX < x + width && uiY >= y && uiY < y + height;
    }

    public void beginOrbit(boolean pan) {
        viewport.beginOrbit(pan);
    }

    public void endDrag() {
        viewport.endDrag();
    }

    public boolean drag(double dx, double dy) {
        return viewport.interacting() && viewport.drag(dx, dy);
    }

    public void scroll(double amount) {
        viewport.scroll(amount);
    }

    /**
     * Draws the panel and whatever it can resolve of the NPC.
     *
     * <p>When the entity cannot be resolved the panel says <em>which</em> step failed rather than a
     * bare "not loaded", because the two causes need different fixes: a missing client entity is an
     * id problem, whereas {@code renderPreview} answering false only means DragonMineZ has no
     * appearance state for it yet, and the plain viewport render stands in for that.
     *
     * @param auraOn retained for callers; preview Aura is controlled by the preview toggle
     */
    public void render(GuiGraphics graphics, Font font, int entityId, boolean auraOn,
                       float partialTick) {
        AtlasPanel.fittedInto(PANEL, x, y, width, height).render(graphics);
        graphics.drawCenteredString(font, "Live NPC", x + width / 2, y + 6, GOLD);

        LivingEntity npc = entity(entityId);
        if (npc == null) {
            Minecraft mc = Minecraft.getInstance();
            String why = mc.level == null ? "no client level"
                    : mc.level.getEntity(entityId) == null
                            ? "id " + entityId + " not in client level"
                            : "not a LivingEntity";
            graphics.drawCenteredString(font, "unavailable", x + width / 2,
                    y + height / 2 - 6, WARN);
            graphics.drawCenteredString(font, why, x + width / 2, y + height / 2 + 6, MUTED);
            return;
        }

        int viewTop = y + 18;
        int viewBottom = y + height - 24;
        int centreX = x + width / 2;
        int baseScale = Math.max(20, (viewBottom - viewTop) / 3);

        viewport.drawGround(graphics, x + 5, viewTop, x + width - 5, viewBottom,
                0xFF80D8FF, 0x60FFFFFF);

        boolean drawn = NpcFullDmzRenderer.renderPreview(npc, graphics, centreX, viewBottom - 5,
                baseScale, viewport.yaw(), viewport.pitch(), partialTick, showAura);
        if (!drawn) {
            viewport.render(graphics, x + 5, viewTop, x + width - 5, viewBottom, npc, baseScale);
        }

        graphics.drawCenteredString(font, "Drag rotate - Wheel zoom",
                x + width / 2, y + height - 16, MUTED);
    }

    /** The client-side entity being previewed, or null when it cannot be resolved. */
    public static LivingEntity entity(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Entity entity = mc.level.getEntity(entityId);
        return entity instanceof LivingEntity living ? living : null;
    }
}
