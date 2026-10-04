package net.bullettrain.xenopixelsmod.combat.technique;

import java.util.ArrayList;
import java.util.List;

/**
 * Xeno strike ids that must appear in DragonMineZ's {@code strikeSkills} list so they show
 * under Attacks / {@code /dmztech} instead of only living in {@code STRIKE_REGISTRY}.
 */
public final class XenoStrikeSkills {

    private XenoStrikeSkills() {
    }

    public static List<String> ids() {
        List<String> ids = new ArrayList<>();
        ids.add(XenoRushTechniques.RUSH_LEFT);
        ids.add(XenoRushTechniques.RUSH_RIGHT);
        ids.add(XenoRushTechniques.RUSH_BREAKER);
        ids.add(XenoRushTechniques.RUSH_FINISHER);
        ids.add(XenoComboStrikes.RUSH_COMBO);
        ids.add(XenoComboStrikes.LIFT_COMBO);
        return ids;
    }

    public static List<String> append(List<String> existing) {
        List<String> merged = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        installInto(merged);
        return merged;
    }

    /** Mutates {@code live} in place so Gson/config copies keep the Xeno strike ids. */
    public static void installInto(List<String> live) {
        if (live == null) return;
        for (String id : ids()) {
            if (!live.contains(id)) {
                live.add(id);
            }
        }
    }
}
