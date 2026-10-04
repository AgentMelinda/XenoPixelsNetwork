package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcAppearanceFx;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcEntityLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies one NPC's edit to the NPCs linked to it.
 *
 * <p>MyNPCs' Advanced &gt; Linked is a list of other NPCs that should be kept the same: edit one and
 * the rest follow. The list has been on {@link NpcCombatProfile#linkedNpcs} for a while, but nothing
 * ever read it, which is why the editor row was a dead control and its key was kept off the
 * server's editable-key whitelist. This is the consumer that makes it real.
 *
 * <h2>One hop, deliberately</h2>
 * A link is not followed through the NPCs it reaches. Two NPCs linked to each other are the normal
 * case, not a mistake, and following links transitively would bounce the same edit between them
 * forever. Propagating exactly one hop makes a cycle harmless without needing a visited-set, and it
 * matches what the reference does: linking is "these are copies of each other", not a graph to walk.
 *
 * <h2>What is copied</h2>
 * Only the subset the editor actually sent, merged over each target's own profile the same way the
 * source's was. A linked NPC therefore keeps everything the edit did not mention - its own position,
 * its own name, and any field the editor left alone.
 */
public final class XenoNpcLinkPropagation {

    private XenoNpcLinkPropagation() {
    }

    /**
     * Copies {@code acceptedSubset} onto every NPC linked from {@code source}.
     *
     * <p>Targets that cannot be reached are skipped rather than dropped from the list: an NPC in an
     * unloaded chunk is not a dead link, and pruning it here would quietly break a link the moment
     * someone saved while the other NPC was out of range.
     *
     * @param acceptedSubset the keys the save policy already accepted; nothing else is applied
     * @return how many linked NPCs were updated
     */
    public static int propagate(MinecraftServer server, XenoNpcEntity source,
                                CompoundTag acceptedSubset) {
        if (server == null || source == null || acceptedSubset == null || acceptedSubset.isEmpty()) {
            return 0;
        }
        List<String> links = NpcCombatProfile.read(source).linkedNpcs;
        if (links.isEmpty()) {
            return 0;
        }

        int updated = 0;
        for (XenoNpcEntity target : resolve(server, source, links)) {
            NpcCombatProfile existing = NpcCombatProfile.read(target);
            // A locked NPC refuses a linked edit for the same reason it refuses a direct one.
            // Otherwise the lock would be bypassable by editing anything linked to it.
            if (existing.editingLocked) {
                continue;
            }
            CompoundTag before = existing.toTag();
            CompoundTag merged = before.copy();
            for (String key : acceptedSubset.getAllKeys()) {
                merged.put(key, acceptedSubset.get(key));
            }
            NpcCombatProfile next = NpcCombatProfile.fromTag(merged);
            if (before.equals(next.toTag())) {
                continue;
            }
            next.write(target);
            if (acceptedSubset.contains("Trades")) {
                target.invalidateTradeOffers();
            }
            XenoNpcBehaviour.apply(target, next);
            NpcAppearanceFx.sync(target);
            target.npcData().markEdited();
            updated++;
        }
        return updated;
    }

    /** The linked NPCs that are loaded right now, excluding the source itself. */
    private static List<XenoNpcEntity> resolve(MinecraftServer server, XenoNpcEntity source,
                                               List<String> links) {
        List<XenoNpcEntity> found = new ArrayList<>();
        for (String raw : links) {
            UUID id = parse(raw);
            // Self-links are ignored rather than rejected. An NPC listing itself is harmless and
            // the source has already been written by the caller.
            if (id == null || id.equals(source.getUUID())) {
                continue;
            }
            LivingEntity entity = NpcEntityLookup.findAlive(server, id);
            if (entity instanceof XenoNpcEntity npc && npc != source) {
                found.add(npc);
            }
        }
        return found;
    }

    /**
     * A UUID string, or null when it is blank or malformed.
     *
     * <p>Public because the editor validates what was typed before adding it to the list, and it
     * should accept exactly what this will later resolve - two spellings of "is this a UUID" would
     * let the editor store an id the server then silently skips.
     */
    public static UUID parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            // A hand-edited datapack or an old save can hold anything. A bad id is one dead link,
            // not a failed save.
            return null;
        }
    }
}
