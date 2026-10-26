package net.bullettrain.xenopixelsmod.combat;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Clientbound Hakai sweep timing (channel {@code xenopixelsmod:hakai_sweep_timing_v2}). Instead of streaming a progress
 * value every tick (which arrives bunched and made the sweep jump), the server, whose ticks are exact, measures the
 * progress rate and sends "progress {@code progress} now, changing by {@code ratePerTick} per tick". The client anchors
 * it on receipt and computes {@code progress + rate * elapsed} every frame. The server re-sends when the real
 * progress or slope diverges from that line (cancel, restore ramp, another source), with a five-tick refresh ceiling.
 * {@code progress <= 0 && rate <= 0} means the sweep is over and the body is whole.
 */
public record HakaiSweepTiming(int entityId, float progress, float ratePerTick,
                              float impactX, float impactY, float impactZ) {
    public HakaiSweepTiming {
        if (!Float.isFinite(progress) || !Float.isFinite(ratePerTick)) throw new IllegalArgumentException("invalid hakai sweep timing");
        progress = Math.max(0f, Math.min(1f, progress));
        ratePerTick = Math.max(-1f, Math.min(1f, ratePerTick));
        impactX = unit(impactX); impactY = unit(impactY); impactZ = unit(impactZ);
    }

    /** Compatibility constructor for non-impact sources; defaults to the body's centre. */
    public HakaiSweepTiming(int entityId, float progress, float ratePerTick) {
        this(entityId, progress, ratePerTick, 0.5f, 0.5f, 0.5f);
    }

    public static void encode(HakaiSweepTiming p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId); buf.writeFloat(p.progress); buf.writeFloat(p.ratePerTick);
        buf.writeFloat(p.impactX); buf.writeFloat(p.impactY); buf.writeFloat(p.impactZ);
    }

    public static HakaiSweepTiming decode(FriendlyByteBuf buf) {
        return new HakaiSweepTiming(buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    /** The predicted progress {@code ticks} after the anchor; pure, shared by server drift checks and the client. */
    public static float at(float progress, float ratePerTick, double ticks) {
        return (float) Math.max(0.0, Math.min(1.0, progress + ratePerTick * ticks));
    }

    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0.5f;
    }
}
