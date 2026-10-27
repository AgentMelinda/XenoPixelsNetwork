package net.bullettrain.xenopixelsmod.space.hakai;

import net.bullettrain.xenopixelsmod.space.chart.ChartDefinition;
import net.minecraft.network.FriendlyByteBuf;

import java.util.HashSet;
import java.util.List;

/** Bounded server-authoritative map positions for damaged and erased bodies. */
public record PlanetIntegrityAtlasSnapshot(long requestId, int totalEntries, int unmappedErased, List<Entry> entries) {
    public static final int MAX_ENTRIES = 128;
    /** Over-cap legacy SavedData rows are preserved, so the display total is an int bound, not the admission cap. */
    public static final int MAX_TOTAL_ENTRIES = Integer.MAX_VALUE;

    public record Entry(String bodyId, double x, double z, double integrity, boolean erased) {
        public Entry {
            ChartDefinition.id(bodyId);
            if (bodyId.length() > 256 || !coordinate(x) || !coordinate(z)
                    || !Double.isFinite(integrity) || integrity < 0 || integrity >= 100)
                throw new IllegalArgumentException("invalid planet-integrity Atlas marker");
        }
    }

    public PlanetIntegrityAtlasSnapshot {
        if (requestId <= 0 || totalEntries < 0 || totalEntries > MAX_TOTAL_ENTRIES
                || unmappedErased < 0 || unmappedErased > ErasedBodiesSavedData.MAX || entries == null
                || entries.size() > MAX_ENTRIES || entries.size() > totalEntries)
            throw new IllegalArgumentException("invalid planet-integrity Atlas snapshot bounds");
        entries = List.copyOf(entries);
        var ids = new HashSet<String>();
        for (Entry entry : entries) if (!ids.add(entry.bodyId()))
            throw new IllegalArgumentException("duplicate body in planet-integrity Atlas snapshot");
    }

    public int omitted() { return totalEntries - entries.size(); }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(requestId);
        buf.writeVarInt(totalEntries);
        buf.writeVarInt(unmappedErased);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUtf(entry.bodyId(), 256);
            buf.writeDouble(entry.x());
            buf.writeDouble(entry.z());
            buf.writeDouble(entry.integrity());
            buf.writeBoolean(entry.erased());
        }
    }

    public static PlanetIntegrityAtlasSnapshot decode(FriendlyByteBuf buf) {
        long requestId = buf.readVarLong();
        int total = buf.readVarInt();
        int unmappedErased = buf.readVarInt();
        int count = buf.readVarInt();
        if (total < 0 || total > MAX_TOTAL_ENTRIES || unmappedErased < 0
                || unmappedErased > ErasedBodiesSavedData.MAX || count < 0 || count > MAX_ENTRIES)
            throw new IllegalArgumentException("planet-integrity Atlas snapshot exceeds bounds");
        var entries = new java.util.ArrayList<Entry>(count);
        for (int i = 0; i < count; i++)
            entries.add(new Entry(buf.readUtf(256), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));
        return new PlanetIntegrityAtlasSnapshot(requestId, total, unmappedErased, entries);
    }

    private static boolean coordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 30_000_000.0;
    }
}
