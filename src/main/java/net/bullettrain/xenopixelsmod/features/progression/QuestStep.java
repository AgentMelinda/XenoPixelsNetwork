package net.bullettrain.xenopixelsmod.features.progression;

import java.util.Locale;

/** One thing a quest asks for, with the extra fields MyNPCs stores beside the count. */
public record QuestStep(QuestObjective.Goal goal, int target, boolean takeItems,
                        boolean ignoreDamage, boolean ignoreNbt,
                        int x, int y, int z, String dimension, int radius) {

    public QuestStep {
        goal = goal == null ? new QuestObjective.Goal(QuestObjective.KILL_MOBS) : goal;
        target = Math.max(1, Math.min(32767, target));
        dimension = dimension == null ? "" : dimension.trim().toLowerCase(Locale.ROOT);
        radius = Math.max(0, radius);
    }

    public QuestStep(QuestObjective.Goal goal, int target) {
        this(goal, target, false, false, false, 0, 0, 0, "", 0);
    }
}
