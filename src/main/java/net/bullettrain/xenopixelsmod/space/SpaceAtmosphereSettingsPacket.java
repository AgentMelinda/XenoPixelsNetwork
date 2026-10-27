package net.bullettrain.xenopixelsmod.space;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Bounded client view of the current dimension's atmosphere transition; never carries raw sidecar data. */
public record SpaceAtmosphereSettingsPacket(ResourceLocation dimension, boolean enabled,
                                            double startAltitude, double endAltitude,
                                            boolean cellBound, int surfaceCellX, int surfaceCellZ) {
    public SpaceAtmosphereSettingsPacket(ResourceLocation dimension, boolean enabled,
                                         double startAltitude, double endAltitude) {
        this(dimension, enabled, startAltitude, endAltitude, false, 0, 0);
    }
    public SpaceAtmosphereSettingsPacket {
        if (dimension == null) throw new IllegalArgumentException("dimension is required");
        if (!Double.isFinite(startAltitude) || !Double.isFinite(endAltitude)
                || Math.abs(startAltitude) > 30_000_000 || Math.abs(endAltitude) > 30_000_000)
            throw new IllegalArgumentException("atmosphere altitudes outside supported coordinate range");
        if (cellBound) new net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells.Cell(
                surfaceCellX, surfaceCellZ);
    }

    public static void encode(SpaceAtmosphereSettingsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.dimension());
        buffer.writeBoolean(packet.enabled());
        buffer.writeDouble(packet.startAltitude());
        buffer.writeDouble(packet.endAltitude());
        buffer.writeBoolean(packet.cellBound());
        if (packet.cellBound()) {
            buffer.writeVarInt(packet.surfaceCellX());
            buffer.writeVarInt(packet.surfaceCellZ());
        }
    }

    public static SpaceAtmosphereSettingsPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        boolean enabled = buffer.readBoolean();
        double start = buffer.readDouble();
        double end = buffer.readDouble();
        boolean cellBound = buffer.readBoolean();
        int cellX = cellBound ? buffer.readVarInt() : 0;
        int cellZ = cellBound ? buffer.readVarInt() : 0;
        return new SpaceAtmosphereSettingsPacket(dimension, enabled, start, end, cellBound, cellX, cellZ);
    }
}
