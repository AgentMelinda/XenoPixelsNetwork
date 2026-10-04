package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.npc.bank.ClientBanks;
import net.bullettrain.xenopixelsmod.npc.bank.BankDefinition;
import net.bullettrain.xenopixelsmod.npc.bank.BankTab;
import net.bullettrain.xenopixelsmod.npc.bank.Banks;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C: the bank list, so the client editor can show it.
 *
 * <p>Banks live in the world store, which only the server reads. Without this the editor's Banks
 * page would be permanently empty — the exact bug the faction import hit, and the reason
 * {@code docs/xeno-npc-schema.md} §0.1 requires a push at every server-side mutation rather than
 * only on join.
 *
 * <p>Sent on join, after {@code /reload}, and after any write to the bank store.
 *
 * <p>Carries no account data. What a player holds in a vault is per-player and never travels here.
 */
public record SyncBanksPacket(List<ClientBanks.Entry> entries) {

    /** A world with more banks than this has other problems; the cap keeps the packet bounded. */
    private static final int MAX_BANKS = 256;

    private static final int MAX_ID = 64;
    private static final int MAX_NAME = 128;

    /** Everything the server's store currently holds. */
    public static SyncBanksPacket current() {
        List<ClientBanks.Entry> entries = new ArrayList<>();
        for (BankDefinition bank : Banks.all()) {
            if (entries.size() >= MAX_BANKS) {
                break;
            }
            List<ClientBanks.Tab> tabs = new ArrayList<>();
            for (BankTab tab : bank.tabs()) {
                tabs.add(new ClientBanks.Tab(tab.name(), tab.costItem(), tab.costCount(),
                        tab.startSlots(), tab.upgradable()));
            }
            entries.add(new ClientBanks.Entry(bank.id(), bank.name(), bank.withdrawFeePercent(),
                    tabs));
        }
        return new SyncBanksPacket(entries);
    }

    public SyncBanksPacket(FriendlyByteBuf buf) {
        this(read(buf));
    }

    private static List<ClientBanks.Entry> read(FriendlyByteBuf buf) {
        int count = Math.min(MAX_BANKS, buf.readVarInt());
        List<ClientBanks.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(MAX_ID);
            String name = buf.readUtf(MAX_NAME);
            int fee = buf.readVarInt();
            int tabCount = Math.min(BankDefinition.MAX_TABS, buf.readVarInt());
            List<ClientBanks.Tab> tabs = new ArrayList<>(tabCount);
            for (int t = 0; t < tabCount; t++) {
                tabs.add(new ClientBanks.Tab(buf.readUtf(MAX_NAME), buf.readUtf(MAX_ID),
                        buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
            }
            entries.add(new ClientBanks.Entry(id, name, fee, tabs));
        }
        return entries;
    }

    public void encode(FriendlyByteBuf buf) {
        List<ClientBanks.Entry> list = entries == null ? List.of() : entries;
        int count = Math.min(MAX_BANKS, list.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ClientBanks.Entry entry = list.get(i);
            buf.writeUtf(entry.id(), MAX_ID);
            buf.writeUtf(entry.name(), MAX_NAME);
            buf.writeVarInt(entry.withdrawFeePercent());
            int tabs = Math.min(BankDefinition.MAX_TABS, entry.tabs().size());
            buf.writeVarInt(tabs);
            for (int t = 0; t < tabs; t++) {
                ClientBanks.Tab tab = entry.tabs().get(t);
                buf.writeUtf(tab.name(), MAX_NAME);
                buf.writeUtf(tab.costItem(), MAX_ID);
                buf.writeVarInt(tab.costCount());
                buf.writeVarInt(tab.startSlots());
                buf.writeBoolean(tab.upgradable());
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientBanks.accept(entries));
        ctx.setPacketHandled(true);
    }
}
