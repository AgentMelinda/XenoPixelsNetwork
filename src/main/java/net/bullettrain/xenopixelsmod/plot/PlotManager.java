package net.bullettrain.xenopixelsmod.plot;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-authoritative store of claimed plots.
 *
 * <p>Persisted as {@link SavedData} on the overworld data storage, matching the existing
 * {@code PartyMetadataSavedData} pattern. Claims are held in memory as a list because overlap
 * checks are inherently linear over the claimed set, and the set is expected to stay small.</p>
 */
public final class PlotManager extends SavedData {

    private static final String FILE_NAME = "xenopixels_plots";
    private static final String KEY_PLOTS = "Plots";

    private final List<PlotArea> plots = new ArrayList<>();

    public static PlotManager get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new Factory<>(PlotManager::new, PlotManager::load), FILE_NAME);
    }

    public static PlotManager load(CompoundTag tag, HolderLookup.Provider registries) {
        PlotManager result = new PlotManager();
        ListTag list = tag.getList(KEY_PLOTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null || !entry.hasUUID("Owner")) {
                continue;
            }
            result.plots.add(new PlotArea(dimension,
                    entry.getInt("MinX"), entry.getInt("MinZ"),
                    entry.getInt("MaxX"), entry.getInt("MaxZ"),
                    entry.getUUID("Owner"), entry.getInt("Flags")));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PlotArea plot : plots) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", plot.dimension().toString());
            entry.putInt("MinX", plot.minX());
            entry.putInt("MinZ", plot.minZ());
            entry.putInt("MaxX", plot.maxX());
            entry.putInt("MaxZ", plot.maxZ());
            entry.putUUID("Owner", plot.owner());
            entry.putInt("Flags", plot.flags());
            list.add(entry);
        }
        tag.put(KEY_PLOTS, list);
        return tag;
    }

    /** All claimed plots, in claim order. */
    public List<PlotArea> all() {
        return List.copyOf(plots);
    }

    /** The plot containing {@code (x, z)} in {@code dimension}, or {@code null}. */
    @Nullable
    public PlotArea at(ResourceLocation dimension, int x, int z) {
        for (PlotArea plot : plots) {
            if (plot.dimension().equals(dimension) && plot.contains(x, z)) {
                return plot;
            }
        }
        return null;
    }

    /** The first claimed plot that intersects {@code candidate}, or {@code null} when free. */
    @Nullable
    public PlotArea findOverlap(PlotArea candidate) {
        for (PlotArea plot : plots) {
            if (plot.overlaps(candidate)) {
                return plot;
            }
        }
        return null;
    }

    /** True when {@code candidate} intersects no existing claim. */
    public boolean isFree(PlotArea candidate) {
        return candidate != null && findOverlap(candidate) == null;
    }

    /** Claims {@code plot}. Returns false when it overlaps an existing claim. */
    public boolean claim(PlotArea plot) {
        if (plot == null || plot.owner() == null || !isFree(plot)) {
            return false;
        }
        plots.add(plot);
        setDirty();
        return true;
    }

    /** Removes every plot owned by {@code owner}. Returns how many were removed. */
    public int release(UUID owner) {
        if (owner == null) {
            return 0;
        }
        int before = plots.size();
        plots.removeIf(plot -> plot.ownedBy(owner));
        int removed = before - plots.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    /**
     * Removes every plot owned by {@code owner}, removing each plot's YAWP region first.
     *
     * <p>Release is the only path that deletes a claim outright, so it is the one that must also
     * delete the YAWP region — otherwise the region outlives the plot and keeps protecting land
     * nobody owns. Best-effort: with YAWP absent the region removal is a no-op and the claim is
     * still released.</p>
     */
    public int release(MinecraftServer server, UUID owner) {
        if (owner == null) {
            return 0;
        }
        for (PlotArea plot : plots) {
            if (!plot.ownedBy(owner)) {
                continue;
            }
            ServerLevel level = server == null ? null : server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, plot.dimension()));
            if (level != null) {
                PlotYaWP.remove(level, plot);
            }
        }
        return release(owner);
    }

    /** Replaces {@code oldPlot} with its flag-updated form. Returns false when it is not claimed. */
    public boolean setFlags(PlotArea oldPlot, int flags) {
        int index = plots.indexOf(oldPlot);
        if (index < 0) {
            return false;
        }
        plots.set(index, new PlotArea(oldPlot.dimension(), oldPlot.minX(), oldPlot.minZ(),
                oldPlot.maxX(), oldPlot.maxZ(), oldPlot.owner(), flags));
        setDirty();
        return true;
    }

    /**
     * Reassigns {@code oldPlot} to {@code newOwner}, keeping its bounds and flags.
     *
     * <p>Used by a sale. The caller settles payment first and only then calls this, so a plot is
     * never handed over without the money having moved.</p>
     */
    public boolean setOwner(PlotArea oldPlot, UUID newOwner) {
        if (oldPlot == null || newOwner == null) {
            return false;
        }
        int index = plots.indexOf(oldPlot);
        if (index < 0) {
            return false;
        }
        plots.set(index, new PlotArea(oldPlot.dimension(), oldPlot.minX(), oldPlot.minZ(),
                oldPlot.maxX(), oldPlot.maxZ(), newOwner, oldPlot.flags()));
        setDirty();
        return true;
    }

    /** Removes one specific claim. Returns false when it was not claimed. */
    public boolean remove(PlotArea plot) {
        if (plot == null) {
            return false;
        }
        boolean removed = plots.remove(plot);
        if (removed) {
            setDirty();
        }
        return removed;
    }
}