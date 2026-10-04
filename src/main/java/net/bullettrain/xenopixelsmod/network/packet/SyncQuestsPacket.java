package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.client.npc.quest.ClientQuests;
import net.bullettrain.xenopixelsmod.features.progression.ActiveQuest;
import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C: one player's active quests, so their quest log can show them.
 *
 * <p>Quest state is a server capability and quest definitions are a server-side reload listener.
 * Neither reaches a client on its own, so the log screen would be permanently empty - including in
 * single-player, which still talks to its own integrated server over the network.
 *
 * <p>Per-player, unlike {@code SyncFactionsPacket}: a quest log is the player's own, and
 * broadcasting everybody's would leak progress across a server.
 *
 * <p>Carries the definition text alongside the progress, because the definition is datapack state
 * the client does not have.
 */
public record SyncQuestsPacket(List<ClientQuests.Entry> entries) {

    /** Matches {@code QuestBook.MAX_ACTIVE}. */
    private static final int MAX_QUESTS = 32;

    private static final int MAX_ID = 64;
    private static final int MAX_TITLE = 128;
    private static final int MAX_CATEGORY = 64;

    /** Generous, because this is prose an author writes - but still bounded. */
    private static final int MAX_LOG_TEXT = 4096;

    /** What this player is currently on. */
    public static SyncQuestsPacket forPlayer(ServerPlayer player) {
        XenoPlayerData data = player == null ? null : XenoCapabilities.get(player).orElse(null);
        if (data == null) {
            return new SyncQuestsPacket(List.of());
        }
        List<ClientQuests.Entry> entries = new ArrayList<>();
        for (ActiveQuest quest : data.quests().actives()) {
            if (entries.size() >= MAX_QUESTS) {
                break;
            }
            ParallelQuests.QuestDef def = ParallelQuests.definition(quest.id());
            entries.add(new ClientQuests.Entry(
                    quest.id(),
                    def == null ? quest.id() : def.title(),
                    def == null ? "" : def.category(),
                    def == null ? "" : def.logText(),
                    quest.progress(),
                    quest.target(),
                    quest.ready(),
                    def == null ? "" : def.completerNpc()));
        }
        return new SyncQuestsPacket(entries);
    }

    public SyncQuestsPacket(FriendlyByteBuf buf) {
        this(read(buf));
    }

    private static List<ClientQuests.Entry> read(FriendlyByteBuf buf) {
        int count = Math.min(MAX_QUESTS, buf.readVarInt());
        List<ClientQuests.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(MAX_ID);
            String title = buf.readUtf(MAX_TITLE);
            String category = buf.readUtf(MAX_CATEGORY);
            String logText = buf.readUtf(MAX_LOG_TEXT);
            int progress = buf.readVarInt();
            int target = buf.readVarInt();
            boolean ready = buf.readBoolean();
            String completer = buf.readUtf(MAX_TITLE);
            entries.add(new ClientQuests.Entry(id, title, category, logText,
                    progress, target, ready, completer));
        }
        return entries;
    }

    public void encode(FriendlyByteBuf buf) {
        List<ClientQuests.Entry> list = entries == null ? List.of() : entries;
        int count = Math.min(MAX_QUESTS, list.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ClientQuests.Entry entry = list.get(i);
            buf.writeUtf(entry.id(), MAX_ID);
            buf.writeUtf(entry.title(), MAX_TITLE);
            buf.writeUtf(entry.category(), MAX_CATEGORY);
            // Truncated rather than refused: an over-long journal entry should cost its tail, not
            // the whole quest log.
            buf.writeUtf(trim(entry.logText(), MAX_LOG_TEXT), MAX_LOG_TEXT);
            buf.writeVarInt(entry.progress());
            buf.writeVarInt(entry.target());
            buf.writeBoolean(entry.ready());
            buf.writeUtf(entry.completerNpc(), MAX_TITLE);
        }
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientQuests.accept(entries));
        ctx.setPacketHandled(true);
    }
}
