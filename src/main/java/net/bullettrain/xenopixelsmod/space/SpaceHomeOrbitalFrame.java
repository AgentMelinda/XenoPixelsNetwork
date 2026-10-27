package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Stable home-system focus shared by charted planets, generated bodies, travel, and client snapshots. */
public final class SpaceHomeOrbitalFrame extends SavedData {
    private static final String FILE = "xenopixels_space_home_orbital_frame_v1";
    public static final String SOL_ID = "xenopixelsmod:sol";
    private static final String EARTH_ID = PhysicalEarth.BODY_ID;

    public record Focus(double x, double y, double z) {
        public Focus {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("home orbital focus must be finite");
        }
    }

    private boolean initialized;
    private double y;

    private SpaceHomeOrbitalFrame() {}

    /** Returns null only when the chart has no valid Sol row; the chosen Y is persisted once per world. */
    public static Focus resolve(ServerLevel host, ChartRegistry chart) {
        int sol = chart.slotOf(SOL_ID);
        if (sol < 0 || !Double.isFinite(chart.x(sol)) || !Double.isFinite(chart.z(sol))) return null;
        SpaceHomeOrbitalFrame data = host.getDataStorage().computeIfAbsent(
                new Factory<>(SpaceHomeOrbitalFrame::new, SpaceHomeOrbitalFrame::load), FILE);
        if (!data.initialized) {
            double initial = PhysicalEarth.persistedPlaneY(host, SOL_ID);
            if (!Double.isFinite(initial)) initial = PhysicalEarth.persistedPlaneY(host, EARTH_ID);
            if (!Double.isFinite(initial))
                initial = Math.max(128.0, host.getMinBuildHeight() + PhysicalEarth.RADIUS + 2.0);
            data.resolveOnce(initial);
        }
        return new Focus(chart.x(sol), data.y, chart.z(sol));
    }

    /**
     * Maps a chart-evaluated Y into the persisted home-system plane. Every descendant of Sol keeps its
     * authored/evaluated vertical offset from Sol; bodies outside that tree retain their chart Y.
     * Live Sable poses should take precedence at call sites that have one.
     */
    public static double chartY(ChartRegistry chart, int slot, double homeY) {
        if (slot < 0 || slot >= chart.size()) return Double.NaN;
        double authoredY = chart.y(slot);
        int sol = chart.slotOf(SOL_ID);
        if (sol < 0 || !Double.isFinite(homeY) || !Double.isFinite(authoredY)) return authoredY;

        int current = slot;
        for (int depth = 0; depth <= chart.size() && current >= 0; depth++) {
            var body = chart.body(current);
            if (SOL_ID.equals(body.id())) {
                double solY = chart.y(sol);
                return Double.isFinite(solY) ? homeY + authoredY - solY : authoredY;
            }
            String parentId = body.parentId();
            current = parentId == null ? -1 : chart.slotOf(parentId);
        }
        return authoredY;
    }

    double resolveOnce(double initialY) {
        if (!initialized) {
            if (!Double.isFinite(initialY)) throw new IllegalArgumentException("initial home plane must be finite");
            y = initialY;
            initialized = true;
            setDirty();
        }
        return y;
    }

    static SpaceHomeOrbitalFrame load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new SpaceHomeOrbitalFrame();
        double y = tag.getDouble("Y");
        if (tag.getBoolean("Initialized") && Double.isFinite(y)) {
            data.initialized = true;
            data.y = y;
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Initialized", initialized);
        if (initialized) tag.putDouble("Y", y);
        return tag;
    }
}
