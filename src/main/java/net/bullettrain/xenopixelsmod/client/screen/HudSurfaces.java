package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.XenoCooldownHudOverlay;
import net.bullettrain.xenopixelsmod.client.XenoHudOverlay;
import net.bullettrain.xenopixelsmod.client.XenoPartyOverlay;
import net.bullettrain.xenopixelsmod.client.XenoTechniqueHotbarOverlay;
import net.bullettrain.xenopixelsmod.client.config.PartLayout;
import net.bullettrain.xenopixelsmod.client.config.XenoCooldownHudConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHotbarConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoDmzScreenConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoPartyHudConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The editable HUD and screen surfaces, adapted to {@link PartLayout}.
 *
 * <p>These adapters live here rather than on the config classes so the configs stay pure data and do
 * not gain a dependency on the overlays they are drawn by. Each one only forwards — no field moves,
 * no serialisation change — so a surface can be added by writing one more adapter and appending it
 * to {@link #ALL}.
 */
@OnlyIn(Dist.CLIENT)
public final class HudSurfaces {
    public static final int PANEL = 0;
    public static final int CHIPS = 1;
    public static final int KI_MENU = 2;
    public static final int PARTY = 3;
    public static final int DMZ_SCREEN = 4;
    public static final int DMZ_NEON = 5;

    private HudSurfaces() {}

    /** The main HP/KI/STM panel: portrait, name, level, form, and every bar and readout. */
    public static final PartLayout PANEL_LAYOUT = new PartLayout() {
        @Override public String name() { return "Panel"; }
        @Override public int partCount() { return XenoHudConfig.Part.COUNT; }
        @Override public String partName(int part) { return XenoHudConfig.Part.NAMES[part]; }
        @Override public int[] x() { return XenoHudConfig.partX; }
        @Override public int[] y() { return XenoHudConfig.partY; }
        @Override public float[] scale() { return XenoHudConfig.partScale; }
        @Override public int[] color() { return XenoHudConfig.partColor; }
        @Override public boolean[] bold() { return XenoHudConfig.partBold; }
        @Override public String[] font() { return XenoHudConfig.partFont; }
        @Override public int defaultColor(int part) { return XenoHudConfig.defaultPartColor(part); }
        @Override public void resetPart(int part) { XenoHudConfig.resetPart(part); }
        @Override public void resetAll() { XenoHudConfig.resetParts(); }
        @Override public boolean customLayout() { return XenoHudConfig.customLayout; }
        @Override public void setCustomLayout(boolean on) { XenoHudConfig.customLayout = on; }
        @Override public int clampOffset(int v) { return XenoHudConfig.clampPartOffset(v); }
        @Override public void save() { XenoHudConfig.save(); }

        @Override
        public void drawPreview(GuiGraphics g, int w, int h) {
            XenoHudOverlay.renderHud(g, w, h, true);
        }

        @Override
        public int[] bounds() {
            return new int[]{XenoHudConfig.x, XenoHudConfig.y,
                    XenoHudConfig.scaledWidth(), XenoHudConfig.scaledHeight()};
        }
    };

    /** The BT3 combat chip strip: key, label, counter and meter, shared by every chip. */
    public static final PartLayout CHIPS_LAYOUT = new PartLayout() {
        @Override public String name() { return "Chips"; }
        @Override public int partCount() { return XenoCooldownHudConfig.Part.COUNT; }
        @Override public String partName(int part) { return XenoCooldownHudConfig.Part.NAMES[part]; }
        @Override public int[] x() { return XenoCooldownHudConfig.partX; }
        @Override public int[] y() { return XenoCooldownHudConfig.partY; }
        @Override public float[] scale() { return XenoCooldownHudConfig.partScale; }
        @Override public int[] color() { return XenoCooldownHudConfig.partColor; }
        @Override public boolean[] bold() { return XenoCooldownHudConfig.partBold; }
        @Override public String[] font() { return XenoCooldownHudConfig.partFont; }
        @Override public int defaultColor(int part) { return XenoCooldownHudConfig.defaultPartColor(part); }
        @Override public void resetPart(int part) { XenoCooldownHudConfig.resetPart(part); }
        @Override public void resetAll() { XenoCooldownHudConfig.resetTextLayout(); }
        @Override public boolean customLayout() { return XenoCooldownHudConfig.textCustomLayout; }
        @Override public void setCustomLayout(boolean on) { XenoCooldownHudConfig.textCustomLayout = on; }
        @Override public int clampOffset(int v) { return XenoCooldownHudConfig.clampTextOffset(v); }
        @Override public void save() { XenoCooldownHudConfig.save(); }

        @Override
        public void drawPreview(GuiGraphics g, int w, int h) {
            XenoCooldownHudOverlay.renderEditorPreview(g, w, h);
        }

        @Override
        public int[] bounds() {
            return XenoCooldownHudOverlay.bounds();
        }
    };

    /** The Alt / Ctrl ki technique menu: header, counter, and the pieces of each row. */
    public static final PartLayout KI_MENU_LAYOUT = new PartLayout() {
        @Override public String name() { return "Ki Menu"; }
        @Override public int partCount() { return XenoHotbarConfig.Part.COUNT; }
        @Override public String partName(int part) { return XenoHotbarConfig.Part.NAMES[part]; }
        @Override public int[] x() { return XenoHotbarConfig.partX; }
        @Override public int[] y() { return XenoHotbarConfig.partY; }
        @Override public float[] scale() { return XenoHotbarConfig.partScale; }
        @Override public int[] color() { return XenoHotbarConfig.partColor; }
        @Override public boolean[] bold() { return XenoHotbarConfig.partBold; }
        @Override public String[] font() { return XenoHotbarConfig.partFont; }
        @Override public int defaultColor(int part) { return XenoHotbarConfig.defaultPartColor(part); }
        @Override public void resetPart(int part) { XenoHotbarConfig.resetPart(part); }
        @Override public void resetAll() { XenoHotbarConfig.resetParts(); }
        @Override public boolean customLayout() { return XenoHotbarConfig.customLayout; }
        @Override public void setCustomLayout(boolean on) { XenoHotbarConfig.customLayout = on; }
        @Override public int clampOffset(int v) { return XenoHotbarConfig.clampPartOffset(v); }
        @Override public void save() { XenoHotbarConfig.save(); }

        @Override
        public void drawPreview(GuiGraphics g, int w, int h) {
            XenoTechniqueHotbarOverlay.renderEditorPreview(g, w, h);
        }

        @Override
        public int[] bounds() {
            return XenoTechniqueHotbarOverlay.hotbarBounds();
        }
    };

    /** The nearby-party card stack: portrait, name, level, leader star, form, sparking and gauges. */
    public static final PartLayout PARTY_LAYOUT = new PartLayout() {
        @Override public String name() { return "Party"; }
        @Override public int partCount() { return XenoPartyHudConfig.Part.COUNT; }
        @Override public String partName(int part) { return XenoPartyHudConfig.Part.NAMES[part]; }
        @Override public int[] x() { return XenoPartyHudConfig.partX; }
        @Override public int[] y() { return XenoPartyHudConfig.partY; }
        @Override public float[] scale() { return XenoPartyHudConfig.partScale; }
        @Override public int[] color() { return XenoPartyHudConfig.partColor; }
        @Override public boolean[] bold() { return XenoPartyHudConfig.partBold; }
        @Override public String[] font() { return XenoPartyHudConfig.partFont; }
        @Override public int defaultColor(int part) { return XenoPartyHudConfig.defaultPartColor(part); }
        @Override public void resetPart(int part) { XenoPartyHudConfig.resetPart(part); }
        @Override public void resetAll() { XenoPartyHudConfig.resetParts(); }
        @Override public boolean customLayout() { return XenoPartyHudConfig.customLayout; }
        @Override public void setCustomLayout(boolean on) { XenoPartyHudConfig.customLayout = on; }
        @Override public int clampOffset(int v) { return XenoPartyHudConfig.clampPartOffset(v); }
        @Override public void save() { XenoPartyHudConfig.save(); }

        @Override
        public void drawPreview(GuiGraphics g, int w, int h) {
            // The editor preview shows the stack as it draws in play, with its demo card forced on
            // so there is always something to aim at even when nobody is partied up.
            XenoPartyOverlay.renderCards(g, true);
        }

        @Override
        public int[] bounds() {
            return new int[]{XenoPartyHudConfig.x, XenoPartyHudConfig.y,
                    XenoPartyHudConfig.scaledWidth(), XenoPartyHudConfig.scaledCardHeight()};
        }
    };

    /**
     * The rebuilt DragonMineZ character screen behind V.
     *
     * <p>A screen rather than an overlay, which the editor does not mind: it only asks a surface to
     * draw a preview and report its bounds, and that screen is ours to draw. Its layout lives in its
     * own config, so nothing here reaches into the HUD's.
     */
    public static final PartLayout DMZ_SCREEN_LAYOUT = new PartLayout() {
        @Override public String name() { return "DMZ Screen"; }
        @Override public int partCount() { return XenoDmzScreenConfig.Part.COUNT; }
        @Override public String partName(int part) { return XenoDmzScreenConfig.Part.NAMES[part]; }
        @Override public int[] x() { return XenoDmzScreenConfig.partX; }
        @Override public int[] y() { return XenoDmzScreenConfig.partY; }
        @Override public float[] scale() { return XenoDmzScreenConfig.partScale; }
        @Override public int[] color() { return XenoDmzScreenConfig.partColor; }
        @Override public boolean[] bold() { return XenoDmzScreenConfig.partBold; }
        @Override public String[] font() { return XenoDmzScreenConfig.partFont; }
        @Override public int defaultColor(int part) { return XenoDmzScreenConfig.defaultPartColor(part); }
        @Override public void resetPart(int part) { XenoDmzScreenConfig.resetPart(part); }
        @Override public void resetAll() { XenoDmzScreenConfig.resetParts(); }
        @Override public boolean customLayout() { return XenoDmzScreenConfig.customLayout; }
        @Override public void setCustomLayout(boolean on) { XenoDmzScreenConfig.customLayout = on; }
        @Override public int clampOffset(int v) { return XenoDmzScreenConfig.clampPartOffset(v); }
        @Override public void save() { XenoDmzScreenConfig.save(); }

        @Override
        public void drawPreview(GuiGraphics g, int w, int h) {
            net.bullettrain.xenopixelsmod.client.screen.XenoDmzScreenPreview.render(g, w, h);
        }

        @Override
        public int[] bounds() {
            return net.bullettrain.xenopixelsmod.client.screen.XenoDmzScreenPreview.bounds();
        }
    };

    /**
     * The neon rebuild of that same screen.
     *
     * <p>Its own surface, not a second view of {@link #DMZ_SCREEN_LAYOUT}. The two rebuilds have
     * different pieces in different places and are kept side by side to be compared, so editing one
     * must not move the other.
     */
    public static final PartLayout DMZ_NEON_LAYOUT = new PartLayout() {
        @Override public String name() { return "DMZ Neon"; }
        @Override public int partCount() { return XenoDmzNeonConfig.Part.COUNT; }
        @Override public String partName(int part) { return XenoDmzNeonConfig.Part.NAMES[part]; }
        @Override public int[] x() { return XenoDmzNeonConfig.partX; }
        @Override public int[] y() { return XenoDmzNeonConfig.partY; }
        @Override public float[] scale() { return XenoDmzNeonConfig.partScale; }
        @Override public int[] color() { return XenoDmzNeonConfig.partColor; }
        @Override public boolean[] bold() { return XenoDmzNeonConfig.partBold; }
        @Override public String[] font() { return XenoDmzNeonConfig.partFont; }
        @Override public int defaultColor(int part) { return XenoDmzNeonConfig.defaultPartColor(part); }
        @Override public void resetPart(int part) { XenoDmzNeonConfig.resetPart(part); }
        @Override public void resetAll() { XenoDmzNeonConfig.resetParts(); }
        @Override public boolean customLayout() { return XenoDmzNeonConfig.customLayout; }
        @Override public void setCustomLayout(boolean on) { XenoDmzNeonConfig.customLayout = on; }
        @Override public int clampOffset(int v) { return XenoDmzNeonConfig.clampPartOffset(v); }
        @Override public void save() { XenoDmzNeonConfig.save(); }

        @Override
        public void drawPreview(GuiGraphics g, int w, int h) {
            net.bullettrain.xenopixelsmod.client.screen.XenoNeonScreenPreview.render(g, w, h);
        }

        @Override
        public int[] bounds() {
            return net.bullettrain.xenopixelsmod.client.screen.XenoNeonScreenPreview.bounds();
        }
    };

    public static final PartLayout[] ALL =
            {PANEL_LAYOUT, CHIPS_LAYOUT, KI_MENU_LAYOUT, PARTY_LAYOUT, DMZ_SCREEN_LAYOUT,
                    DMZ_NEON_LAYOUT};
}
