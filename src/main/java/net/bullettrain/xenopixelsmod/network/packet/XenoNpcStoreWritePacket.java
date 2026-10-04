package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * C2S: an operator wrote or deleted an entry in the world store.
 *
 * <p>A packet of its own rather than a reuse of {@link XenoNpcSavePacket}, because that one is
 * entity-scoped from end to end: an entity id, a reach check, a revision compare against the NPC's
 * own, an editing lock, and link propagation. None of that means anything for a faction that
 * belongs to no entity, and making each of those guards conditional on "is there an entity here" is
 * how one of them eventually gets skipped by accident.
 *
 * <p>The guard <em>discipline</em> is the same though, including the part that matters most: every
 * rejection answers the client. A write refused for permission, a bad id or a locked category has
 * to look different from one that worked, which is the whole reason
 * {@link NpcProfileSaveResultPacket} exists. It is reused rather than given a sibling, so the
 * sequential channel gains one packet rather than two.
 */
public record XenoNpcStoreWritePacket(int category, String group, String id, boolean delete,
                                      int expectedRevision, CompoundTag payload) {

    /** Matching the store's own limits, so nothing can be sent that could not then be stored. */
    private static final int MAX_ID = XenoNpcStorePaths.MAX_ID;
    private static final int MAX_GROUP = XenoNpcStorePaths.MAX_GROUP;

    /** A single entry over this is a payload problem, not content. */
    private static final int MAX_KEYS = 96;

    public XenoNpcStoreWritePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(MAX_GROUP), buf.readUtf(MAX_ID), buf.readBoolean(),
                buf.readVarInt(), buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(category);
        buf.writeUtf(group == null ? "" : group, MAX_GROUP);
        buf.writeUtf(id == null ? "" : id, MAX_ID);
        buf.writeBoolean(delete);
        // Never negative on the wire: UNCHECKED is a server-side concession for programmatic
        // writes, not something a client may ask for.
        buf.writeVarInt(Math.max(0, expectedRevision));
        buf.writeNbt(delete ? null : payload);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            // The same bar as editing an NPC. Store content is server-wide, so if anything it
            // wants a higher one, never a lower.
            if (!player.hasPermissions(2)) {
                reject(player, NpcProfileSaveResultPacket.NOT_PERMITTED);
                return;
            }
            // Bounds-checked rather than indexed: the ordinal came off the wire.
            XenoNpcStoreCategory resolved = XenoNpcStoreCategory.byOrdinal(category);
            if (resolved == null) {
                reject(player, NpcProfileSaveResultPacket.NOT_EDITABLE);
                return;
            }
            // Scripts now run on NPC hooks without anyone pressing Run, so writing one is the
            // same authority as running one: level 4, as CustomNPCs' scripter requires.
            if ((resolved == XenoNpcStoreCategory.SCRIPTS || resolved == XenoNpcStoreCategory.PLAYER_SCRIPTS
                    || resolved == XenoNpcStoreCategory.FORGE_SCRIPTS)
                    && !player.hasPermissions(net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts.PERMISSION)) {
                reject(player, NpcProfileSaveResultPacket.NOT_PERMITTED);
                return;
            }
            XenoNpcWorldStore store = XenoNpcStores.get();
            if (store == null) {
                reject(player, NpcProfileSaveResultPacket.GONE);
                return;
            }
            if (!delete && (payload == null || payload.size() > MAX_KEYS)) {
                reject(player, NpcProfileSaveResultPacket.NOT_EDITABLE);
                return;
            }
            if (!delete && resolved == XenoNpcStoreCategory.SCENES) {
                String invalid = net.bullettrain.xenopixelsmod.npc.scene.XenoNpcScene
                        .rejectPayload(payload);
                if (invalid != null) {
                    player.sendSystemMessage(Component.literal("§7NPC store: §f" + invalid));
                    reject(player, NpcProfileSaveResultPacket.NOT_EDITABLE);
                    return;
                }
            }
            if (!delete && resolved == XenoNpcStoreCategory.QUESTS) {
                String json = payload.getString("DefinitionJson");
                if (json.isBlank()) {
                    player.sendSystemMessage(Component.literal("§7NPC store: §fquest has no definition"));
                    reject(player, NpcProfileSaveResultPacket.NOT_EDITABLE);
                    return;
                }
                try {
                    net.bullettrain.xenopixelsmod.features.progression.XenoQuests.parse(
                            id, com.google.gson.JsonParser.parseString(json));
                } catch (RuntimeException invalid) {
                    player.sendSystemMessage(Component.literal(
                            "§7NPC store: §f" + invalid.getMessage()));
                    reject(player, NpcProfileSaveResultPacket.NOT_EDITABLE);
                    return;
                }
            }
            if (!delete && resolved == XenoNpcStoreCategory.DIALOGS) {
                CompoundTag wrapped = new CompoundTag();
                wrapped.put("Dialogue", payload);
                XenoNpcSavePolicy.Validation validation = XenoNpcSavePolicy.validate(wrapped);
                if (!validation.accepted()) {
                    player.sendSystemMessage(Component.literal(
                            "§7NPC store: §f" + validation.reason()));
                    reject(player, NpcProfileSaveResultPacket.NOT_EDITABLE);
                    return;
                }
            }
            if (!delete && resolved == XenoNpcStoreCategory.SPAWNS) {
                // A rule that can never place an NPC is worse than no rule: it sits in the store
                // looking configured while doing nothing. Refuse the write and say why.
                String invalid = net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawn
                        .rejectPayload(payload);
                if (invalid != null) {
                    player.sendSystemMessage(Component.literal("§7NPC store: §f" + invalid));
                    reject(player, NpcProfileSaveResultPacket.NOT_EDITABLE);
                    return;
                }
            }

            if (!delete && (resolved == XenoNpcStoreCategory.SCRIPTS
                    || resolved == XenoNpcStoreCategory.PLAYER_SCRIPTS
                    || resolved == XenoNpcStoreCategory.FORGE_SCRIPTS)) {
                // Source text is the one store payload where an oversized or mis-shaped entry is
                // not merely ugly but dangerous: something will hand it to a script engine. Refuse
                // it here, where the size caps and the field names are known, rather than at run
                // time, where the only answer left is an exception in a log.
                String invalid = net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts
                        .rejectPayload(payload);
                if (invalid != null) {
                    player.sendSystemMessage(Component.literal("§7NPC store: §f" + invalid));
                    reject(player, NpcProfileSaveResultPacket.NOT_EDITABLE);
                    return;
                }
            }

            if (!delete && (resolved == XenoNpcStoreCategory.FACTIONS
                    || resolved == XenoNpcStoreCategory.DIALOGS
                    || resolved == XenoNpcStoreCategory.QUESTS)) {
                // The editor rebuilds these tags from its own fields, which do not include the
                // CustomNPCs number an import recorded. Without this, saving an imported faction
                // once would silently break every script that addressed it by number.
                net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoScriptIds.carrySourceSlot(
                        store.get(resolved, group, id), payload);
            }
            String refusal = delete
                    ? store.remove(resolved, group, id)
                    : store.put(resolved, group, id, payload, Math.max(0, expectedRevision));
            if (refusal != null) {
                // The store's own reason is more useful than a code, and this is an operator-only
                // screen, so it goes to them verbatim.
                player.sendSystemMessage(Component.literal("§7NPC store: §f" + refusal));
                // A lost race reads as STALE so the screen tells them to reopen, rather than as
                // "not editable" which suggests the field itself was wrong.
                reject(player, refusal.contains("reopen the screen")
                        ? NpcProfileSaveResultPacket.STALE
                        : NpcProfileSaveResultPacket.NOT_EDITABLE);
                // Their view is behind, so give them the current one without waiting for a reload.
                ModNetwork.sendToPlayer(player, SyncNpcStoreIndexPacket.current());
                return;
            }

            XenoPixelsMod.LOGGER.info("NPC store: {} {}/{} by {}",
                    delete ? "removed" : "wrote", resolved.folder(), id,
                    player.getName().getString());
            if (resolved == XenoNpcStoreCategory.SCRIPTS || resolved == XenoNpcStoreCategory.PLAYER_SCRIPTS
                    || resolved == XenoNpcStoreCategory.FORGE_SCRIPTS) {
                net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts.changed();
                if (resolved == XenoNpcStoreCategory.FORGE_SCRIPTS) {
                    net.bullettrain.xenopixelsmod.npc.script.ForgeScriptHost.reload(player.getServer());
                }
                if (resolved == XenoNpcStoreCategory.PLAYER_SCRIPTS) {
                    net.bullettrain.xenopixelsmod.npc.script.PlayerScriptHost.reload(player.getServer());
                }
            }
            ModNetwork.sendToPlayer(player, new NpcProfileSaveResultPacket(true, ""));
            // Everyone sees the same store, so everyone is told - not just whoever pressed Add.
            // Without this a second operator's screen would keep listing an entry the first one
            // deleted, and would fail confusingly when they opened it.
            ModNetwork.sendToAll(SyncNpcStoreIndexPacket.current());
            if (resolved == XenoNpcStoreCategory.FACTIONS) {
                ModNetwork.sendToAll(SyncFactionsPacket.current());
            }
            if (resolved == XenoNpcStoreCategory.BANKS) {
                ModNetwork.sendToAll(SyncBanksPacket.current());
            }
            if (resolved == XenoNpcStoreCategory.SPAWNS) {
                ModNetwork.sendToAll(SyncNaturalSpawnsPacket.current());
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void reject(ServerPlayer player, String reason) {
        ModNetwork.sendToPlayer(player, new NpcProfileSaveResultPacket(false, reason));
    }
}
