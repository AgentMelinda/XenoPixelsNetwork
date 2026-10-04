package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.npc.faction.ClientFactions;
import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.bullettrain.xenopixelsmod.npc.faction.XenoFactions;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C: the faction list, so the client editor can show it.
 *
 * <p>Factions come from a datapack, and datapack loading is a server-side reload listener. Without
 * this the editor's faction screens would be permanently empty on any client - including the one
 * embedded in single-player, since that still talks to its own integrated server over the network.
 * An empty screen reads as broken rather than as not-yet-loaded, which is what this avoids.
 *
 * <p>Sent on join and again after every datapack reload, so an operator running {@code /reload}
 * does not have to rejoin to see a faction they just wrote.
 */
public record SyncFactionsPacket(List<ClientFactions.Entry> entries) {

    /** A pack with more factions than this has other problems; the cap keeps the packet bounded. */
    private static final int MAX_FACTIONS = 256;

    /** Per-faction hostility list cap, for the same reason. */
    private static final int MAX_HOSTILE = 64;

    private static final int MAX_ID = 64;
    private static final int MAX_NAME = 128;

    /**
     * Everything the server currently has, world store and datapack together.
     *
     * <p>Through {@code XenoNpcDataSource} rather than straight to {@code XenoFactions}, so a
     * faction an operator wrote in game reaches the client alongside the pack's - and one that
     * shadows a pack faction appears once, not twice.
     */
    public static SyncFactionsPacket current() {
        List<ClientFactions.Entry> entries = new ArrayList<>();
        for (XenoFaction faction
                : net.bullettrain.xenopixelsmod.npc.store.XenoNpcDataSource.factions()) {
            if (entries.size() < MAX_FACTIONS) {
                entries.add(new ClientFactions.Entry(faction.id(), faction.name(), faction.color(),
                        faction.hostileTo(), faction.defaultStanding(), faction.attackedByMobs(),
                        faction.aggressiveToMobs(), faction.attackableMobs()));
            }
        }
        return new SyncFactionsPacket(entries);
    }

    public SyncFactionsPacket(FriendlyByteBuf buf) {
        this(read(buf));
    }

    private static List<ClientFactions.Entry> read(FriendlyByteBuf buf) {
        int count = Math.min(MAX_FACTIONS, buf.readVarInt());
        List<ClientFactions.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(MAX_ID);
            String name = buf.readUtf(MAX_NAME);
            int color = buf.readInt();
            int standing = buf.readVarInt() - XenoFaction.MAX_STANDING;
            int hostileCount = Math.min(MAX_HOSTILE, buf.readVarInt());
            List<String> hostile = new ArrayList<>(hostileCount);
            for (int h = 0; h < hostileCount; h++) {
                hostile.add(buf.readUtf(MAX_ID));
            }
            boolean attacked = buf.readBoolean();
            boolean aggressive = buf.readBoolean();
            int mobCount = Math.min(MAX_HOSTILE, buf.readVarInt());
            List<String> mobs = new ArrayList<>(mobCount);
            for (int m = 0; m < mobCount; m++) mobs.add(buf.readUtf(MAX_ID));
            entries.add(new ClientFactions.Entry(id, name, color, hostile, standing,
                    attacked, aggressive, mobs));
        }
        return entries;
    }

    public void encode(FriendlyByteBuf buf) {
        List<ClientFactions.Entry> list = entries == null ? List.of() : entries;
        int count = Math.min(MAX_FACTIONS, list.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ClientFactions.Entry entry = list.get(i);
            buf.writeUtf(entry.id(), MAX_ID);
            buf.writeUtf(entry.name(), MAX_NAME);
            buf.writeInt(entry.color());
            // Shifted so a negative standing survives a VarInt, which is unsigned-friendly only.
            buf.writeVarInt(entry.defaultStanding() + XenoFaction.MAX_STANDING);
            int hostile = Math.min(MAX_HOSTILE, entry.hostileTo().size());
            buf.writeVarInt(hostile);
            for (int h = 0; h < hostile; h++) {
                buf.writeUtf(entry.hostileTo().get(h), MAX_ID);
            }
            buf.writeBoolean(entry.attackedByMobs());
            buf.writeBoolean(entry.aggressiveToMobs());
            int mobs = Math.min(MAX_HOSTILE, entry.attackableMobs().size());
            buf.writeVarInt(mobs);
            for (int m = 0; m < mobs; m++) buf.writeUtf(entry.attackableMobs().get(m), MAX_ID);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientFactions.accept(entries));
        ctx.setPacketHandled(true);
    }
}
