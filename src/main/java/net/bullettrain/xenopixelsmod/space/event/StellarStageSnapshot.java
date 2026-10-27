package net.bullettrain.xenopixelsmod.space.event;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded client snapshot for chart-star lifecycle visuals; it never changes SpaceSnapshot's wire contract.
 *
 * <p>Wire version 2 adds each star's chart display name. AddonNetwork hashes packet ids, not payload schemas, so a
 * wire-shape change must come with a new id ({@link #PACKET_ID}); an old client is then rejected at connection
 * instead of misreading the payload.
 */
public record StellarStageSnapshot(boolean enabled, int rampTicks, List<Entry> stars) {
    public static final int WIRE_VERSION = 2;
    public static final String PACKET_ID = "stellar_stage_snapshot_v" + WIRE_VERSION;
    public static final int MAX_STARS = 1024;
    public static final int MAX_RAMP_TICKS = 365 * 24_000;
    public static final int MAX_DISPLAY_NAME_LENGTH = 64;
    private static final int MAX_STAGE_NAME_LENGTH = 48;

    /** @param displayName the chart display name, trimmed to {@link #MAX_DISPLAY_NAME_LENGTH}; empty when unknown */
    public record Entry(String id, String displayName, StellarStage stage, long stageAgeTicks) {
        public Entry {
            if (id == null || id.length() > 256 || ResourceLocation.tryParse(id) == null)
                throw new IllegalArgumentException("invalid stellar body id");
            if (stage == null || stageAgeTicks < 0L) throw new IllegalArgumentException("invalid stellar stage entry");
            displayName = boundedName(displayName);
        }

        /** An entry without a display name; clients fall back to an id-derived label. */
        public Entry(String id, StellarStage stage, long stageAgeTicks) {
            this(id, "", stage, stageAgeTicks);
        }
    }

    /** Blank or null becomes empty; a longer name is cut to the bound, never rejected, so a chart cannot block a send. */
    static String boundedName(String name) {
        if (name == null || name.isBlank()) return "";
        String trimmed = name.strip();
        return trimmed.length() <= MAX_DISPLAY_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_DISPLAY_NAME_LENGTH);
    }

    public StellarStageSnapshot {
        if (rampTicks < 1 || rampTicks > MAX_RAMP_TICKS)
            throw new IllegalArgumentException("invalid stellar snapshot clock");
        stars = List.copyOf(stars);
        if (stars.size() > MAX_STARS) throw new IllegalArgumentException("too many chart stars");
        Set<String> ids = new HashSet<>();
        for (Entry star : stars) if (!ids.add(star.id()))
            throw new IllegalArgumentException("duplicate chart star id: " + star.id());
    }

    public static void encode(StellarStageSnapshot packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enabled());
        buffer.writeInt(packet.rampTicks());
        buffer.writeVarInt(packet.stars().size());
        for (Entry star : packet.stars()) {
            buffer.writeUtf(star.id(), 256);
            buffer.writeUtf(star.displayName(), MAX_DISPLAY_NAME_LENGTH);
            buffer.writeUtf(star.stage().name(), MAX_STAGE_NAME_LENGTH);
            buffer.writeLong(star.stageAgeTicks());
        }
    }

    public static StellarStageSnapshot decode(FriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        int rampTicks = buffer.readInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_STARS) throw new IllegalArgumentException("invalid chart star count: " + count);
        var stars = new java.util.ArrayList<Entry>(count);
        for (int i = 0; i < count; i++) {
            String id = buffer.readUtf(256);
            String displayName = buffer.readUtf(MAX_DISPLAY_NAME_LENGTH);
            StellarStage stage = stageFromWire(buffer.readUtf(MAX_STAGE_NAME_LENGTH));
            stars.add(new Entry(id, displayName, stage, buffer.readLong()));
        }
        return new StellarStageSnapshot(enabled, rampTicks, stars);
    }

    static StellarStage stageFromWire(String name) {
        if (name == null || name.length() > MAX_STAGE_NAME_LENGTH) return StellarStage.STABLE;
        try {
            return StellarStage.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return StellarStage.STABLE;
        }
    }
}
