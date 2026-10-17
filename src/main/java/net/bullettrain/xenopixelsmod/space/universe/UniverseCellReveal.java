package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/** One newly discovered system; avoids rebuilding and resending the full Atlas snapshot for every reveal. */
public record UniverseCellReveal(long fieldSeed, boolean warpAllowed, UniverseField.Cell cell) {
    public UniverseCellReveal {
        Objects.requireNonNull(cell, "cell");
    }

    public static void encode(UniverseCellReveal packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.fieldSeed);
        buf.writeBoolean(packet.warpAllowed);
        buf.writeVarInt(packet.cell.x());
        buf.writeVarInt(packet.cell.z());
    }

    public static UniverseCellReveal decode(FriendlyByteBuf buf) {
        return new UniverseCellReveal(buf.readLong(), buf.readBoolean(),
                new UniverseField.Cell(buf.readVarInt(), buf.readVarInt()));
    }
}
