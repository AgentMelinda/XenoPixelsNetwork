package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells;
import net.minecraft.resources.ResourceLocation;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;

/** Resolves the current chart body for shared planet-surface cells without capturing SDB/small-planet trips. */
public final class ChartSurfaceBodyResolver {
    public static final String SHARED_SURFACE_DIMENSION = "xenopixelsmod:planet_surfaces";

    private ChartSurfaceBodyResolver() {}

    /**
     * For the shared cell dimension, cell identity is authoritative. Unassigned cells return -1 so the
     * dedicated SDB and generated-small-planet travel handlers retain ownership of their cells. For all
     * ordinary one-body dimensions, preserve ChartRegistry's existing first-binding behavior.
     */
    public static int slotAt(ChartRegistry chart, String dimensionId, int blockX, int blockZ) {
        if (SHARED_SURFACE_DIMENSION.equals(dimensionId)) {
            var address = PlanetSurfaceCells.addressAtBlock(blockX, blockZ).orElse(null);
            if (address == null || address.chartBodyId() == null) return -1;
            int slot = chart.slotOf(address.chartBodyId());
            if (slot < 0) return -1;
            var body = chart.body(slot);
            return body.enterable() && SHARED_SURFACE_DIMENSION.equals(body.dimensionId()) ? slot : -1;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(dimensionId);
        return dimension == null ? -1 : chart.slotFor(dimension);
    }

    /** Shared-surface bodies must arrive inside the cell whose identity drives their return handoff. */
    public static boolean anchorMatchesCell(CelestialBody body) {
        if (body == null || !SHARED_SURFACE_DIMENSION.equals(body.dimensionId())
                || !Double.isFinite(body.spatialAnchorX()) || !Double.isFinite(body.spatialAnchorZ())) return false;
        double x = Math.floor(body.spatialAnchorX());
        double z = Math.floor(body.spatialAnchorZ());
        if (x < Long.MIN_VALUE || x > Long.MAX_VALUE || z < Long.MIN_VALUE || z > Long.MAX_VALUE) return false;
        var address = PlanetSurfaceCells.addressAtBlock((long) x, (long) z).orElse(null);
        return address != null && body.id().equals(address.chartBodyId());
    }
}
