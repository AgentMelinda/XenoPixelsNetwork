package net.bullettrain.xenopixelsmod.space.hakai;

import net.bullettrain.xenopixelsmod.space.chart.ChartDefinition;
import net.minecraft.network.FriendlyByteBuf;

/** Single-body incremental integrity change; avoids resending the chart after each accepted impact. */
public record PlanetIntegrityUpdate(long revision, String bodyId, double integrity,
                                    double scarredAt, double criticalAt) {
    public PlanetIntegrityUpdate {
        ChartDefinition.id(bodyId);
        if (revision < 0 || bodyId.length() > 256 || !Double.isFinite(integrity) || integrity < 0 || integrity > 100
                || !Double.isFinite(scarredAt) || scarredAt <= 0 || scarredAt >= 100
                || !Double.isFinite(criticalAt) || criticalAt <= 0 || criticalAt > scarredAt)
            throw new IllegalArgumentException("invalid planet-integrity update");
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(revision);
        buf.writeUtf(bodyId, 256);
        buf.writeDouble(integrity);
        buf.writeDouble(scarredAt);
        buf.writeDouble(criticalAt);
    }

    public static PlanetIntegrityUpdate decode(FriendlyByteBuf buf) {
        return new PlanetIntegrityUpdate(buf.readVarLong(), buf.readUtf(256), buf.readDouble(),
                buf.readDouble(), buf.readDouble());
    }
}
