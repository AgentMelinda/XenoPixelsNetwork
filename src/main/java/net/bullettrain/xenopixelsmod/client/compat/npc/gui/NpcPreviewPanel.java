package net.bullettrain.xenopixelsmod.client.compat.npc.gui;

import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraResolver;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Always-present, aura-enabled NPC visualizer shared by the wand editor tabs.
 *
 * <p>Owns its own hit-testing and drawing, so a host screen only has to park it beside the 420px
 * {@code GuiNPCInterface2} body and forward mouse events. Aura on/off is visualizer-local: it
 * never writes the NPC's persisted aura flag, so merely opening a tab cannot mutate the NPC.
 * The persisted aura toggle stays wherever the host screen already exposes it.
 */
public final class NpcPreviewPanel {
    public static final int GAP = 8;
    public static final int WIDTH = 150;
    public static final int HEIGHT = 200;
    private static final int HEADER_H = 15;
    private static final int MODEL_BASE = 132;
    private static final int CONTROL_Y = 145;
    private static final int HINT_INSET = 11;

    /** Host hook for the CNPC colour sub-screen; {@code currentRgb} seeds the picker. */
    public interface Listener {
        void auraColorRequested(int currentRgb);
    }

    // Deliberately LivingEntity, not either mod's EntityNPCInterface: this class is shared by
    // the CustomNPCs and My NPCs trees, and both NPC types are LivingEntities.
    private final LivingEntity npc;
    private final Listener listener;
    private NpcCombatProfile profile;
    private boolean auraOn = true;
    private float yaw = 180.0F;
    private float pitch;
    private float zoom = 1.0F;
    private boolean dragging;

    public NpcPreviewPanel(LivingEntity npc, Listener listener) {
        this.npc = npc;
        this.listener = listener;
    }

    public NpcCombatProfile profile() {
        return profile;
    }

    /** Rebinds the visual source and re-applies it to the client-side appearance cache. */
    public void profile(NpcCombatProfile profile) {
        this.profile = profile;
        if (profile != null && npc != null) {
            NpcAppearanceClient.applyProfile(npc.getUUID(), profile);
        }
    }

    public boolean auraOn() {
        return auraOn;
    }

    /**
     * Left edge of the panel, pulled back inside the scaled viewport when the 420px body is
     * wide enough that {@code guiLeft + 420 + GAP} would run past the right edge. At the
     * default GUI scale a centred 420px body leaves no room on either side, so an unclamped
     * panel was drawn entirely off-screen and read as "the visualizer is missing".
     */
    public int left(int guiLeft) {
        int desired = guiLeft + 420 + GAP;
        // Headless tests have no client instance or window; the placement contract is still the
        // raw offset there, so fall back to it rather than dereferencing a null client.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return desired;
        }
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        return Math.max(2, Math.min(desired, screenWidth - WIDTH - 2));
    }

    public int top(int guiTop) {
        return guiTop;
    }

    /** The model window, not the whole panel: the controls row sits below the scissor box. */
    public boolean inside(int guiLeft, int guiTop, double mouseX, double mouseY) {
        int left = left(guiLeft);
        return mouseX >= left && mouseX < left + WIDTH
                && mouseY >= guiTop && mouseY < guiTop + MODEL_BASE;
    }

    public boolean mouseClicked(int guiLeft, int guiTop, double mouseX, double mouseY, int button) {
        if (button != 0 || profile == null) {
            return false;
        }
        int left = left(guiLeft);
        if (inside(guiLeft, guiTop, mouseX, mouseY)) {
            dragging = true;
            return true;
        }
        if (hit(mouseX, mouseY, left + 6, guiTop + CONTROL_Y, 44, 16)) {
            auraOn = !auraOn;
            return true;
        }
        if (listener != null && hit(mouseX, mouseY, left + 84, guiTop + CONTROL_Y, 60, 16)) {
            listener.auraColorRequested(auraRgb());
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button != 0 || !dragging) {
            return false;
        }
        yaw += (float) dragX * 1.5F;
        pitch = Mth.clamp(pitch - (float) dragY, -30.0F, 30.0F);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button != 0 || !dragging) {
            return false;
        }
        dragging = false;
        return true;
    }

    public boolean mouseScrolled(int guiLeft, int guiTop, double mouseX, double mouseY,
                                 double scrollY) {
        if (!inside(guiLeft, guiTop, mouseX, mouseY)) {
            return false;
        }
        zoom = Mth.clamp(zoom + (float) scrollY * 0.08F, 0.65F, 1.75F);
        return true;
    }

    public void render(GuiGraphics graphics, Font font, int guiLeft, int guiTop,
                       float partialTick) {
        if (profile == null || npc == null) {
            return;
        }
        int left = left(guiLeft);
        int top = top(guiTop);
        int right = left + WIDTH;
        int bottom = top + HEIGHT;
        graphics.fill(left, top, right, bottom, 0xFF101218);
        graphics.fill(left, top, right, top + 1, 0xFFB98235);
        graphics.fill(left, bottom - 1, right, bottom, 0xFFB98235);
        graphics.fill(left, top, left + 1, bottom, 0xFFB98235);
        graphics.fill(right - 1, top, right, bottom, 0xFFB98235);
        graphics.drawCenteredString(font, "Live NPC", left + WIDTH / 2, top + 4, 0xFFE2C078);

        graphics.drawCenteredString(font, clip(auraCaption(), 26),
                left + WIDTH / 2, top + MODEL_BASE, 0xFFAAAAAA);
        graphics.drawCenteredString(font, "Drag rotate  •  Wheel zoom",
                left + WIDTH / 2, bottom - HINT_INSET, 0xFF777777);

        int scale = Math.max(20, Math.round(54.0F * zoom));
        graphics.enableScissor(left + 2, top + HEADER_H, right - 2, top + MODEL_BASE);
        try {
            NpcFullDmzRenderer.renderPreview(npc, graphics, left + WIDTH / 2,
                    top + MODEL_BASE, scale, yaw, pitch, partialTick, auraOn);
        } finally {
            graphics.disableScissor();
        }

        drawToggle(graphics, font, left + 6, top + CONTROL_Y, 44, 16, auraOn);
        graphics.drawString(font, "Aura", left + 53, top + CONTROL_Y + 4, 0xFFFFFFFF);
        // Only tabs that can actually route a colour edit to a field show the swatch.
        if (listener != null) {
            drawSwatch(graphics, font, left + 84, top + CONTROL_Y, 60, 16, auraRgb());
        }
    }

    private String auraCaption() {
        NpcAuraResolver.Resolved resolved = NpcAuraResolver.resolve(profile);
        if (resolved.layers().isEmpty()) {
            return "Aura: inherited";
        }
        NpcAuraResolver.Layer last = resolved.layers().get(resolved.layers().size() - 1);
        return "Aura: " + last.type() + "  L" + last.index();
    }

    private int auraRgb() {
        return NpcAuraResolver.resolve(profile).particleRgb();
    }

    private static void drawToggle(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                   boolean value) {
        graphics.fill(x, y, x + w, y + h, 0xFF1A1F27);
        graphics.fill(x, y, x + w, y + 1, 0xFFB98235);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFFB98235);
        graphics.fill(x, y, x + 1, y + h, 0xFFB98235);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFFB98235);
        String label = value ? "Yes" : "No";
        graphics.drawCenteredString(font, label, x + w / 2, y + 4, 0xFFFFFFFF);
    }

    private static void drawSwatch(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                   int rgb) {
        graphics.fill(x, y, x + w, y + h, 0xFF1A1F27);
        graphics.fill(x + 3, y + 3, x + 19, y + h - 3, 0xFF000000 | (rgb & 0xFFFFFF));
        graphics.fill(x, y, x + w, y + 1, 0xFFB98235);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFFB98235);
        graphics.fill(x, y, x + 1, y + h, 0xFFB98235);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFFB98235);
        graphics.drawString(font, NpcCombatProfile.formatHex(rgb), x + 24, y + 4, 0xFFFFFFFF);
    }

    private static boolean hit(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
