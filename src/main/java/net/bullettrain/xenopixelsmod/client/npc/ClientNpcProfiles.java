package net.bullettrain.xenopixelsmod.client.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcProfileRequestPacket;
import net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's view of NPC DMZ profiles. The client entity's persistent data is never synced by
 * the server, so screens used to edit a copy full of defaults and save it back over the real
 * profile. This keeps the server's full profile (from the editor payload or a refresh), writes it
 * into the client entity so screens read real values, and sends saves as "baseline + edited" so
 * the server applies only what the screen changed.
 */
public final class ClientNpcProfiles {
    /** A screen that shows an NPC profile and wants server-side changes pushed into it. */
    public interface Refreshable {
        void onServerProfile(int entityId);
    }

    private static final Map<Integer, CompoundTag> BASELINE = new ConcurrentHashMap<>();

    private ClientNpcProfiles() {}

    /** The server's profile for this entity arrived (editor open or refresh). */
    public static void accept(int entityId, CompoundTag profile) {
        if (profile == null || profile.isEmpty()) return;
        BASELINE.put(entityId, profile.copy());
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level == null ? null : mc.level.getEntity(entityId);
        if (entity != null) entity.getPersistentData().put(NpcCombatProfile.NBT_KEY, profile.copy());
        if (mc.screen instanceof Refreshable screen) screen.onServerProfile(entityId);
    }

    /** Asks the server for the real profile once per screen open; the answer arrives via accept. */
    public static void ensure(Entity entity) {
        if (entity != null) ModNetwork.sendToServer(new NpcProfileRequestPacket(entity.getId()));
    }

    /** A copy of the last server profile received for this entity, or null. */
    public static CompoundTag baselineCopy(int entityId) {
        CompoundTag tag = BASELINE.get(entityId);
        return tag == null ? null : tag.copy();
    }

    public static boolean known(Entity entity) {
        return entity != null && BASELINE.containsKey(entity.getId());
    }

    /** Sends a DMZ save that applies only the keys changed since the server profile arrived. */
    public static void save(int id, CompoundTag edited, NpcProfileSavePacket.Action action,
                            String group, String form) {
        CompoundTag baseline = BASELINE.get(id);
        ModNetwork.sendToServer(new NpcProfileSavePacket(id, edited, action, group, form, baseline));
        // The server now holds these values; a refresh follows if it changed anything else.
        if (baseline != null) BASELINE.put(id, edited.copy());
    }

    public static void forgetAll() { BASELINE.clear(); }
}
