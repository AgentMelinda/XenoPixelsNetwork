package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.minecraft.network.FriendlyByteBuf;

/** Server-authoritative settings required by clients rendering deterministic bodies. */
public record SpaceOrbitClockSnapshot(boolean realOrbits, double epochOffsetSeconds) {
    public static final String PACKET_ID = "space_orbit_settings_v1";

    public SpaceOrbitClockSnapshot {
        if (!Double.isFinite(epochOffsetSeconds) || Math.abs(epochOffsetSeconds) > 1.0e12)
            throw new IllegalArgumentException("invalid orbital epoch offset");
    }

    public static SpaceOrbitClockSnapshot capture() {
        // `realOrbits` here means the client may show moving space visuals; the server's chart config
        // remains authoritative separately when the master space switch is off.
        return new SpaceOrbitClockSnapshot(XenoSpaceConfig.enabled() && XenoSpaceConfig.realOrbits(),
                XenoSpaceConfig.orbitEpochOffsetSeconds());
    }

    /** Uses the level game clock, which NeoForge derives from and synchronizes with the overworld clock. */
    public double secondsAt(long clientGameTick, double partialTick) {
        if (!realOrbits) return Double.NaN;
        return SpaceOrbitClockSample.secondsAt(clientGameTick, partialTick, epochOffsetSeconds);
    }

    /** Defensive render-path conversion: invalid frames hide the body instead of throwing out of rendering. */
    public double clientSecondsAt(long clientGameTick, float partialTick) {
        if (!realOrbits || clientGameTick < 0 || !Float.isFinite(partialTick)) return Double.NaN;
        return secondsAt(clientGameTick, Math.max(0.0f, Math.min(1.0f, partialTick)));
    }

    public static void encode(SpaceOrbitClockSnapshot packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.realOrbits());
        buffer.writeDouble(packet.epochOffsetSeconds());
    }

    public static SpaceOrbitClockSnapshot decode(FriendlyByteBuf buffer) {
        return new SpaceOrbitClockSnapshot(buffer.readBoolean(), buffer.readDouble());
    }
}
