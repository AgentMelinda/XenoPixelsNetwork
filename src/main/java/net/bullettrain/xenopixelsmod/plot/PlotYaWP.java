package net.bullettrain.xenopixelsmod.plot;

import net.bullettrain.xenopixelsmod.compat.yawp.YawpRegionBridge;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.UUID;

/**
 * Keeps a {@link PlotArea} and its YAWP local region in step.
 *
 * <p><b>One boundary, one owner.</b> A plot has exactly one YAWP region. {@link PlotFlags} stays
 * authoritative for the five behaviours it names and YAWP is the block-protection backstop, so
 * YAWP can only ever be <i>more</i> restrictive, never less. Neither side is derived from the
 * other at read time — they are reconciled only at the three mutation points: claim, release and
 * sale.</p>
 *
 * <p>The mirror is one-way and best-effort. With YAWP absent every call is a no-op and the plot
 * still claims and sells; it is simply not protected by YAWP.</p>
 */
public final class PlotYaWP {

    /** Region names YAWP reserves for its own hierarchy; never collide with them. */
    private static final String PREFIX = "xenoplot";

    private PlotYaWP() {
    }

    /**
     * Deterministic region name for a plot, derived from the tuple that already identifies it.
     *
     * <p>Deterministic rather than random so a restart re-matches the existing region instead of
     * creating a second one.</p>
     */
    public static String regionName(PlotArea plot) {
        ResourceLocation dimension = plot.dimension();
        return PREFIX
                + "_" + sanitize(dimension.getNamespace())
                + "_" + sanitize(dimension.getPath())
                + "_" + coordinate(plot.minX())
                + "_" + coordinate(plot.minZ());
    }

    /** True when the YAWP mod is present and its region API resolved. */
    public static boolean available() {
        return YawpRegionBridge.available();
    }

    /** Creates or refreshes the YAWP region backing {@code plot}. */
    public static boolean sync(ServerLevel level, PlotArea plot) {
        if (level == null || plot == null) {
            return false;
        }
        ResourceKey<Level> dimension = level.dimension();
        // A plot carries no Y, so the region spans the full column. Whether YAWP then treats it
        // identically to a hand-built region is unverified; see docs/shops-and-plots.md.
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        boolean written = YawpRegionBridge.ensureRegion(regionName(plot), dimension,
                plot.minX(), plot.minZ(), plot.maxX(), plot.maxZ(), minY, maxY);
        if (written) {
            record(level, plot);
        }
        return written;
    }

    /** Removes the YAWP region backing {@code plot}, and stops reporting it as drift. */
    public static boolean remove(ServerLevel level, PlotArea plot) {
        if (level == null || plot == null) {
            return false;
        }
        boolean removed = YawpRegionBridge.removeRegion(regionName(plot), level.dimension());
        MinecraftServer server = level.getServer();
        if (server != null) {
            PlotRegionIndex.get(server).forget(plot);
        }
        return removed;
    }

    /** Notes in the region index that {@code plot}'s region was just written. */
    private static void record(ServerLevel level, PlotArea plot) {
        MinecraftServer server = level.getServer();
        if (server != null) {
            PlotRegionIndex.get(server).record(plot, regionName(plot), server.getTickCount());
        }
    }

    /**
     * Points the plot's YAWP region at a new owner, so the buyer is recognised by YAWP as well as
     * by {@link PlotManager}.
     */
    public static boolean reassign(ServerLevel level, PlotArea plot, ServerPlayer newOwner) {
        if (level == null || plot == null || newOwner == null) {
            return false;
        }
        // Rebuild first: the region may have been removed by hand, in which case adding the player
        // to it would silently do nothing.
        sync(level, plot);
        return YawpRegionBridge.addOwner(regionName(plot), level.dimension(), newOwner);
    }

    /** True when this plot's YAWP region currently exists. */
    public static boolean present(ServerLevel level, PlotArea plot) {
        if (level == null || plot == null) {
            return false;
        }
        return YawpRegionBridge.hasRegion(regionName(plot), level.dimension());
    }

    /**
     * Renders one plot coordinate as an alphanumeric token.
     *
     * <p>Negative coordinates are common — plots are placed relative to spawn — and a bare
     * {@code -} would put a non-alphanumeric character into a name that is also typed into
     * commands. The sign is spelled out as a leading {@code m} instead, which keeps the token
     * unambiguous: {@code -5} becomes {@code m5} and never collides with {@code 5}.</p>
     */
    private static String coordinate(int value) {
        return value < 0 ? "m" + (-value) : Integer.toString(value);
    }

    /** YAWP region names are used in commands and on disk; keep to a conservative character set. */
    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            out.append(Character.isLetterOrDigit(c) ? Character.toLowerCase(c) : '_');
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    /** Owner group name YAWP uses for the region's owning players. */
    static UUID ownerOf(PlotArea plot) {
        return plot.owner();
    }
}