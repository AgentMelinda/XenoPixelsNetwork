package net.bullettrain.xenopixelsmod.features.progression;

import java.util.List;

/** Keeps XenoSkill-point rewards owned by quests offered by native Xeno NPCs. */
public final class QuestRewardOriginPolicy {
    private static final QuestReward NO_REWARD =
            new QuestReward(0, 0, List.of(), List.of(), List.of());

    private QuestRewardOriginPolicy() {}

    /**
     * Third-party and command-started quests may still pay their declared vanilla rewards and
     * actions, but cannot mint XenoSkill points. A missing definition pays nothing.
     *
     * <p>Only skill points are adjusted. Reward actions, experience, items and faction standing are
     * left exactly as declared, because the editor's "Enable reward actions" toggle already decides
     * those at save time by writing an empty {@code commands} list - a policy that stripped or
     * restored them would fight the other toggle. An empty list therefore stays empty here, and this
     * method can never resurrect a command or a point that the quest does not declare.
     */
    public static QuestReward forCompletion(QuestReward declared, boolean xenoNpcGiver) {
        if (declared == null) return NO_REWARD;
        return new QuestReward(xenoNpcGiver ? declared.skillPoints() : 0,
                declared.experience(), declared.items(), declared.commands(),
                declared.factionPoints());
    }
}
