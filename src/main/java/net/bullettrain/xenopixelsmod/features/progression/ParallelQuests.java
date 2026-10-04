package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lightweight Parallel Quest catalog. */
public final class ParallelQuests {
    public static final Map<String, QuestDef> QUESTS = new LinkedHashMap<>();

    static {
        // Rewards stay at the long-standing two skill points unless a quest says otherwise, so
        // nothing a player is part-way through changes value under them. The longer quests pay a
        // little experience on top, which is the first thing rewards were wanted for.
        QUESTS.put("kill_mobs", new QuestDef("kill_mobs", "Hunt", "Defeat 10 hostiles", 10,
                new QuestObjective.Goal(QuestObjective.KILL_MOBS),
                new QuestReward(2, 30, java.util.List.of(), java.util.List.of())));
        QUESTS.put("kill_players", new QuestDef("kill_players", "Rivalry", "Defeat 3 players", 3,
                new QuestObjective.Goal(QuestObjective.KILL_PLAYERS),
                new QuestReward(3, 50, java.util.List.of(), java.util.List.of())));
        QUESTS.put("dummy_session", new QuestDef("dummy_session", "Training",
                "Deal 500 damage to a training dummy", 500,
                new QuestObjective.Goal(QuestObjective.DUMMY_DAMAGE),
                new QuestReward(2, 20, java.util.List.of(), java.util.List.of())));
    }

    private ParallelQuests() {}

    /**
     * One quest.
     *
     * <p>{@code reward} is what it pays on completion. It used to be implicit and identical for
     * every quest - {@code ProgressionEvents.completeQuest} handed out two skill points without
     * knowing which quest had finished - so declaring it here is what lets quests differ.
     */
    public record QuestDef(String id, String title, String desc, int target,
                           QuestObjective.Goal goal, QuestReward reward,
                           String category, String logText, String completeText,
                           QuestCompletionMode completionMode, String completerNpc,
                           QuestRepeat repeat, java.util.List<QuestStep> steps, String nextQuest,
                           boolean randomReward, QuestMail mail, QuestAvailability availability,
                           String completionPalette, String completionFrame, boolean targetHunts) {
        public QuestDef {
            reward = reward == null ? QuestReward.DEFAULT : reward;
            goal = goal == null ? new QuestObjective.Goal(QuestObjective.KILL_MOBS) : goal;
            category = category == null ? "" : category.trim();
            logText = logText == null ? "" : logText;
            completeText = completeText == null ? "" : completeText;
            completerNpc = completerNpc == null ? "" : completerNpc.trim();
            repeat = repeat == null ? QuestRepeat.REPEATABLE : repeat;
            if (steps == null || steps.isEmpty()) {
                steps = java.util.List.of(new QuestStep(goal, Math.max(1, target)));
            } else {
                steps = java.util.List.copyOf(steps);
            }
            goal = steps.get(0).goal();
            target = steps.get(0).target();
            nextQuest = nextQuest == null ? "" : nextQuest.trim().toLowerCase(java.util.Locale.ROOT);
            mail = mail == null ? QuestMail.NONE : mail;
            availability = availability == null ? QuestAvailability.NONE : availability;
            completionPalette = normalizePalette(completionPalette);
            completionFrame = "banner".equalsIgnoreCase(completionFrame) ? "banner" : "rounded";
            // A quest that asks to be handed in but names nobody would sit at full progress
            // forever, waiting for an NPC that was never identified. It keeps the behaviour it
            // would have had without the field at all.
            completionMode = completionMode == QuestCompletionMode.NPC && !completerNpc.isEmpty()
                    ? QuestCompletionMode.NPC : QuestCompletionMode.INSTANT;
        }

        private static String normalizePalette(String value) {
            if (value == null) return "blue";
            return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "gold", "green", "red" -> value.trim().toLowerCase(java.util.Locale.ROOT);
                default -> "blue";
            };
        }

        /** Source-compatible constructor for definitions that predate the target-hunts option. */
        public QuestDef(String id, String title, String desc, int target,
                        QuestObjective.Goal goal, QuestReward reward,
                        String category, String logText, String completeText,
                        QuestCompletionMode completionMode, String completerNpc,
                        QuestRepeat repeat, java.util.List<QuestStep> steps, String nextQuest,
                        boolean randomReward, QuestMail mail, QuestAvailability availability,
                        String completionPalette, String completionFrame) {
            this(id, title, desc, target, goal, reward, category, logText, completeText,
                    completionMode, completerNpc, repeat, steps, nextQuest, randomReward,
                    mail, availability, completionPalette, completionFrame, false);
        }

        /**
         * True when a KILL_NPC step names an NPC and {@link #targetHunts} is set: that NPC hunts the
         * player from quest start until the quest ends ({@code QuestHunts}).
         */
        public java.util.List<String> huntedNpcNames() {
            if (!targetHunts) return java.util.List.of();
            java.util.List<String> out = new java.util.ArrayList<>();
            for (QuestStep step : steps) {
                if (step.goal().type() == QuestObjective.KILL_NPC && !step.goal().parameter().isBlank()) {
                    out.add(step.goal().parameter());
                }
            }
            return out;
        }

        /** Source-compatible constructor for definitions that predate completion card styling. */
        public QuestDef(String id, String title, String desc, int target,
                        QuestObjective.Goal goal, QuestReward reward,
                        String category, String logText, String completeText,
                        QuestCompletionMode completionMode, String completerNpc,
                        QuestRepeat repeat, java.util.List<QuestStep> steps, String nextQuest,
                        boolean randomReward, QuestMail mail, QuestAvailability availability) {
            this(id, title, desc, target, goal, reward, category, logText, completeText,
                    completionMode, completerNpc, repeat, steps, nextQuest, randomReward,
                    mail, availability, "blue", "rounded", false);
        }

        public QuestDef(String id, String title, String desc, int target,
                        QuestObjective.Goal goal, QuestReward reward,
                        String category, String logText, String completeText,
                        QuestCompletionMode completionMode, String completerNpc,
                        QuestRepeat repeat) {
            this(id, title, desc, target, goal, reward, category, logText, completeText,
                    completionMode, completerNpc, repeat, java.util.List.of(), "", false,
                    QuestMail.NONE, QuestAvailability.NONE, "blue", "rounded", false);
        }

        public QuestDef(String id, String title, String desc, int target,
                        QuestObjective.Goal goal, QuestReward reward,
                        String category, String logText, String completeText,
                        QuestCompletionMode completionMode, String completerNpc) {
            this(id, title, desc, target, goal, reward, category, logText, completeText,
                    completionMode, completerNpc, QuestRepeat.REPEATABLE);
        }

        /** A quest that pays the long-standing default and counts any hostile kill. */
        public QuestDef(String id, String title, String desc, int target) {
            this(id, title, desc, target, null, QuestReward.DEFAULT);
        }

        /** A quest with a goal and reward but none of the journal fields. */
        public QuestDef(String id, String title, String desc, int target,
                        QuestObjective.Goal goal, QuestReward reward) {
            this(id, title, desc, target, goal, reward, "", "", "",
                    QuestCompletionMode.INSTANT, "");
        }
    }

    /**
     * Every quest id that can be started, built-ins and pack-defined together.
     *
     * <p>Pack order after the built-ins, so the list a command prints is stable.
     */
    public static java.util.List<String> ids() {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>(QUESTS.keySet());
        ids.addAll(XenoQuests.all().keySet());
        return java.util.List.copyOf(ids);
    }

    /**
     * The definition for {@code questId}, or null when nothing defines it.
     *
     * <p>A pack quest shadows a built-in of the same id, so the three shipped ones are defaults
     * rather than reserved names - a pack that wants {@code kill_mobs} to ask for something else
     * can say so.
     */
    public static QuestDef definition(String questId) {
        if (questId == null || questId.isBlank()) {
            return null;
        }
        String id = questId.trim().toLowerCase(java.util.Locale.ROOT);
        QuestDef fromPack = XenoQuests.get(id);
        return fromPack != null ? fromPack : QUESTS.get(id);
    }

    /**
     * What {@code questId} asks for, or null when nothing defines it.
     *
     * <p>Null rather than a default goal on purpose: the handlers treat it as "not one of ours and
     * therefore not advanced by this event". Returning a default would resurrect the old
     * behaviour where an unrecognised quest silently counted any kill.
     */
    public static QuestObjective.Goal goalFor(String questId) {
        QuestDef def = definition(questId);
        return def == null ? null : def.goal();
    }

    /** Every objective on the quest, or an empty list when the id is unknown. */
    public static java.util.List<QuestStep> stepsFor(String questId) {
        QuestDef def = definition(questId);
        return def == null ? java.util.List.of() : def.steps();
    }

    /** The reward for {@code questId}, or the default when the quest is not one of ours. */
    public static QuestReward rewardFor(String questId) {
        QuestDef def = definition(questId);
        return def == null ? QuestReward.DEFAULT : def.reward();
    }

    public static String start(ServerPlayer player, String questId) {
        return start(player, questId, null);
    }

    public static String start(ServerPlayer player, String questId, net.minecraft.world.entity.Entity giver) {
        if (questId != null && questId.toLowerCase().startsWith("npc:")) {
            return "Take that CustomNPCs quest from the NPC — /xenoquest cannot accept it for you";
        }
        QuestDef def = definition(questId);
        if (def == null) {
            return "Unknown quest. Try: " + String.join(", ", ids())
                    + " or see npc: ids from /xenoquest list";
        }
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) return "No player data";
        if (data.quests().hasCompleted(def.id()) && !def.repeat().canRestart(
                data.quests().completedAt(def.id()), data.quests().completedAtReal(def.id()),
                player.level().getGameTime(), System.currentTimeMillis())) {
            return "That quest cannot be repeated yet";
        }
        String refusal = data.quests().start(def.id(), def.target(), giver);
        if (refusal != null) {
            return refusal;
        }
        player.displayClientMessage(Component.literal(
                "§bQuest started: §f" + def.title() + " §7— " + def.desc()), false);
        // Pushed here rather than left to the next tick: the New Quest toast fires off this
        // packet, and a toast that arrives seconds after the dialogue closed has lost its moment.
        QuestSync.push(player);
        QuestHunts.onQuestStarted(player, def);
        return null;
    }

    /**
     * Every active quest, one per line.
     *
     * <p>Used to describe "the" active quest, which only made sense while a player could hold one.
     * A quest waiting to be handed in says so, because otherwise a full progress bar that has not
     * paid out looks like a bug rather than a step.
     */
    public static String status(ServerPlayer player) {
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) return "No player data";
        java.util.List<ActiveQuest> actives = data.quests().actives();
        if (actives.isEmpty()) return "No active quest. /xenoquest start <id>";

        StringBuilder out = new StringBuilder();
        for (ActiveQuest quest : actives) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(quest.id()).append(": ")
                    .append(quest.progress()).append('/').append(quest.target());
            if (quest.ready()) {
                QuestDef def = definition(quest.id());
                out.append(" - ready, complete with ")
                        .append(def == null || def.completerNpc().isEmpty()
                                ? "an NPC" : def.completerNpc());
            }
        }
        return out.toString();
    }
}
