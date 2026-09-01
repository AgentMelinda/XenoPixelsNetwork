package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.config.PartLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * The single per-element editor for every HUD surface.
 *
 * <p>Replaces three near-identical screens, one per surface. They shared their entire control set
 * and differed only in which config they wrote to, which meant a control added to one — the font
 * picker — silently did not exist on the others, and a fix to one had to be copied twice. Here the
 * surface is just a {@link PartLayout} behind a tab, so every control reaches all three by
 * construction.
 *
 * <p>Edits are made against the live config so the preview <em>is</em> the real HUD. That makes
 * discarding the screen's job: the entry state of all three surfaces is captured in the constructor,
 * and both Cancel and Escape put every one of them back. Only Save writes to disk.
 */
@OnlyIn(Dist.CLIENT)
public class XenoElementsEditScreen extends UnblurredScreen {
    /**
     * Fonts the picker walks.
     *
     * <p>The four vanilla faces are always present, and {@code dragonminez:smooth} ships with
     * DragonMineZ, which is a hard dependency — so every entry here is guaranteed to resolve. A
     * resource pack's own font can still be used by editing the config's {@code partFont} directly;
     * this button only cycles the set that cannot fail.
     */
    private static final String[] FONTS = {
            "dragonminez:smooth", "minecraft:default", "minecraft:uniform",
            "minecraft:alt", "minecraft:illageralt"};

    private final Screen parent;
    private final PartLayout[] surfaces = HudSurfaces.ALL;

    /** Entry state of every surface, so Cancel and Escape can undo across tab switches too. */
    private final int[][] priorX;
    private final int[][] priorY;
    private final float[][] priorScale;
    private final int[][] priorColor;
    private final boolean[][] priorBold;
    private final String[][] priorFont;
    private final boolean[] priorCustom;

    private int surface;
    private final int[] selectedPerSurface;

    private HexColorBox hexBox;
    private boolean dragging;
    private int dragOffX;
    private int dragOffY;

    public XenoElementsEditScreen(Screen parent) {
        this(parent, HudSurfaces.PANEL);
    }

    public XenoElementsEditScreen(Screen parent, int surface) {
        super(Component.literal("Edit HUD Elements"));
        this.parent = parent;
        this.surface = Math.max(0, Math.min(surfaces.length - 1, surface));

        int n = surfaces.length;
        priorX = new int[n][];
        priorY = new int[n][];
        priorScale = new float[n][];
        priorColor = new int[n][];
        priorBold = new boolean[n][];
        priorFont = new String[n][];
        priorCustom = new boolean[n];
        selectedPerSurface = new int[n];
        for (int i = 0; i < n; i++) {
            PartLayout s = surfaces[i];
            priorX[i] = s.x().clone();
            priorY[i] = s.y().clone();
            priorScale[i] = s.scale().clone();
            priorColor[i] = s.color().clone();
            priorBold[i] = s.bold().clone();
            priorFont[i] = s.font().clone();
            priorCustom[i] = s.customLayout();
        }
        // Only the tab being opened is switched on. Turning all three on would change how the other
        // two render the moment this screen opens, which is not what "edit the panel" asked for.
        surfaces[this.surface].setCustomLayout(true);
    }

    private PartLayout active() {
        return surfaces[surface];
    }

    private int selected() {
        return selectedPerSurface[surface];
    }

    /**
     * Centred {@code [x, w]} pairs for one row of controls, shrunk to fit the screen.
     *
     * <p>Fixed {@code centre ± n} offsets only work if the screen is at least as wide as the widest
     * row. At a high GUI scale it is not, and the rows ran off both edges and overlapped each other.
     * Laying each row out from the real width instead means the editor is usable at any resolution.
     */
    private int[] rowSlots(int[] widths) {
        int gap = 4;
        int margin = 4;
        int natural = 0;
        for (int w : widths) natural += w;
        int avail = this.width - margin * 2 - gap * (widths.length - 1);
        float k = natural > avail ? avail / (float) natural : 1f;

        int[] slots = new int[widths.length * 2];
        int total = gap * (widths.length - 1);
        for (int i = 0; i < widths.length; i++) {
            slots[i * 2 + 1] = Math.max(16, Math.round(widths[i] * k));
            total += slots[i * 2 + 1];
        }
        int x = Math.max(margin, (this.width - total) / 2);
        for (int i = 0; i < widths.length; i++) {
            slots[i * 2] = x;
            x += slots[i * 2 + 1] + gap;
        }
        return slots;
    }

    /** The clear band between the tab row and the control rows: draggable, and safe to draw in. */
    private int bandTop;
    private int bandBottom;

    @Override
    protected void init() {
        int by = this.height - 26;
        int styleY = by - 23;
        int fontY = styleY - 23;
        int tabY = Math.min(34, fontY - 26);
        bandTop = tabY + 24;
        bandBottom = fontY - 4;

        // Tab row.
        int[] tabW = new int[surfaces.length];
        java.util.Arrays.fill(tabW, 78);
        int[] tabs = rowSlots(tabW);
        for (int i = 0; i < surfaces.length; i++) {
            final int idx = i;
            String label = (i == surface ? "▸ " : "") + surfaces[i].name();
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> selectSurface(idx))
                    .bounds(tabs[i * 2], tabY, tabs[i * 2 + 1], 20).build());
        }

        // Font row: which face this element draws with.
        int[] f = rowSlots(new int[]{76, 76});
        this.addRenderableWidget(Button.builder(Component.literal("◂ Font"), b -> cycleFont(-1))
                .bounds(f[0], fontY, f[1], 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Font ▸"), b -> cycleFont(1))
                .bounds(f[2], fontY, f[3], 20).build());

        // Style row: size, weight and colour.
        int[] s = rowSlots(new int[]{52, 52, 34, 44, 36, 90});
        this.addRenderableWidget(Button.builder(Component.literal("Size -"), b -> nudgeScale(-0.05f))
                .bounds(s[0], styleY, s[1], 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Size +"), b -> nudgeScale(0.05f))
                .bounds(s[2], styleY, s[3], 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("1x"),
                        b -> active().scale()[selected()] = 1.0f)
                .bounds(s[4], styleY, s[5], 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Bold"),
                        b -> active().bold()[selected()] = !active().bold()[selected()])
                .bounds(s[6], styleY, s[7], 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Def"), b -> {
            active().color()[selected()] = active().defaultColor(selected());
            if (hexBox != null) hexBox.syncFromValue();
        }).bounds(s[8], styleY, s[9], 20).build());
        hexBox = new HexColorBox(this.font, s[10], styleY, s[11],
                () -> active().color()[selected()],
                v -> active().color()[selected()] = v);
        this.addRenderableWidget(hexBox);

        // Action row.
        int[] a = rowSlots(new int[]{60, 60, 76, 72, 60, 60});
        this.addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> cyclePart(-1))
                .bounds(a[0], by, a[1], 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Next >"), b -> cyclePart(1))
                .bounds(a[2], by, a[3], 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset part"), b -> {
            active().resetPart(selected());
            if (hexBox != null) hexBox.syncFromValue();
        }).bounds(a[4], by, a[5], 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset tab"), b -> {
            active().resetAll();
            active().setCustomLayout(true);
            if (hexBox != null) hexBox.syncFromValue();
        }).bounds(a[6], by, a[7], 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            // Every surface, not just the visible tab: tabs can be switched freely before saving.
            for (PartLayout s2 : surfaces) s2.save();
            this.minecraft.setScreen(parent);
        }).bounds(a[8], by, a[9], 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            restore();
            this.minecraft.setScreen(parent);
        }).bounds(a[10], by, a[11], 20).build());
    }

    private void selectSurface(int idx) {
        surface = idx;
        active().setCustomLayout(true);
        rebuildWidgets();
        if (hexBox != null) hexBox.syncFromValue();
    }

    private void cyclePart(int delta) {
        int n = active().partCount();
        selectedPerSurface[surface] = (selected() + delta + n) % n;
        if (hexBox != null) hexBox.syncFromValue();
    }

    private void nudgeScale(float delta) {
        float[] scale = active().scale();
        scale[selected()] = Math.max(0.25f, Math.min(3.0f, scale[selected()] + delta));
    }

    private void cycleFont(int delta) {
        String[] fonts = active().font();
        int i = 0;
        for (int f = 0; f < FONTS.length; f++) {
            if (FONTS[f].equals(fonts[selected()])) { i = f; break; }
        }
        fonts[selected()] = FONTS[(i + delta + FONTS.length) % FONTS.length];
    }

    /** Put every surface back exactly as it was on entry. */
    private void restore() {
        for (int i = 0; i < surfaces.length; i++) {
            PartLayout s = surfaces[i];
            int n = s.partCount();
            System.arraycopy(priorX[i], 0, s.x(), 0, n);
            System.arraycopy(priorY[i], 0, s.y(), 0, n);
            System.arraycopy(priorScale[i], 0, s.scale(), 0, n);
            System.arraycopy(priorColor[i], 0, s.color(), 0, n);
            System.arraycopy(priorBold[i], 0, s.bold(), 0, n);
            System.arraycopy(priorFont[i], 0, s.font(), 0, n);
            s.setCustomLayout(priorCustom[i]);
        }
    }

    /** Escape discards, exactly like Cancel. Only Save commits. */
    @Override
    public void onClose() {
        restore();
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xAA050510);
        active().drawPreview(graphics, this.width, this.height);

        PartLayout s = active();
        int part = selected();
        graphics.drawCenteredString(this.font,
                "Drag to move  |  arrows nudge 1px  |  Tab cycles parts  |  Escape discards",
                this.width / 2, 10, 0xFFAAAAAA);
        graphics.drawCenteredString(this.font,
                s.name() + " — " + s.partName(part).toUpperCase()
                        + "  x=" + s.x()[part] + "  y=" + s.y()[part]
                        + String.format("  %.2fx", s.scale()[part])
                        + (s.bold()[part] ? "  BOLD" : "")
                        + String.format("  #%08X", s.color()[part])
                        + "  " + s.font()[part],
                this.width / 2, 22, s.color()[part]);

        // Every part's numbers stay on screen, so a finished layout can be read off and reported
        // rather than described.
        // Read-off list of every part's numbers, so a finished layout can be reported verbatim.
        // Right-aligned and clipped to the clear band rather than parked at a fixed column, which
        // put it on top of the controls once the screen was narrow enough.
        int rows = Math.min(s.partCount(), Math.max(0, (bandBottom - bandTop) / 10));
        for (int i = 0; i < rows; i++) {
            String line = String.format("%-10s %+4d,%+4d %s", s.partName(i), s.x()[i], s.y()[i],
                    s.color()[i] == s.defaultColor(i)
                            ? "" : String.format("#%06X", s.color()[i] & 0xFFFFFF));
            graphics.drawString(this.font, line, this.width - 4 - this.font.width(line),
                    bandTop + i * 10, i == part ? 0xFFFFC107 : 0xFF6C7A89, false);
        }

        int[] b = s.bounds();
        if (b != null && b[2] > 0 && b[3] > 0) {
            graphics.renderOutline(b[0], b[1], b[2], b[3], 0xFF42A5F5);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Above the control rows only, so dragging cannot start under a button.
        if (button == 0 && mouseY > bandTop && mouseY < bandBottom) {
            dragging = true;
            dragOffX = (int) mouseX - active().x()[selected()];
            dragOffY = (int) mouseY - active().y()[selected()];
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            PartLayout s = active();
            s.x()[selected()] = s.clampOffset((int) mouseX - dragOffX);
            s.y()[selected()] = s.clampOffset((int) mouseY - dragOffY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            nudgeScale((float) scrollY * 0.02f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hexBox != null && hexBox.isFocused()) return super.keyPressed(keyCode, scanCode, modifiers);
        int dx = keyCode == GLFW.GLFW_KEY_LEFT ? -1 : keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
        int dy = keyCode == GLFW.GLFW_KEY_UP ? -1 : keyCode == GLFW.GLFW_KEY_DOWN ? 1 : 0;
        if (dx != 0 || dy != 0) {
            PartLayout s = active();
            s.x()[selected()] = s.clampOffset(s.x()[selected()] + dx);
            s.y()[selected()] = s.clampOffset(s.y()[selected()] + dy);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cyclePart(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
