package net.bullettrain.xenopixelsmod.compat.npc.clone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns a CustomNPCs clone into one My NPCs will load.
 *
 * <p>My NPCs is CustomNPCs with its package renamed, and its clones are very nearly the same file:
 * comparing real saves, about 190 top-level keys of which 170 are shared, and only two values in the
 * whole tree carry the mod's own namespace. So this is a small, precise conversion rather than a
 * translation — which is what makes it safe to run automatically.
 *
 * <p>Three things change:
 * <ul>
 *   <li>the {@code customnpcs:} namespace becomes {@code mynpcs:}, on the entity id and the skin
 *       texture. The tree is walked rather than the file being string-replaced, so item ids like
 *       {@code dragonminez:senzu_bean} and attribute ids like {@code minecraft:generic.max_health}
 *       cannot be caught by accident;</li>
 *   <li>keys only CustomNPCs has are dropped — the companion system, the vanilla hunger fields it
 *       inherits from {@code Player}, and NeoForge's attachment blob, none of which the fork reads;
 *       </li>
 *   <li>keys only My NPCs has are seeded at the values a native clone carries, so a converted clone
 *       is indistinguishable from one the fork saved and cannot trip code that reads a field without
 *       first checking it exists.</li>
 * </ul>
 *
 * <p>Nothing outside those lists is touched: an unrecognised key is <em>kept</em>, not dropped, so a
 * My NPCs update that adds fields degrades to "a slightly foreign clone" rather than to data loss.
 *
 * <p>Pure {@link CompoundTag} in, {@link CompoundTag} out, with no world or mod access, so it can be
 * tested against real clone files.
 */
public final class NpcCloneConverter {

    /** The entity id each mod writes into a clone. */
    public static final String CUSTOMNPCS_ID = "customnpcs:customnpc";
    public static final String MYNPCS_ID = "mynpcs:customnpc";

    private static final String CUSTOMNPCS_NAMESPACE = "customnpcs:";
    private static final String MYNPCS_NAMESPACE = "mynpcs:";

    /**
     * Keys CustomNPCs writes that My NPCs has no field for.
     *
     * <p>Measured by diffing real clones from both mods: the companion system, the four hunger
     * fields CustomNPCs inherits by extending a player-like entity, and NeoForge's attachment blob,
     * which holds capability data belonging to a mod id that will not be loaded.
     */
    private static final Set<String> CUSTOMNPCS_ONLY = Set.of(
            "CompanionAge", "CompanionCanAge", "CompanionDefendOwner", "CompanionExp",
            "CompanionHasInv", "CompanionID", "CompanionInventory", "CompanionJob",
            "CompanionOwner", "CompanionOwnerName", "CompanionStage", "CompanionTalents",
            "foodExhaustionLevel", "foodLevel", "foodSaturationLevel", "foodTickTimer",
            "neoforge:attachments");

    /** How deep the namespace walk will go before giving up, in case of a cyclic or absurd tag. */
    private static final int MAX_DEPTH = 24;

    private NpcCloneConverter() {
    }

    /** Whether this clone came from CustomNPCs and therefore needs converting. */
    public static boolean isCustomNpcsClone(CompoundTag clone) {
        return clone != null && CUSTOMNPCS_ID.equals(clone.getString("id"));
    }

    /** Whether this clone is already in My NPCs' own format. */
    public static boolean isMyNpcsClone(CompoundTag clone) {
        return clone != null && MYNPCS_ID.equals(clone.getString("id"));
    }

    /**
     * Converts a CustomNPCs clone, or returns the input untouched when it is not one.
     *
     * <p>A My NPCs clone passes straight through, so this is safe to call on every file in a folder
     * holding both — and running it twice changes nothing the second time.
     *
     * @return a converted copy; the caller's tag is never modified
     */
    public static CompoundTag convert(CompoundTag clone) {
        if (!isCustomNpcsClone(clone)) {
            return clone;
        }
        CompoundTag out = clone.copy();
        for (String key : CUSTOMNPCS_ONLY) {
            out.remove(key);
        }
        rewriteNamespace(out, 0);
        seedMyNpcsDefaults(out);
        return out;
    }

    /**
     * Migrates a <em>live</em> NPC's tag in place, so it loads as a My NPCs entity.
     *
     * <p>Used while the world is loading: a chunk holds these NPCs as entities with
     * {@code id: customnpcs:customnpc}, and with CustomNPCs uninstalled vanilla cannot resolve that
     * id, logs "Skipping Entity with id", and drops the entity — permanently, once the chunk saves
     * again. Rewriting the id first is the difference between the NPC surviving and being deleted.
     *
     * <p>Unlike {@link #convert}, this <b>keeps</b> the keys only CustomNPCs uses. That is
     * deliberate. A clone is a template being re-saved, where a clean file is what is wanted; a live
     * entity is somebody's actual NPC and this migration is one-way, so nothing is thrown away that
     * does not have to be. My NPCs ignores tags it does not recognise, which costs nothing.
     *
     * @return true when this was a CustomNPCs NPC and has been migrated
     */
    public static boolean migrateEntityInPlace(CompoundTag entity) {
        if (!isCustomNpcsClone(entity)) {
            return false;
        }
        rewriteNamespace(entity, 0);
        seedMyNpcsDefaults(entity);
        return true;
    }

    /**
     * The keys this converter would drop that are actually present, for reporting.
     *
     * <p>Worth surfacing rather than dropping in silence: losing a companion NPC's owner is the kind
     * of thing somebody should be told about once, not discover later.
     */
    public static List<String> droppedKeys(CompoundTag clone) {
        List<String> dropped = new ArrayList<>();
        if (clone == null) {
            return dropped;
        }
        for (String key : CUSTOMNPCS_ONLY) {
            if (clone.contains(key)) {
                dropped.add(key);
            }
        }
        dropped.sort(String::compareTo);
        return dropped;
    }

    /** Rewrites this mod's own namespace wherever it appears as a string value, at any depth. */
    private static void rewriteNamespace(Tag tag, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        if (tag instanceof CompoundTag compound) {
            // Copy the key set first: the values are replaced as we go.
            for (String key : new ArrayList<>(compound.getAllKeys())) {
                Tag child = compound.get(key);
                String rewritten = rewritten(child);
                if (rewritten != null) {
                    compound.putString(key, rewritten);
                } else {
                    rewriteNamespace(child, depth + 1);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                Tag child = list.get(i);
                String rewritten = rewritten(child);
                if (rewritten != null) {
                    list.set(i, StringTag.valueOf(rewritten));
                } else {
                    rewriteNamespace(child, depth + 1);
                }
            }
        }
    }

    /** The re-namespaced value of a string tag, or null when it is not one of ours to change. */
    private static String rewritten(Tag tag) {
        if (!(tag instanceof StringTag)) {
            return null;
        }
        String value = tag.getAsString();
        return value.startsWith(CUSTOMNPCS_NAMESPACE)
                ? MYNPCS_NAMESPACE + value.substring(CUSTOMNPCS_NAMESPACE.length())
                : null;
    }

    /**
     * Adds the fields My NPCs writes and CustomNPCs does not, at the values a native clone carries.
     *
     * <p>Only ever adds: a clone that already has one of these — because it was converted before, or
     * because a future CustomNPCs gains the field — keeps whatever it had.
     */
    private static void seedMyNpcsDefaults(CompoundTag clone) {
        if (!clone.contains("CNPC_persistantData")) {
            clone.put("CNPC_persistantData", new CompoundTag());
        }
        putByteIfAbsent(clone, "FtbQuestCompleteEnabled");
        putByteIfAbsent(clone, "FtbQuestJobRequired");
        putByteIfAbsent(clone, "FtbQuestRequiredEnabled");
        putByteIfAbsent(clone, "FtbQuestRoleRequired");
        putStringIfAbsent(clone, "FtbQuestCompleteId");
        putStringIfAbsent(clone, "FtbQuestRequiredId");
    }

    private static void putByteIfAbsent(CompoundTag clone, String key) {
        if (!clone.contains(key)) {
            clone.putBoolean(key, false);
        }
    }

    private static void putStringIfAbsent(CompoundTag clone, String key) {
        if (!clone.contains(key)) {
            clone.putString(key, "");
        }
    }
}
