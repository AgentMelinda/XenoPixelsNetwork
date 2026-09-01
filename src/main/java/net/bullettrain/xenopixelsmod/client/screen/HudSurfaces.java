package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.XenoCooldownHudOverlay;
import net.bullettrain.xenopixelsmod.client.XenoHudOverlay;
import net.bullettrain.xenopixelsmod.client.XenoTechniqueHotbarOverlay;
import net.bullettrain.xenopixelsmod.client.config.PartLayout;
import net.bullettrain.xenopixelsmod.client.config.XenoCooldownHudConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHotbarConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The three editable HUD surfaces, adapted to {@link PartLayout}.
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

    public static final PartLayout[] ALL = {PANEL_LAYOUT, CHIPS_LAYOUT, KI_MENU_LAYOUT};
}
