package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.client.npc.faction.ClientStandings;
import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S2C: this player's faction standings, so the Factions tab can show their own.
 *
 * <p>{@code SyncFactionsPacket} carries what the factions are, including each one's default
 * standing - the same for everybody. This carries what one player has actually earned, which is
 * per-player and must not be broadcast.
 */
public record SyncStandingsPacket(Map<String, Integer> standings) {

    /** Matches {@code SyncFactionsPacket.MAX_FACTIONS}. */
    private static final int MAX_STANDINGS = 256;

    private static final int MAX_ID = 64;

    /** What this player currently stands at. */
    public static SyncStandingsPacket forPlayer(ServerPlayer player) {
        XenoPlayerData data = player == null ? null : XenoCapabilities.get(player).orElse(null);
        return new SyncStandingsPacket(data == null ? Map.of() : data.factionStandings());
    }

    public SyncStandingsPacket(FriendlyByteBuf buf) {
        this(read(buf));
    }

    private static Map<String, Integer> read(FriendlyByteBuf buf) {
        int count = Math.min(MAX_STANDINGS, buf.readVarInt());
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(MAX_ID);
            // Shifted so a negative standing survives a VarInt, which is unsigned-friendly only -
            // the same trick SyncFactionsPacket uses, for the same reason.
            int standing = buf.readVarInt() - XenoFaction.MAX_STANDING;
            out.put(id, standing);
        }
        return out;
    }

    public void encode(FriendlyByteBuf buf) {
        Map<String, Integer> map = standings == null ? Map.of() : standings;
        int count = Math.min(MAX_STANDINGS, map.size());
        buf.writeVarInt(count);
        int written = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (written >= count) {
                break;
            }
            buf.writeUtf(entry.getKey(), MAX_ID);
            buf.writeVarInt(XenoFaction.clampStanding(
                    entry.getValue() == null ? 0 : entry.getValue()) + XenoFaction.MAX_STANDING);
            written++;
        }
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientStandings.accept(standings));
        ctx.setPacketHandled(true);
    }
}
