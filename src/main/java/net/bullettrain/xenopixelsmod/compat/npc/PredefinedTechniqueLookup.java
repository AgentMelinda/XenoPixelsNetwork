package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Lookup over DragonMineZ's global technique registry
 * ({@code PredefinedTechniques#REGISTRY} / {@code #isPredefinedTechniqueId}).
 * Reachable with no {@code StatsData} or {@code Player} — unlike a player's owned
 * {@code Techniques}.
 */
public final class PredefinedTechniqueLookup {
    private PredefinedTechniqueLookup() {}

    public static KiAttackData find(String id) {
        return id == null ? null : PredefinedTechniques.REGISTRY.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public static StrikeAttackData findStrike(String id) {
        return id == null ? null : PredefinedTechniques.STRIKE_REGISTRY.get(
                id.toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean isKnown(String id) {
        return id != null && PredefinedTechniques.isPredefinedTechniqueId(
                id.toLowerCase(java.util.Locale.ROOT));
    }

    /** The predefined ki and strike techniques this NPC dispatcher can actually execute. */
    public static List<String> npcTechniqueIds() {
        TreeSet<String> ids = new TreeSet<>();
        for (String id : PredefinedTechniques.REGISTRY.keySet()) {
            if (NpcKiAttackDispatcher.supportsPredefinedTechnique(id)) {
                ids.add(id.toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (String id : PredefinedTechniques.STRIKE_REGISTRY.keySet()) {
            ids.add(id.toLowerCase(java.util.Locale.ROOT));
        }
        return List.copyOf(new ArrayList<>(ids));
    }

    /** A per-NPC copy of a DMZ ki technique with that NPC's authored upgrade levels applied. */
    public static KiAttackData forNpc(String id, NpcCombatProfile profile) {
        KiAttackData template = find(id);
        if (template == null) {
            return null;
        }
        KiAttackData copy = new KiAttackData();
        copy.load(template.save());
        if (profile != null) {
            profile.techniqueLevels.apply(id, copy);
        }
        return copy;
    }

    /** A per-NPC copy of a DMZ strike with that NPC's authored upgrade levels applied. */
    public static StrikeAttackData strikeForNpc(String id, NpcCombatProfile profile) {
        StrikeAttackData template = findStrike(id);
        if (template == null) {
            return null;
        }
        StrikeAttackData copy = new StrikeAttackData();
        copy.load(template.save());
        if (profile != null) {
            profile.techniqueLevels.apply(id, copy);
        }
        return copy;
    }
}
