package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.SyncFactionsPacket;
import net.bullettrain.xenopixelsmod.network.packet.SyncNpcStoreIndexPacket;
import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.bullettrain.xenopixelsmod.npc.store.XenoFactionNbt;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcDataSource;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.entity.ICustomNpc;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.handler.data.IFaction;

import java.util.ArrayList;
import java.util.List;

/**
 * A native faction as XenoAPI's {@link IFaction}. Setters change this view only; {@link #save()}
 * writes it to the world store, which shadows a datapack faction of the same id, as the editor's
 * save does. {@link #getId()} is the CustomNPCs number an import recorded, or
 * {@link XenoScriptIds#NONE} for a Xeno-made faction.
 */
public final class XenoFactionAdapter implements IFaction {
    /** Stored beside the native fields; nothing native reads it, CustomNPCs scripts do. */
    static final String TAG_HIDDEN = "HideFaction";

    private final String id;
    private XenoFaction pending;
    private Boolean hiddenPending;

    XenoFactionAdapter(String id) {
        this(id, null);
    }

    XenoFactionAdapter(String id, XenoFaction snapshot) {
        this.id = id;
        this.pending = snapshot;
    }

    /** The native faction id behind this view. */
    public String nativeId() { return id; }

    private XenoFaction faction() {
        if (pending != null) return pending;
        XenoFaction live = XenoNpcDataSource.faction(id);
        if (live == null) throw new CustomNPCsException("Faction %s no longer exists", id);
        return live;
    }

    private void change(XenoFaction next) { pending = next; }

    private static XenoFaction with(XenoFaction f, List<String> hostile, int standing, boolean attacked) {
        return new XenoFaction(f.id(), f.name(), f.color(), hostile, standing, attacked,
                f.aggressiveToMobs(), f.attackableMobs());
    }

    @Override public int getId() { return XenoScriptIds.factionSlot(id); }
    @Override public String getName() { return faction().name(); }
    @Override public int getDefaultPoints() { return faction().defaultStanding(); }
    @Override public int getColor() { return faction().color(); }

    @Override
    public void setDefaultPoints(int points) {
        XenoFaction f = faction();
        change(with(f, f.hostileTo(), points, f.attackedByMobs()));
    }

    /** -1 hostile, 0 neutral, 1 friendly, from the player's standing and the native thresholds. */
    @Override
    public int playerStatus(IPlayer player) {
        if (!(XenoApiAdapters.unwrap(player) instanceof ServerPlayer target)) {
            throw new IllegalArgumentException("IFaction.playerStatus: player cannot be null");
        }
        return status(target, id);
    }

    static int status(ServerPlayer player, String factionId) {
        int standing = XenoCapabilities.get(player).map(d -> d.getFactionStanding(factionId)).orElse(0);
        return switch (XenoFaction.attitudeAt(standing)) {
            case HOSTILE -> -1;
            case NEUTRAL -> 0;
            case FRIENDLY -> 1;
        };
    }

    @Override
    public boolean hostileToNpc(ICustomNpc npc) {
        if (!(XenoApiAdapters.unwrap(npc) instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity target)) {
            throw new IllegalArgumentException("IFaction.hostileToNpc: npc cannot be null");
        }
        return faction().isHostileTo(target.npcData().faction());
    }

    @Override
    public boolean hostileToFaction(int factionId) {
        String other = XenoScriptIds.factionId(factionId);
        return other != null && faction().isHostileTo(other);
    }

    /** The numbers of the hostile factions that have one; Xeno-made ones are not listed. */
    @Override
    public int[] getHostileList() {
        return faction().hostileTo().stream().mapToInt(XenoScriptIds::factionSlot)
                .filter(slot -> slot != XenoScriptIds.NONE).toArray();
    }

    private static String requireFaction(String method, int number) {
        String other = XenoScriptIds.factionId(number);
        if (other == null) throw new CustomNPCsException("%s: no faction has number %s", method, number);
        return other;
    }

    @Override
    public void addHostile(int id) {
        String other = requireFaction("IFaction.addHostile", id);
        XenoFaction f = faction();
        if (f.hostileTo().contains(other)) return;
        List<String> hostile = new ArrayList<>(f.hostileTo());
        hostile.add(other);
        change(with(f, hostile, f.defaultStanding(), f.attackedByMobs()));
    }

    @Override
    public void removeHostile(int id) {
        String other = XenoScriptIds.factionId(id);
        XenoFaction f = faction();
        if (other == null || !f.hostileTo().contains(other)) return;
        List<String> hostile = new ArrayList<>(f.hostileTo());
        hostile.remove(other);
        change(with(f, hostile, f.defaultStanding(), f.attackedByMobs()));
    }

    @Override public boolean hasHostile(int id) { return hostileToFaction(id); }

    @Override
    public boolean getIsHidden() {
        if (hiddenPending != null) return hiddenPending;
        XenoNpcWorldStore store = XenoNpcStores.get();
        CompoundTag tag = store == null ? null : store.get(XenoNpcStoreCategory.FACTIONS, "", id);
        return tag != null && tag.getBoolean(TAG_HIDDEN);
    }

    @Override public void setIsHidden(boolean bo) { hiddenPending = bo; }
    @Override public boolean getAttackedByMobs() { return faction().attackedByMobs(); }

    @Override
    public void setAttackedByMobs(boolean bo) {
        XenoFaction f = faction();
        change(with(f, f.hostileTo(), f.defaultStanding(), bo));
    }

    /** Writes this view to the world store and tells every client, as an editor save does. */
    @Override
    public void save() {
        XenoApiAdapters.requireServerThreadNow();
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) throw new IllegalStateException("IFaction.save needs a loaded world");
        CompoundTag previous = store.get(XenoNpcStoreCategory.FACTIONS, "", id);
        CompoundTag tag = previous == null ? new CompoundTag() : previous.copy();
        tag.merge(XenoFactionNbt.write(faction()));
        if (hiddenPending != null) tag.putBoolean(TAG_HIDDEN, hiddenPending);
        String refusal = store.put(XenoNpcStoreCategory.FACTIONS, "", id, tag);
        if (refusal != null) throw new CustomNPCsException("IFaction.save: %s", refusal);
        pending = null;
        hiddenPending = null;
        broadcast();
    }

    static void broadcast() {
        if (net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() == null) return;
        ModNetwork.sendToAll(SyncNpcStoreIndexPacket.current());
        ModNetwork.sendToAll(SyncFactionsPacket.current());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof XenoFactionAdapter faction && faction.id.equals(id);
    }

    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return id; }
}
