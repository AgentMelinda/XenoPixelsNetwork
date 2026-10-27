package net.bullettrain.xenopixelsmod.space.event;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded server-authored event origins for the client-side expanding shock-front visual. */
public record StellarBlastWaveSnapshot(boolean enabled, int durationTicks, double speedBlocksPerTick,
                                       List<Wave> waves) {
    public static final String PACKET_ID = "stellar_blast_wave_v1";
    public static final int MAX_WAVES = 64;
    private static final int MAX_ID_LENGTH = 256;
    private static final int MAX_NAME_LENGTH = 64;

    public record Wave(String id, String displayName, double x, double y, double z, long ageTicks) {
        public Wave {
            if (id == null || id.length() > MAX_ID_LENGTH || ResourceLocation.tryParse(id) == null)
                throw new IllegalArgumentException("invalid stellar wave id");
            displayName = StellarStageSnapshot.boundedName(displayName);
            if (!coordinate(x) || !coordinate(y) || !coordinate(z) || ageTicks < 0L)
                throw new IllegalArgumentException("invalid stellar wave origin or age");
        }

        private static boolean coordinate(double value) {
            return Double.isFinite(value) && Math.abs(value) <= StellarBlastWaveMath.MAX_RADIUS_BLOCKS;
        }
    }

    public StellarBlastWaveSnapshot {
        if (durationTicks < 1 || durationTicks > StellarStageSnapshot.MAX_RAMP_TICKS
                || !Double.isFinite(speedBlocksPerTick) || speedBlocksPerTick <= 0.0
                || speedBlocksPerTick > 1_000.0)
            throw new IllegalArgumentException("invalid stellar wave settings");
        waves = List.copyOf(waves);
        if (waves.size() > MAX_WAVES) throw new IllegalArgumentException("too many stellar waves");
        Set<String> ids = new HashSet<>();
        for (Wave wave : waves) {
            if (wave.ageTicks() > durationTicks)
                throw new IllegalArgumentException("stellar wave age exceeds duration");
            if (!ids.add(wave.id())) throw new IllegalArgumentException("duplicate stellar wave id: " + wave.id());
        }
        if (!enabled && !waves.isEmpty()) throw new IllegalArgumentException("disabled snapshot cannot contain waves");
    }

    public static StellarBlastWaveSnapshot empty(boolean enabled, int durationTicks, double speedBlocksPerTick) {
        return new StellarBlastWaveSnapshot(enabled, durationTicks, speedBlocksPerTick, List.of());
    }

    public static void encode(StellarBlastWaveSnapshot packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enabled());
        buffer.writeVarInt(packet.durationTicks());
        buffer.writeDouble(packet.speedBlocksPerTick());
        buffer.writeVarInt(packet.waves().size());
        for (Wave wave : packet.waves()) {
            buffer.writeUtf(wave.id(), MAX_ID_LENGTH);
            buffer.writeUtf(wave.displayName(), MAX_NAME_LENGTH);
            buffer.writeDouble(wave.x());
            buffer.writeDouble(wave.y());
            buffer.writeDouble(wave.z());
            buffer.writeLong(wave.ageTicks());
        }
    }

    public static StellarBlastWaveSnapshot decode(FriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        int duration = buffer.readVarInt();
        double speed = buffer.readDouble();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_WAVES) throw new IllegalArgumentException("invalid stellar wave count: " + count);
        var waves = new java.util.ArrayList<Wave>(count);
        for (int i = 0; i < count; i++) {
            waves.add(new Wave(buffer.readUtf(MAX_ID_LENGTH), buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readLong()));
        }
        return new StellarBlastWaveSnapshot(enabled, duration, speed, waves);
    }
}
