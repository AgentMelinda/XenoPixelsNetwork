package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.client.space.InfiniteUniverseClient;
import net.bullettrain.xenopixelsmod.client.space.UniverseClient;
import net.bullettrain.xenopixelsmod.space.universe.InfiniteUniverseField;
import net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageRequest;
import net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageSnapshot;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.bullettrain.xenopixelsmod.space.universe.UniverseWarpRequest;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetField;
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
    private long integrityViewRevision = Long.MIN_VALUE;
    private java.util.Map<String, Double> integrityViewSource;
    private net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasRows.View integrityView =
            new net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasRows.View(List.of(), 0);
    private final net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasMarkerCache integrityMarkerCache =
            new net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasMarkerCache();
    private List<net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasMarkerLayout.Cluster>
            integrityMarkerClusters = List.of();
    private List<String> selectedIntegrityClusterIds = List.of();
    private int selectedIntegrityClusterScroll;

    public GalaxyAtlasScreen() { super(Component.translatable("screen.xenopixelsmod.galaxy_atlas")); }

    @Override protected void init() {
        warp = addRenderableWidget(Button.builder(Component.translatable("screen.xenopixelsmod.galaxy_atlas.warp"), b -> {
            if (selected != null) {
                AddonNetwork.sendToServer(new UniverseWarpRequest(selected.cell().x(), selected.cell().z()));
                onClose();
            }
        }).bounds(width - 110, height - 30, 100, 20).build());
        refreshWarp();
        if (!logicalMode) net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasClient.request();
    }

    private int integrityRefreshTicks;

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
        List<Component> integrityMarkerTooltip = drawPlanetIntegrityAtlasMarkers(g, mouseX, mouseY);
        g.drawString(font, title, 10, 10, 0xFFFFFFFF, false);
        g.drawString(font, logicalMode
                ? Component.literal("Logical 64x64 page: " + logicalOriginX + ", " + logicalOriginZ + "  ·  press L for finite atlas")
                : Component.translatable("screen.xenopixelsmod.galaxy_atlas.known", UniverseClient.knownSystemCount()),
                10, 22, 0xFF9FB6D8, false);
        g.drawString(font, logicalMode
                ? Component.literal("Logical preview only — physical travel is not connected")
                : Component.translatable("screen.xenopixelsmod.galaxy_atlas.viewport", visibleSystems.size()),
                10, 34, 0xFF8299B8, false);
        // P4 eclipse event markers: an announced tournament-zone list only; no PvP rule is applied.
        var eclipses = net.bullettrain.xenopixelsmod.client.space.EclipseEventsClient.active();
        if (!eclipses.isEmpty()) {
            g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.eclipses", eclipses.size()),
                    10, 46, 0xFFFFC46B, false);
            int shown = Math.min(4, eclipses.size());
            for (int i = 0; i < shown; i++) {
                var eclipse = eclipses.get(i);
                g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.eclipse_entry",
                        eclipse.planetId(), eclipse.moonId(), Math.round(eclipse.coverage() * 100f), eclipse.starId()),
                        14, 58 + i * 12, 0xFFFFE0A8, false);
            }
        }
        // Section 23.1 telegraph: chart stars at an announced lifecycle stage, from the stage snapshot.
        var level = net.minecraft.client.Minecraft.getInstance().level;
        var stellar = level == null ? java.util.List.<net.bullettrain.xenopixelsmod.client.space.StellarStageClient.ClientWarning>of()
                : net.bullettrain.xenopixelsmod.client.space.StellarStageClient.warnings(level.getGameTime());
        if (!stellar.isEmpty()) {
            int top = eclipses.isEmpty() ? 46 : 62 + Math.min(4, eclipses.size()) * 12;
            g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.stellar_warnings", stellar.size()),
                    10, top, 0xFFFF7A4D, false);
            int shown = Math.min(4, stellar.size());
            for (int i = 0; i < shown; i++) {
                var flagged = stellar.get(i);
                String name = flagged.name();
                Component line = switch (flagged.warning().level()) {
                    case CRITICAL -> Component.translatable("screen.xenopixelsmod.galaxy_atlas.stellar_critical", name,
                            net.bullettrain.xenopixelsmod.space.event.StellarWarning.countdown(flagged.warning().ticksRemaining()));
                    case SUPERNOVA -> Component.translatable("screen.xenopixelsmod.galaxy_atlas.stellar_supernova", name);
                    default -> Component.translatable("screen.xenopixelsmod.galaxy_atlas.stellar_unstable", name);
                };
                g.drawString(font, line, 14, top + 12 + i * 12, 0xFFFFB08A, false);
            }
        }
        drawPlanetIntegrityRecords(g);
        Component integrityClusterRowTooltip = drawIntegrityClusterPanel(g, mouseX, mouseY);
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
            g.fill(px - 6, py - 6, width - 6, py + 74, 0xC0101826);
            g.drawString(font, selectedLogical.revealed() ? s.name() : "Uncharted system", px, py, 0xFFFFFFFF, false);
            if (selectedLogical.revealed())
                g.drawString(font, s.starClass().name().toLowerCase(Locale.ROOT).replace('_', ' '), px, py + 12,
                        s.starClass().colour, false);
            g.drawString(font, "Cell " + selectedLogical.cell().id(), px, py + 24, 0xFFB9C7DD, false);
            g.drawString(font, selectedLogical.revealed() ? "Known · logical preview only" : "Not yet charted",
                    px, py + 36, 0xFFFFC46B, false);
            g.drawString(font, "System planet slots: " + (selectedLogical.revealed() ? s.planetSlots() : "?"),
                    px, py + 48, 0xFFB9C7DD, false);
            g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.no_physical_bodies"),
                    px, py + 60, 0xFFFFC46B, false);
        } else if (selected != null && !logicalMode) {
            var s = selected.system();
            int px = width - 220, py = 40;
            var slots = SmallPlanetField.summarizeSlots(s);
            g.fill(px - 6, py - 6, width - 6, py + 74, 0xC0101826);
            g.drawString(font, s.name(), px, py, 0xFFFFFFFF, false);
            g.drawString(font, s.starClass().name().toLowerCase(Locale.ROOT).replace('_', ' '), px, py + 12, s.starClass().colour, false);
            g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.slots", slots.systemPlanetSlots()), px, py + 24, 0xFFB9C7DD, false);
            g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.small_worlds", slots.smallWorldSlots()),
                    px, py + 36, 0xFFB9C7DD, false);
            if (slots.unmappedSystemSlots() > 0)
                g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.unmapped_slots", slots.unmappedSystemSlots()),
                        px, py + 48, 0xFFFFC46B, false);
            g.drawString(font, Math.round(s.x()) + ", " + Math.round(s.z()), px, py + 60, 0xFFB9C7DD, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (integrityClusterRowTooltip != null) g.renderTooltip(font, integrityClusterRowTooltip, mouseX, mouseY);
        else if (integrityMarkerTooltip != null)
            g.renderTooltip(font, integrityMarkerTooltip, java.util.Optional.empty(), mouseX, mouseY);
        else if (hovered != null) g.renderTooltip(font, Component.literal(hovered.system().name()), mouseX, mouseY);
    }

    private void drawPlanetIntegrityRecords(GuiGraphics g) {
        if (logicalMode) return; // The logical preview explicitly has no physical planet mapping.
        long revision = net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityClient.revision();
        var values = net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityClient.values();
        if (revision != integrityViewRevision || values != integrityViewSource) {
            integrityViewRevision = revision;
            integrityViewSource = values;
            integrityView = net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasRows.lowest(
                    values, 5);
        }
        var markerSnapshot = net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasClient.snapshot();
        int markerSummaryLines = markerSnapshot != null && markerSnapshot.totalEntries() > 0 ? 1 : 0;
        int unmappedLines = markerSnapshot != null && markerSnapshot.unmappedErased() > 0 ? 1 : 0;
        if (integrityView.rows().isEmpty() && markerSummaryLines == 0 && unmappedLines == 0) return;
        int x = 10;
        int y = Math.max(96, height - 42 - integrityView.rows().size() * 13
                - (integrityView.omitted() > 0 ? 13 : 0) - markerSummaryLines * 13 - unmappedLines * 13);
        int bottom = y + 22 + integrityView.rows().size() * 13
                + (integrityView.omitted() > 0 ? 13 : 0) + markerSummaryLines * 13 + unmappedLines * 13;
        g.fill(x - 6, y - 6, x + 230, bottom, 0xC0101826);
        Component heading = integrityView.rows().isEmpty()
                ? Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_records_empty")
                : Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_records", integrityView.total());
        g.drawString(font, heading, x, y, 0xFFFFC46B, false);
        for (int i = 0; i < integrityView.rows().size(); i++) {
            var row = integrityView.rows().get(i);
            var stage = net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityClient.stage(row.bodyId());
            String body = row.bodyId().length() > 27 ? row.bodyId().substring(0, 26) + "…" : row.bodyId();
            Component line;
            int color;
            if (stage == net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityRules.Stage.DESTROYED) {
                line = Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_cracked", body);
                color = 0xFFFF786D;
            } else if (stage == net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityRules.Stage.CRITICAL) {
                line = Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_critical", body,
                        Math.round(row.integrity()));
                color = 0xFFFFA45C;
            } else {
                line = Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_scarred", body,
                        Math.round(row.integrity()));
                color = 0xFFFFD06A;
            }
            g.drawString(font, line, x + 3, y + 13 + i * 13, color, false);
        }
        if (integrityView.omitted() > 0)
            g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_omitted",
                    integrityView.omitted()), x + 3, y + 13 + integrityView.rows().size() * 13, 0xFFB9C7DD, false);
        int noteY = y + 13 + integrityView.rows().size() * 13 + (integrityView.omitted() > 0 ? 13 : 0);
        if (markerSummaryLines > 0) {
            g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_map_marker_count",
                    markerSnapshot.entries().size(), markerSnapshot.totalEntries()), x + 3, noteY, 0xFFB9C7DD, false);
            noteY += 13;
        }
        if (unmappedLines > 0)
            g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_unmapped_erased",
                    markerSnapshot.unmappedErased()), x + 3, noteY, 0xFFFFA45C, false);
    }

    private List<Component> drawPlanetIntegrityAtlasMarkers(GuiGraphics g, int mouseX, int mouseY) {
        if (logicalMode) { integrityMarkerClusters = List.of(); integrityMarkerCache.clear(); return null; }
        var snapshot = net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasClient.snapshot();
        if (snapshot == null) { integrityMarkerClusters = List.of(); integrityMarkerCache.clear(); return null; }
        integrityMarkerClusters = integrityMarkerCache.layout(snapshot, centreX, centreZ, scale, width, height,
                marker -> new net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasMarkerLayout.Point(
                        sx(marker.x() / UniverseField.CELL_SIZE), sz(marker.z() / UniverseField.CELL_SIZE)));
        List<Component> hoveredMarker = null;
        int hoveredCluster = net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasMarkerLayout.pick(
                integrityMarkerClusters, mouseX, mouseY, 6);
        for (int clusterIndex = 0; clusterIndex < integrityMarkerClusters.size(); clusterIndex++) {
            var cluster = integrityMarkerClusters.get(clusterIndex);
            int x = cluster.point().x();
            int y = cluster.point().y();
            if (x < -12 || y < -12 || x > width + 12 || y > height + 12) continue;
            if (cluster.markers().size() == 1) {
                drawIntegrityAtlasMarker(g, x, y, cluster.markers().getFirst());
            } else {
                // The cluster badge denotes several real positions that collide at this zoom; no body is offset.
                g.fill(x - 6, y - 6, x + 7, y + 7, 0xFFB92E25);
                g.fill(x - 4, y - 4, x + 5, y + 5, 0xFF28121B);
                g.drawCenteredString(font, Integer.toString(cluster.markers().size()), x, y - 4, 0xFFFFD8A8);
            }
            if (clusterIndex == hoveredCluster) {
                int shown = Math.min(8, cluster.markers().size());
                var lines = new java.util.ArrayList<Component>(shown + 2);
                if (cluster.markers().size() > 1)
                    lines.add(Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_overlap",
                            cluster.markers().size()));
                for (int i = 0; i < shown; i++) lines.add(integrityMarkerStatus(cluster.markers().get(i), true));
                if (cluster.markers().size() > shown)
                    lines.add(Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_more_click",
                            cluster.markers().size() - shown));
                hoveredMarker = List.copyOf(lines);
            }
        }
        return hoveredMarker;
    }

    private void drawIntegrityAtlasMarker(GuiGraphics g, int x, int y,
            net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasSnapshot.Entry marker) {
        if (marker.erased() || marker.integrity() == 0.0) {
            g.drawString(font, "☠", x - 3, y - 5, 0xFFFF655E, false);
        } else {
            int color = net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasMarkerStyle.color(
                    net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityClient.stage(marker.bodyId()));
            g.fill(x - 4, y - 4, x + 4, y - 3, color);
            g.fill(x - 4, y + 3, x + 4, y + 4, color);
            g.fill(x - 4, y - 3, x - 3, y + 3, color);
            g.fill(x + 3, y - 3, x + 4, y + 3, color);
            g.fill(x - 1, y - 2, x, y, 0xFFFFF2D4);
            g.fill(x, y, x + 1, y + 2, 0xFFFFF2D4);
        }
    }

    private static Component integrityMarkerStatus(
            net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasSnapshot.Entry marker, boolean shortened) {
        String bodyId = marker.bodyId();
        if (shortened && bodyId.length() > 32) bodyId = bodyId.substring(0, 31) + "…";
        return marker.erased() ? Component.translatable(
                "screen.xenopixelsmod.galaxy_atlas.integrity_erased", bodyId)
                : marker.integrity() == 0.0 ? Component.translatable(
                        "screen.xenopixelsmod.galaxy_atlas.integrity_destroyed", bodyId)
                : Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_marker",
                        bodyId, Math.round(marker.integrity()));
    }

    private Component drawIntegrityClusterPanel(GuiGraphics g, int mouseX, int mouseY) {
        if (logicalMode || selectedIntegrityClusterIds.isEmpty()) return null;
        var snapshot = net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasClient.snapshot();
        if (snapshot == null) { selectedIntegrityClusterIds = List.of(); return null; }
        var byId = new java.util.HashMap<String,
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasSnapshot.Entry>();
        for (var marker : snapshot.entries()) byId.put(marker.bodyId(), marker);
        var selectedMarkers = new java.util.ArrayList<
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasSnapshot.Entry>();
        var retainedIds = new java.util.ArrayList<String>();
        for (String id : selectedIntegrityClusterIds) {
            var marker = byId.get(id);
            if (marker != null) { retainedIds.add(id); selectedMarkers.add(marker); }
        }
        selectedIntegrityClusterIds = List.copyOf(retainedIds);
        if (selectedMarkers.isEmpty()) return null;

        int panelX = width - 230, panelY = 48, panelBottom = height - 38;
        int rowLimit = Math.max(1, (panelBottom - panelY - 28) / 12);
        selectedIntegrityClusterScroll = Math.max(0, Math.min(selectedIntegrityClusterScroll,
                selectedMarkers.size() - rowLimit));
        g.fill(panelX, panelY, width - 8, panelBottom, 0xE0101826);
        g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_cluster_title",
                selectedMarkers.size()), panelX + 6, panelY + 5, 0xFFFFC46B, false);
        g.drawString(font, "×", width - 22, panelY + 4, 0xFFFF786D, false);
        if (selectedMarkers.size() > rowLimit)
            g.drawString(font, Component.translatable("screen.xenopixelsmod.galaxy_atlas.integrity_cluster_scroll",
                    selectedIntegrityClusterScroll + 1, selectedMarkers.size()), panelX + 6, panelY + 17,
                    0xFFB9C7DD, false);
        int rowsY = panelY + 29;
        Component hovered = null;
        for (int row = 0; row < rowLimit && selectedIntegrityClusterScroll + row < selectedMarkers.size(); row++) {
            int index = selectedIntegrityClusterScroll + row;
            int rowY = rowsY + row * 12;
            var marker = selectedMarkers.get(index);
            g.drawString(font, integrityMarkerStatus(marker, true), panelX + 6, rowY, 0xFFFFFFFF, false);
            if (mouseX >= panelX && mouseX < width - 8 && mouseY >= rowY && mouseY < rowY + 12)
                hovered = integrityMarkerStatus(marker, false);
        }
        return hovered;
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
            int panelX = width - 230, panelY = 48, panelBottom = height - 38;
            if (!selectedIntegrityClusterIds.isEmpty() && mouseX >= panelX && mouseX < width - 8
                    && mouseY >= panelY && mouseY < panelBottom) {
                if (mouseX >= width - 26 && mouseY < panelY + 15) selectedIntegrityClusterIds = List.of();
                return true;
            }
            int markerIndex = net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasMarkerLayout.pick(
                    integrityMarkerClusters, mouseX, mouseY, 6);
            if (markerIndex >= 0) {
                selectedIntegrityClusterIds = integrityMarkerClusters.get(markerIndex).markers().stream()
                        .map(net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasSnapshot.Entry::bodyId).toList();
                selectedIntegrityClusterScroll = 0;
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
            selectedIntegrityClusterIds = List.of();
            selectedIntegrityClusterScroll = 0;
            if (logicalMode) {
                logicalOriginX = 0;
                logicalOriginZ = 0;
                logicalCentreX = InfiniteUniversePageRequest.VIEWPORT_EDGE / 2.0;
                logicalCentreZ = InfiniteUniversePageRequest.VIEWPORT_EDGE / 2.0;
                scale = Math.max(scale, Math.max(width, height) / (double) InfiniteUniversePageRequest.VIEWPORT_EDGE);
                InfiniteUniverseClient.requestAt(0, 0, true);
            } else {
                // The logical preview has no physical-body markers. Refresh immediately when
                // returning to the finite chart instead of waiting for the next periodic tick.
                integrityRefreshTicks = 0;
                net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasClient.request();
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
        int panelX = width - 230, panelY = 48, panelBottom = height - 38;
        if (!selectedIntegrityClusterIds.isEmpty() && mouseX >= panelX && mouseX < width - 8
                && mouseY >= panelY && mouseY < panelBottom && selectedIntegrityClusterIds.size() > 1) {
            int rowLimit = Math.max(1, (panelBottom - panelY - 28) / 12);
            int maximum = Math.max(0, selectedIntegrityClusterIds.size() - rowLimit);
            selectedIntegrityClusterScroll = Math.max(0, Math.min(maximum,
                    selectedIntegrityClusterScroll + (scrollY > 0 ? -3 : 3)));
            return true;
        }
        double minimum = logicalMode ? Math.max(2.0, Math.max(width, height) / (double) InfiniteUniversePageRequest.VIEWPORT_EDGE) : 2.0;
        scale = Math.max(minimum, Math.min(96, scale * (scrollY > 0 ? 1.25 : 0.8)));
        return true;
    }

    @Override public void tick() {
        super.tick();
        if (!logicalMode && ++integrityRefreshTicks >= 40) {
            integrityRefreshTicks = 0;
            net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasClient.request();
        }
        if (logicalMode) InfiniteUniverseClient.requestAt(safeCell(logicalOriginX, logicalCentreX),
                safeCell(logicalOriginZ, logicalCentreZ), true);
    }

    @Override public boolean isPauseScreen() { return false; }
}
