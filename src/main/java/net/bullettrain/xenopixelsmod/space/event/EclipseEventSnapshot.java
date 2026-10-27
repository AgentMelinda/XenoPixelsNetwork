package net.bullettrain.xenopixelsmod.space.event;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Clientbound, bounded list of active eclipse event markers for the Galaxy Atlas. An empty list clears them. */
public record EclipseEventSnapshot(List<Entry> entries) {
    public static final int MAX_ENTRIES = 32;
    private static final int MAX_ID_LENGTH = 256;

    public record Entry(String starId, String planetId, String moonId, float coverage,
                        double x, double y, double z, float radius, long startTick) {
        public Entry {
            requireId(starId); requireId(planetId); requireId(moonId);
            if (!Float.isFinite(coverage) || coverage < 0f || coverage > 1f || !Float.isFinite(radius) || radius < 0f
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("invalid eclipse event entry");
        }

        static Entry of(EclipseEvents.Active active) {
            return new Entry(active.triple().starId(), active.triple().planetId(), active.triple().moonId(),
                    (float) Math.max(0.0, Math.min(1.0, active.coverage())), active.x(), active.y(), active.z(),
                    (float) Math.max(0.0, active.radius()), active.startTick());
        }
    }

    public EclipseEventSnapshot {
        if (entries == null || entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("too many eclipse events");
        entries = List.copyOf(entries);
    }

    /** Builds a snapshot from the tracker, keeping at most {@link #MAX_ENTRIES} in start order. */
    public static EclipseEventSnapshot of(List<EclipseEvents.Active> active) {
        var sorted = new ArrayList<>(active);
        sorted.sort(java.util.Comparator.comparingLong(EclipseEvents.Active::startTick)
                .thenComparing(entry -> entry.triple().key()));
        var entries = new ArrayList<Entry>(Math.min(MAX_ENTRIES, sorted.size()));
        for (var eclipse : sorted) {
            if (entries.size() >= MAX_ENTRIES) break;
            entries.add(Entry.of(eclipse));
        }
        return new EclipseEventSnapshot(entries);
    }

    public static void encode(EclipseEventSnapshot packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entries().size());
        for (Entry entry : packet.entries()) {
            buffer.writeUtf(entry.starId(), MAX_ID_LENGTH);
            buffer.writeUtf(entry.planetId(), MAX_ID_LENGTH);
            buffer.writeUtf(entry.moonId(), MAX_ID_LENGTH);
            buffer.writeFloat(entry.coverage());
            buffer.writeDouble(entry.x()); buffer.writeDouble(entry.y()); buffer.writeDouble(entry.z());
            buffer.writeFloat(entry.radius());
            buffer.writeLong(entry.startTick());
        }
    }

    public static EclipseEventSnapshot decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("too many eclipse events: " + count);
        var entries = new ArrayList<Entry>(count);
        for (int i = 0; i < count; i++)
            entries.add(new Entry(buffer.readUtf(MAX_ID_LENGTH), buffer.readUtf(MAX_ID_LENGTH), buffer.readUtf(MAX_ID_LENGTH),
                    buffer.readFloat(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readFloat(), buffer.readLong()));
        return new EclipseEventSnapshot(entries);
    }

    private static void requireId(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH) throw new IllegalArgumentException("invalid body id");
    }
}
