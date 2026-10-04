package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.npc.store.ClientNpcStoreIndex;
import net.bullettrain.xenopixelsmod.client.npc.XenoNpcEditorScreen;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C: what the world store holds, so the editor can list it.
 *
 * <p>An index, not the content. The store lives under the world folder and is server-side; a client
 * cannot read it, which is why the editor's Global screens showed nothing but disabled buttons for
 * so long. But broadcasting every dialogue in the world to every client would be paying
 * continuously for something almost nobody opens — so this carries ids and labels, and an entry's
 * actual contents are fetched when somebody opens it.
 *
 * <p>Sent on join and after every accepted write, so a second operator's Add shows up on the first
 * operator's screen rather than only after a rejoin.
 */
public record SyncNpcStoreIndexPacket(List<ClientNpcStoreIndex.Entry> entries) {

    /** Store entries plus a bounded server-owned quest catalog. */
    private static final int MAX_STORE_ENTRIES = XenoNpcStoreCategory.values().length
            * XenoNpcWorldStore.MAX_PER_CATEGORY;
    // Preserve the existing protocol bound. Global definitions use remaining slots after the
    // world-store index; a saturated store still syncs as before, without a new packet shape.
    private static final int MAX_ENTRIES = MAX_STORE_ENTRIES;

    private static final int MAX_ID = XenoNpcStorePaths.MAX_ID;
    private static final int MAX_GROUP = XenoNpcStorePaths.MAX_GROUP;

    /** A label is for display only, so it is clamped rather than validated. */
    private static final int MAX_LABEL = 128;

    /** Everything the server currently has stored. */
    public static SyncNpcStoreIndexPacket current() {
        List<ClientNpcStoreIndex.Entry> entries = new ArrayList<>();
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store != null) {
            for (XenoNpcStoreCategory category : XenoNpcStoreCategory.values()) {
                for (XenoNpcWorldStore.Entry entry : store.list(category)) {
                    if (entries.size() >= MAX_STORE_ENTRIES) {
                        break;
                    }
                    entries.add(new ClientNpcStoreIndex.Entry(category, entry.group(), entry.id(),
                            labelOf(entry), entry.revision()));
                }
            }
        }

        java.util.Set<String> questIds = new java.util.HashSet<>();
        for (ClientNpcStoreIndex.Entry entry : entries) {
            if (entry.category() == XenoNpcStoreCategory.QUESTS) {
                questIds.add(entry.id());
            }
        }
        for (String id : ParallelQuests.ids()) {
            if (entries.size() >= MAX_ENTRIES) {
                break;
            }
            ParallelQuests.QuestDef quest = ParallelQuests.definition(id);
            if (quest == null || !questIds.add(quest.id())) {
                continue;
            }
            String title = quest.title() == null || quest.title().isBlank()
                    ? quest.id() : quest.title();
            entries.add(new ClientNpcStoreIndex.Entry(XenoNpcStoreCategory.QUESTS,
                    quest.category(), quest.id(), title, 0));
        }
        return new SyncNpcStoreIndexPacket(entries);
    }

    /**
     * What to show in a list.
     *
     * <p>Most stored things carry a {@code Name}; a dialogue does not, so its id is the label. An
     * entry that showed as a blank row would be one the operator could not tell from any other.
     */
    private static String labelOf(XenoNpcWorldStore.Entry entry) {
        String name = entry.tag().getString("Name");
        return name.isBlank() ? entry.id() : name;
    }

    public SyncNpcStoreIndexPacket(FriendlyByteBuf buf) {
        this(read(buf));
    }

    private static List<ClientNpcStoreIndex.Entry> read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("NPC store index entry count out of bounds: " + count);
        }
        List<ClientNpcStoreIndex.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // Bounds-checked rather than indexed: the ordinal came off the wire, and an unknown
            // one is skipped rather than dropping the rest of the list with it.
            XenoNpcStoreCategory category = XenoNpcStoreCategory.byOrdinal(buf.readVarInt());
            String group = buf.readUtf(MAX_GROUP);
            String id = buf.readUtf(MAX_ID);
            String label = buf.readUtf(MAX_LABEL);
            int revision = buf.readVarInt();
            if (category != null) {
                entries.add(new ClientNpcStoreIndex.Entry(category, group, id, label, revision));
            }
        }
        return entries;
    }

    public void encode(FriendlyByteBuf buf) {
        List<ClientNpcStoreIndex.Entry> list = entries == null ? List.of() : entries;
        int count = Math.min(MAX_ENTRIES, list.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ClientNpcStoreIndex.Entry entry = list.get(i);
            buf.writeVarInt(entry.category().ordinal());
            buf.writeUtf(entry.group(), MAX_GROUP);
            buf.writeUtf(entry.id(), MAX_ID);
            buf.writeUtf(clamp(entry.label()), MAX_LABEL);
            buf.writeVarInt(entry.revision());
        }
    }

    /**
     * Clamped rather than refused.
     *
     * <p>{@code writeUtf} throws past its cap, which would drop the whole packet and leave every
     * Global screen empty because one entry had a wordy name.
     */
    private static String clamp(String label) {
        if (label == null) {
            return "";
        }
        return label.length() <= MAX_LABEL ? label : label.substring(0, MAX_LABEL);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ClientNpcStoreIndex.accept(entries);
            if (Minecraft.getInstance().screen instanceof XenoNpcEditorScreen editor) {
                editor.refreshStoreIndex();
            }
        });
        ctx.setPacketHandled(true);
    }
}
