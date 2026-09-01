package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;

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
}
