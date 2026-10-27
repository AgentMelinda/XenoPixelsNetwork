package net.bullettrain.xenopixelsmod.space.hakai;

import net.bullettrain.xenopixelsmod.space.chart.ChartDefinition;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Bounded page of authoritative planet-integrity state for client presentation. */
public record PlanetIntegritySnapshot(long revision, int pageIndex, int pageCount,
                                      double scarredAt, double criticalAt, List<Entry> entries) {
    public static final int PAGE_SIZE = 128;
    public static final int MAX_ENTRIES = PlanetIntegritySavedData.MAX_ENTRIES;
    public static final int MAX_PAGES = (MAX_ENTRIES + PAGE_SIZE - 1) / PAGE_SIZE;

    public record Entry(String bodyId, double integrity) {
        public Entry {
            ChartDefinition.id(bodyId);
            if (bodyId.length() > 256 || !Double.isFinite(integrity) || integrity < 0.0 || integrity >= 100.0)
                throw new IllegalArgumentException("invalid planet-integrity entry");
        }
    }

    public PlanetIntegritySnapshot {
        if (revision < 0 || pageCount < 1 || pageCount > MAX_PAGES || pageIndex < 0 || pageIndex >= pageCount
                || !Double.isFinite(scarredAt) || scarredAt <= 0.0 || scarredAt >= 100.0
                || !Double.isFinite(criticalAt) || criticalAt <= 0.0 || criticalAt > scarredAt
                || entries == null || entries.size() > PAGE_SIZE
                || (pageCount > 1 && pageIndex < pageCount - 1 && entries.size() != PAGE_SIZE)
                || (pageCount > 1 && pageIndex == pageCount - 1
                    && (entries.isEmpty() || (pageCount == MAX_PAGES && entries.size() > MAX_ENTRIES % PAGE_SIZE))))
            throw new IllegalArgumentException("invalid planet-integrity snapshot bounds");
        entries = List.copyOf(entries);
        var ids = new HashSet<String>();
        for (Entry entry : entries) if (!ids.add(entry.bodyId()))
            throw new IllegalArgumentException("duplicate body in planet-integrity snapshot page");
        if (pageCount > 1 && entries.isEmpty()) throw new IllegalArgumentException("empty non-final snapshot page");
    }

    /** Sends sparse saved state, including destroyed IDs no longer present in the active chart. */
    public static List<PlanetIntegritySnapshot> fromSaved(PlanetIntegritySavedData saved,
                                                          long revision, double scarredAt, double criticalAt) {
        if (saved.size() > MAX_ENTRIES)
            throw new IllegalStateException("persisted integrity rows exceed the bounded client snapshot cap");
        for (String id : saved.entries().keySet()) {
            if (id.length() > 256)
                throw new IllegalStateException("persisted integrity body id exceeds the 256-character client wire limit");
        }
        var changed = new ArrayList<Entry>(saved.size());
        saved.entries().forEach((id, integrity) -> changed.add(new Entry(id, integrity)));
        int pages = Math.max(1, (changed.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        var snapshots = new ArrayList<PlanetIntegritySnapshot>(pages);
        for (int page = 0; page < pages; page++) {
            int start = page * PAGE_SIZE;
            int end = Math.min(changed.size(), start + PAGE_SIZE);
            snapshots.add(new PlanetIntegritySnapshot(revision, page, pages, scarredAt, criticalAt,
                    changed.subList(start, end)));
        }
        return List.copyOf(snapshots);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(revision);
        buf.writeVarInt(pageIndex);
        buf.writeVarInt(pageCount);
        buf.writeDouble(scarredAt);
        buf.writeDouble(criticalAt);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUtf(entry.bodyId(), 256);
            buf.writeDouble(entry.integrity());
        }
    }

    public static PlanetIntegritySnapshot decode(FriendlyByteBuf buf) {
        long revision = buf.readVarLong();
        int pageIndex = buf.readVarInt();
        int pageCount = buf.readVarInt();
        double scarredAt = buf.readDouble();
        double criticalAt = buf.readDouble();
        int count = buf.readVarInt();
        if (count < 0 || count > PAGE_SIZE) throw new IllegalArgumentException("planet-integrity page too large");
        var entries = new ArrayList<Entry>(count);
        for (int i = 0; i < count; i++) entries.add(new Entry(buf.readUtf(256), buf.readDouble()));
        return new PlanetIntegritySnapshot(revision, pageIndex, pageCount, scarredAt, criticalAt, entries);
    }
}
