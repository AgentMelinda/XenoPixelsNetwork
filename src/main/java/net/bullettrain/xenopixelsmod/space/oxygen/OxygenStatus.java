package net.bullettrain.xenopixelsmod.space.oxygen;

import net.minecraft.network.FriendlyByteBuf;

/** Clientbound oxygen HUD state (channel {@code xenopixelsmod:oxygen_status_v1}); all values in ticks. */
public record OxygenStatus(int breath, int maxBreath, int reserve, int phase) {
    public OxygenStatus {
        if (breath < 0 || maxBreath <= 0 || reserve < 0 || phase < 0 || phase >= OxygenRules.Phase.values().length)
            throw new IllegalArgumentException("invalid oxygen status");
    }

    public OxygenRules.Phase phaseValue() { return OxygenRules.Phase.values()[phase]; }

    public static void encode(OxygenStatus packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.breath); buf.writeVarInt(packet.maxBreath); buf.writeVarInt(packet.reserve); buf.writeVarInt(packet.phase);
    }

    public static OxygenStatus decode(FriendlyByteBuf buf) {
        return new OxygenStatus(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
