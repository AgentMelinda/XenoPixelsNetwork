package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.client.space.InfiniteUniverseClient;
import net.bullettrain.xenopixelsmod.client.space.UniverseClient;
import net.bullettrain.xenopixelsmod.space.universe.InfiniteUniverseField;
import net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageRequest;
import net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageSnapshot;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.bullettrain.xenopixelsmod.space.universe.UniverseWarpRequest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Design-doc §9 Galaxy Atlas, first slice: a 2D map of the known star systems on the Voidsea plane, centred on Sol.
 * Drag to pan, scroll to zoom, click a star to select it; the side panel shows its name, star class, planet slots and
 * coordinates, and the Warp button asks the server to warp there (the server re-checks that it is known, that warp is
 * enabled and the player's permission). One unit on the map is one universe cell (131,072 blocks).
 */
public class GalaxyAtlasScreen extends Screen {
    private static final int MAX_MARKERS_PER_FRAME = 8_192;
    private double centreX, centreZ;
    private double scale = 18;
    private UniverseClient.Known selected;
    private InfiniteUniverseClient.Known selectedLogical;
    private boolean logicalMode;
    private long logicalOriginX, logicalOriginZ;
    private double logicalCentreX = InfiniteUniversePageRequest.VIEWPORT_EDGE / 2.0;
    private double logicalCentreZ = InfiniteUniversePageRequest.VIEWPORT_EDGE / 2.0;
    private Button warp;
    private boolean dragging;
    private List<UniverseClient.Known> visibleSystems = List.of();
    private List<InfiniteUniverseClient.Known> visibleLogicalSystems = List.of();
    private long viewRevision = Long.MIN_VALUE;
    private double viewCentreX = Double.NaN, viewCentreZ = Double.NaN, viewScale = Double.NaN;
    private int viewWidth = -1, viewHeight = -1;
    private InfiniteUniversePageSnapshot logicalViewPage;
    private double logicalViewCentreX = Double.NaN, logicalViewCentreZ = Double.NaN;
    private double logicalViewScale = Double.NaN;
    private long logicalViewRevision = Long.MIN_VALUE;

    public GalaxyAtlasScreen() { super(Component.translatable("screen.xenopixelsmod.galaxy_atlas")); }

    @Override protected void init() {
        warp = addRenderableWidget(Button.builder(Component.translatable("screen.xenopixelsmod.galaxy_atlas.warp"), b -> {
            if (selected != null) {
                AddonNetwork.sendToServer(new UniverseWarpRequest(selected.cell().x(), selected.cell().z()));
                onClose();
            }
        }).bounds(width - 110, height - 30, 100, 20).build());
        refreshWarp();
    }

    private void refreshWarp() {
        warp.active = !logicalMode && selected != null && UniverseClient.hasSnapshot() && UniverseClient.warpAllowed();
    }

    private int sx(double cellX) { return (int) Math.round(width / 2.0 + (cellX - centreX) * scale); }
    private int sz(double cellZ) { return (int) Math.round(height / 2.0 + (cellZ - centreZ) * scale); }

    private static double cx(UniverseField.StarSystem s) { return s.x() / UniverseField.CELL_SIZE; }
    private static double cz(UniverseField.StarSystem s) { return s.z() / UniverseField.CELL_SIZE; }

    private void refreshVisibleSystems() {
        if (logicalMode) {
            long cellX = safeCell(logicalOriginX, logicalCentreX);
            long cellZ = safeCell(logicalOriginZ, logicalCentreZ);
            var page = InfiniteUniverseClient.page();
            if (logicalViewPage == page && logicalViewRevision == InfiniteUniverseClient.revision()
                    && logicalViewCentreX == logicalCentreX && logicalViewCentreZ == logicalCentreZ
                    && logicalViewScale == scale && viewWidth == width && viewHeight == height) return;
            logicalViewPage = page;
            logicalViewRevision = InfiniteUniverseClient.revision();
            logicalViewCentreX = logicalCentreX;
            logicalViewCentreZ = logicalCentreZ;
            logicalViewScale = scale;
            viewWidth = width;
            viewHeight = height;
            visibleSystems = List.of();
            visibleLogicalSystems = page == null || !page.available()
                    || page.originX() != logicalOriginX || page.originZ() != logicalOriginZ
                    ? List.of() : InfiniteUniverseClient.systems();
            if (selectedLogical != null) {
                var selectedCell = selectedLogical.cell();
                selectedLogical = visibleLogicalSystems.stream()
                        .filter(known -> known.cell().equals(selectedCell)).findFirst().orElse(selectedLogical);
            }
            selected = null;
            if (warp != null) refreshWarp();
            return;
        }
        long revision = UniverseClient.revision();
        if (viewRevision == revision && viewCentreX == centreX && viewCentreZ == centreZ && viewScale == scale
                && viewWidth == width && viewHeight == height) return;
        viewRevision = revision;
        viewCentreX = centreX; viewCentreZ = centreZ; viewScale = scale; viewWidth = width; viewHeight = height;
        visibleSystems = !UniverseClient.hasSnapshot() ? List.of() : UniverseClient.atlasSystems(
                centreX, centreZ, width / (2.0 * scale), height / (2.0 * scale), MAX_MARKERS_PER_FRAME);
        visibleLogicalSystems = List.of();
        logicalViewPage = null;
        if (warp != null) refreshWarp();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refreshVisibleSystems();
        g.fill(0, 0, width, height, 0xF0060912);
        if (scale >= 6) {
            int step = (int) Math.round(scale);
            int gridX = logicalMode ? sxLogical(0.5, logicalCentreX) : sx(0.5);
            int gridZ = logicalMode ? szLogical(0.5, logicalCentreZ) : sz(0.5);
            for (int x = Math.floorMod(gridX, step); x < width; x += step) g.fill(x, 0, x + 1, height, 0x1A6FA8FF);
            for (int y = Math.floorMod(gridZ, step); y < height; y += step) g.fill(0, y, width, y + 1, 0x1A6FA8FF);
        }
        if (logicalMode) {
            for (var known : visibleLogicalSystems) {
                var system = known.system();
                double px = known.cell().x() - logicalOriginX + system.localX() / UniverseField.CELL_SIZE;
                double pz = known.cell().z() - logicalOriginZ + system.localZ() / UniverseField.CELL_SIZE;
                int x = sxLogical(px, logicalCentreX), y = szLogical(pz, logicalCentreZ);
                if (x < -4 || y < -4 || x > width + 4 || y > height + 4) continue;
                int r = known.cell().home() ? 3 : 2;
                int color = known.revealed() ? system.starClass().colour : 0xFF657488;
                g.fill(x - r, y - r, x + r + 1, y + r + 1, color);
                if (known.revealed() && (scale >= 24 || known.cell().home()))
                    g.drawString(font, system.name(), x + 5, y - 4, 0xFFCFE3FF, false);
                if (Math.abs(mouseX - x) <= 4 && Math.abs(mouseY - y) <= 4)
                    g.renderTooltip(font, Component.literal(known.revealed() ? system.name() : "Uncharted system"), mouseX, mouseY);
                if (known == selectedLogical) {
                    g.fill(x - r - 2, y - r - 2, x + r + 3, y - r - 1, 0xFFFFFFFF);
                    g.fill(x - r - 2, y + r + 2, x + r + 3, y + r + 3, 0xFFFFFFFF);
                }
            }
        }
        UniverseClient.Known hovered = null;
        for (var known : visibleSystems) {
            var system = known.system();
            int x = sx(cx(system)), y = sz(cz(system));
            if (x < -4 || y < -4 || x > width + 4 || y > height + 4) continue;
            int r = known.cell().home() ? 3 : 2;
            g.fill(x - r, y - r, x + r + 1, y + r + 1, system.starClass().colour);
            if (Math.abs(mouseX - x) <= 4 && Math.abs(mouseY - y) <= 4) hovered = known;
            if (known == selected) {
                g.fill(x - r - 2, y - r - 2, x + r + 3, y - r - 1, 0xFFFFFFFF);
                g.fill(x - r - 2, y + r + 2, x + r + 3, y + r + 3, 0xFFFFFFFF);
            }
            if (scale >= 24 || known.cell().home()) g.drawString(font, system.name(), x + 5, y - 4, 0xFFCFE3FF, false);
        }
        g.drawString(font, title, 10, 10, 0xFFFFFFFF, false);
        g.drawString(font, logicalMode
                ? Component.literal("Logical 64x64 page: " + logicalOriginX + ", " + logicalOriginZ + "  ·  press L for finite atlas")
                : Component.translatable("screen.xenopixelsmod.galaxy_atlas.known", UniverseClient.knownSystemCount()),
                10, 22, 0xFF9FB6D8, false);
        g.drawString(font, logicalMode
                ? Component.literal("Logical preview only — physical travel is not connected")
                : Component.translatable("screen.xenopixelsmod.galaxy_atlas.viewport", visibleSystems.size()),
                10, 34, 0xFF8299B8, false);
        if (logicalMode && (logicalViewPage == null || logicalViewPage.originX() != logicalOriginX
                || logicalViewPage.originZ() != logicalOriginZ || InfiniteUniverseClient.isGenerating()))
            g.drawCenteredString(font, Component.literal("Requesting bounded logical page…"), width / 2, height / 2, 0xFFFFC46B);
        else if (logicalMode && !logicalViewPage.available())
            g.drawCenteredString(font, Component.literal("Logical page unavailable; server setting may be off or busy"), width / 2, height / 2, 0xFFFFC46B);
        else if (!logicalMode && !UniverseClient.hasSnapshot())
            g.drawCenteredString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.empty"), width / 2, height / 2, 0xFFFFC46B);
        if (selectedLogical != null && logicalMode) {
            var s = selectedLogical.system();
            int px = width - 220, py = 40;
            g.fill(px - 6, py - 6, width - 6, py + 62, 0xC0101826);
            g.drawString(font, selectedLogical.revealed() ? s.name() : "Uncharted system", px, py, 0xFFFFFFFF, false);
            if (selectedLogical.revealed())
                g.drawString(font, s.starClass().name().toLowerCase(Locale.ROOT).replace('_', ' '), px, py + 12,
                        s.starClass().colour, false);
            g.drawString(font, "Cell " + selectedLogical.cell().id(), px, py + 24, 0xFFB9C7DD, false);
            g.drawString(font, selectedLogical.revealed() ? "Known · logical preview only" : "Not yet charted",
                    px, py + 36, 0xFFFFC46B, false);
        } else if (selected != null && !logicalMode) {
            var s = selected.system();
            int px = width - 200, py = 40;
            g.fill(px - 6, py - 6, width - 6, py + 50, 0xC0101826);
            g.drawString(font, s.name(), px, py, 0xFFFFFFFF, false);
            g.drawString(font, s.starClass().name().toLowerCase(Locale.ROOT).replace('_', ' '), px, py + 12, s.starClass().colour, false);
            g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.slots", s.planetSlots()), px, py + 24, 0xFFB9C7DD, false);
            g.drawString(font, Math.round(s.x()) + ", " + Math.round(s.z()), px, py + 36, 0xFFB9C7DD, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null) g.renderTooltip(font, Component.literal(hovered.system().name()), mouseX, mouseY);
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            if (logicalMode) {
                InfiniteUniverseClient.Known bestLogical = null;
                double bestDistance = 36;
                for (var known : visibleLogicalSystems) {
                    double px = known.cell().x() - logicalOriginX + known.system().localX() / UniverseField.CELL_SIZE;
                    double pz = known.cell().z() - logicalOriginZ + known.system().localZ() / UniverseField.CELL_SIZE;
                    double dx = mouseX - sxLogical(px, logicalCentreX), dy = mouseY - szLogical(pz, logicalCentreZ);
                    double d = dx * dx + dy * dy;
                    if (d < bestDistance) { bestLogical = known; bestDistance = d; }
                }
                if (bestLogical != null) { selectedLogical = bestLogical; refreshWarp(); return true; }
                dragging = true;
                return true;
            }
            UniverseClient.Known best = null;
            double bestDistance = 36;
            for (var known : visibleSystems) {
                double dx = mouseX - sx(cx(known.system())), dy = mouseY - sz(cz(known.system()));
                double d = dx * dx + dy * dy;
                if (d < bestDistance) { best = known; bestDistance = d; }
            }
            if (best != null) { selected = best; refreshWarp(); return true; }
            dragging = true;
            return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            if (logicalMode) {
                logicalCentreX -= dragX / scale;
                logicalCentreZ -= dragY / scale;
                shiftLogicalPageIfNeeded();
            } else { centreX -= dragX / scale; centreZ -= dragY / scale; }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_L) {
            logicalMode = !logicalMode;
            selected = null;
            selectedLogical = null;
            if (logicalMode) {
                logicalOriginX = 0;
                logicalOriginZ = 0;
                logicalCentreX = InfiniteUniversePageRequest.VIEWPORT_EDGE / 2.0;
                logicalCentreZ = InfiniteUniversePageRequest.VIEWPORT_EDGE / 2.0;
                scale = Math.max(scale, Math.max(width, height) / (double) InfiniteUniversePageRequest.VIEWPORT_EDGE);
                InfiniteUniverseClient.requestAt(0, 0, true);
            }
            logicalViewPage = null;
            logicalViewRevision = Long.MIN_VALUE;
            viewRevision = Long.MIN_VALUE;
            refreshWarp();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int sxLogical(double x, double centre) { return (int) Math.round(width / 2.0 + (x - centre) * scale); }
    private int szLogical(double z, double centre) { return (int) Math.round(height / 2.0 + (z - centre) * scale); }

    private static long safeCell(long origin, double offset) {
        double local = Math.floor(offset);
        if (local <= Long.MIN_VALUE) return Long.MIN_VALUE;
        if (local >= Long.MAX_VALUE) return Long.MAX_VALUE;
        try { return Math.addExact(origin, (long) local); }
        catch (ArithmeticException overflow) { return local < 0 ? Long.MIN_VALUE : Long.MAX_VALUE; }
    }

    private void shiftLogicalPageIfNeeded() {
        shiftAxis(true);
        shiftAxis(false);
    }

    private void shiftAxis(boolean xAxis) {
        double centre = xAxis ? logicalCentreX : logicalCentreZ;
        double pages = Math.floor(centre / InfiniteUniversePageRequest.VIEWPORT_EDGE);
        if (pages == 0) return;
        long pageShift = pages <= Long.MIN_VALUE ? Long.MIN_VALUE : pages >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) pages;
        try {
            long cellShift = Math.multiplyExact(pageShift, (long) InfiniteUniversePageRequest.VIEWPORT_EDGE);
            long origin = Math.addExact(xAxis ? logicalOriginX : logicalOriginZ, cellShift);
            centre -= cellShift;
            if (xAxis) { logicalOriginX = origin; logicalCentreX = centre; }
            else { logicalOriginZ = origin; logicalCentreZ = centre; }
        } catch (ArithmeticException overflow) {
            long origin = pageShift < 0 ? Long.MIN_VALUE : Long.MAX_VALUE - (InfiniteUniversePageRequest.VIEWPORT_EDGE - 1L);
            if (xAxis) { logicalOriginX = origin; logicalCentreX = InfiniteUniversePageRequest.VIEWPORT_EDGE / 2.0; }
            else { logicalOriginZ = origin; logicalCentreZ = InfiniteUniversePageRequest.VIEWPORT_EDGE / 2.0; }
        }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double minimum = logicalMode ? Math.max(2.0, Math.max(width, height) / (double) InfiniteUniversePageRequest.VIEWPORT_EDGE) : 2.0;
        scale = Math.max(minimum, Math.min(96, scale * (scrollY > 0 ? 1.25 : 0.8)));
        return true;
    }

    @Override public void tick() {
        super.tick();
        if (logicalMode) InfiniteUniverseClient.requestAt(safeCell(logicalOriginX, logicalCentreX),
                safeCell(logicalOriginZ, logicalCentreZ), true);
    }

    @Override public boolean isPauseScreen() { return false; }
}
