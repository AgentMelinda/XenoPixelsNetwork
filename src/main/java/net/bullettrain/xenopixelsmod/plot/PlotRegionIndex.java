package net.bullettrain.xenopixelsmod.plot;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Records which YAWP region was last written for a plot, so drift can be told from absence.
 *
 * <p>{@link PlotYaWP#present} answers one question — does the region exist right now. It cannot
 * distinguish a plot that has never been mirrored from one that was mirrored and then deleted or
 * renamed by hand. Those need different answers: the first is normal, the second is a
 * desynchronisation someone should be told about.</p>
 *
 * <p>The index does <b>not</b> resolve region names. {@link PlotYaWP#regionName} is already
 * deterministic from the plot tuple, so an index cannot improve on it. Its only job is to remember
 * that a write happened, and when.</p>
 *
 * <p>Persisted as {@link SavedData} on the overworld data storage, matching {@link PlotManager}.</p>
 */
public final class PlotRegionIndex extends SavedData {

    private static final String FILE_NAME = "xenopixels_plot_region_index";
    private static final String KEY_ENTRIES = "Entries";

    /** What the index knows about a plot's YAWP region. */
    public enum State {
        /** No region has ever been written for this plot. */
        NEVER_SYNCED,
        /** A region was written and still exists. */
        PRESENT,
        /** A region was written but is now gone — renamed or deleted outside XenoPixels. */
        ABSENT
    }

    /** The last region name written for a plot, and the tick it was written. */
    public record Entry(String regionName, long tick) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static PlotRegionIndex get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new Factory<>(PlotRegionIndex::new, PlotRegionIndex::load), FILE_NAME);
    }

    /**
     * The index key for a plot: dimension, then the two coordinates that identify it.
     *
     * <p>Coordinates are written with an explicit sign so {@code -5} and {@code 5} cannot collide,
     * and the dimension is separated from them by a character that cannot appear in a
     * {@link ResourceLocation} path.</p>
     */
    public static String key(PlotArea plot) {
        return plot.dimension() + "#" + plot.minX() + "," + plot.minZ();
    }

    public static PlotRegionIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        PlotRegionIndex result = new PlotRegionIndex();
        ListTag list = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null) {
                continue;
            }
            String name = entry.getString("Region");
            if (name.isBlank()) {
                continue;
            }
            String key = dimension + "#" + entry.getInt("MinX") + "," + entry.getInt("MinZ");
            result.entries.put(key.toLowerCase(Locale.ROOT), new Entry(name, entry.getLong("Tick")));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            CompoundTag tagEntry = new CompoundTag();
            String[] parts = split(entry.getKey());
            if (parts == null) {
                continue;
            }
            tagEntry.putString("Dimension", parts[0]);
            tagEntry.putInt("MinX", Integer.parseInt(parts[1]));
            tagEntry.putInt("MinZ", Integer.parseInt(parts[2]));
            tagEntry.putString("Region", entry.getValue().regionName());
            tagEntry.putLong("Tick", entry.getValue().tick());
            list.add(tagEntry);
        }
        tag.put(KEY_ENTRIES, list);
        return tag;
    }

    /** Records that {@code regionName} was written for {@code plot} at {@code tick}. */
    public void record(PlotArea plot, String regionName, long tick) {
        if (plot == null || regionName == null || regionName.isBlank()) {
            return;
        }
        entries.put(key(plot).toLowerCase(Locale.ROOT), new Entry(regionName, tick));
        setDirty();
    }

    /** Forgets {@code plot}, so a removed region stops being reported as drift. */
    public void forget(PlotArea plot) {
        if (plot == null) {
            return;
        }
        if (entries.remove(key(plot).toLowerCase(Locale.ROOT)) != null) {
            setDirty();
        }
    }

    /** The recorded entry for {@code plot}, or {@code null} when it was never synced. */
    @Nullable
    public Entry entry(PlotArea plot) {
        return plot == null ? null : entries.get(key(plot).toLowerCase(Locale.ROOT));
    }

    /** How {@code plot}'s YAWP region stands: never written, present, or drifted away. */
    public State state(ServerLevel level, PlotArea plot) {
        if (entry(plot) == null) {
            return State.NEVER_SYNCED;
        }
        return PlotYaWP.present(level, plot) ? State.PRESENT : State.ABSENT;
    }

    /** Splits an index key back into {@code [dimension, minX, minZ]}, or {@code null}. */
    @Nullable
    private static String[] split(String key) {
        int hash = key.lastIndexOf('#');
        int comma = key.indexOf(',', hash + 1);
        if (hash < 0 || comma < 0) {
            return null;
        }
        try {
            Integer.parseInt(key.substring(hash + 1, comma));
            Integer.parseInt(key.substring(comma + 1));
        } catch (NumberFormatException exception) {
            return null;
        }
        return new String[]{key.substring(0, hash), key.substring(hash + 1, comma),
                key.substring(comma + 1)};
    }
}