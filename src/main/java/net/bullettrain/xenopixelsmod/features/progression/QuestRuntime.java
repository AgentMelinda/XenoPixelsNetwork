package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.List;
import java.util.Locale;

/** Applies one event to every objective on the player's active quests. */
public final class QuestRuntime {

    private QuestRuntime() {}

    public static boolean onKill(ServerPlayer player, XenoPlayerData data, ActiveQuest quest,
                                 Entity victim, boolean trainingDummy) {
        return advance(quest, (step, index) -> {
            if (step.goal().type() == QuestObjective.AREA_KILL) {
                if (!inside(player, step)) return 0;
                if (!step.goal().parameter().isBlank()
                        && !step.goal().countsKill(victim, trainingDummy)
                        && !matchesArea(step, victim, trainingDummy)) {
                    return 0;
                }
                if (step.goal().parameter().isBlank()) {
                    return trainingDummy || victim instanceof net.minecraft.world.entity.player.Player
                            ? 0 : 1;
                }
                return matchesArea(step, victim, trainingDummy) ? 1 : 0;
            }
            return step.goal().countsKill(victim, trainingDummy) ? 1 : 0;
        });
    }

    public static boolean onTalk(ActiveQuest quest, String npcName, String roleId, String npcId) {
        return advance(quest, (step, index) -> {
            if (!step.goal().countsTalk(npcName, roleId)) return 0;
            String key = "talk:" + index + ":" + (npcId == null ? "" : npcId);
            return quest.markVisited(key) ? 1 : 0;
        });
    }

    public static boolean onDialog(ActiveQuest quest, String optionId) {
        String wanted = optionId == null ? "" : optionId.trim().toLowerCase(Locale.ROOT);
        return advance(quest, (step, index) ->
                step.goal().type() == QuestObjective.DIALOG
                        && (step.goal().parameter().isEmpty() || step.goal().parameter().equals(wanted))
                        ? 1 : 0);
    }

    public static boolean onManual(ActiveQuest quest, int amount) {
        return advance(quest, (step, index) ->
                step.goal().type() == QuestObjective.MANUAL ? Math.max(0, amount) : 0);
    }

    public static boolean onPresence(ServerPlayer player, ActiveQuest quest) {
        return advance(quest, (step, index) -> switch (step.goal().type()) {
            case ITEM -> hasItems(player, step) ? step.target() - quest.stepProgress(index) : 0;
            case LOCATION -> inside(player, step) ? 1 : 0;
            default -> 0;
        });
    }

    public static void takeItems(ServerPlayer player, ParallelQuests.QuestDef def) {
        if (player == null || def == null) return;
        for (QuestStep step : def.steps()) {
            if (step.goal().type() != QuestObjective.ITEM || !step.takeItems()) continue;
            removeItems(player, step);
        }
    }

    public static boolean available(ServerPlayer player, XenoPlayerData data, QuestAvailability gate) {
        if (gate == null) return true;
        for (QuestAvailability.DialogGate dialog : gate.dialogs()) {
            if (dialog.state() == QuestAvailability.DialogState.ALWAYS) continue;
            if (data == null || dialog.dialogId().isEmpty()) return false;
            boolean viewed = data.hasViewedDialogue(dialog.dialogId());
            if (dialog.state() == QuestAvailability.DialogState.AFTER && !viewed) return false;
            if (dialog.state() == QuestAvailability.DialogState.BEFORE && viewed) return false;
        }
        if (player == null) return true;
        if (player.experienceLevel < gate.minLevel()) return false;
        long time = player.level().getDayTime() % 24000L;
        boolean night = time >= 13000L && time < 23000L;
        if ("night".equals(gate.daytime()) && !night) return false;
        if ("day".equals(gate.daytime()) && night) return false;
        if (data != null) {
            for (QuestAvailability.QuestGate quest : gate.quests()) {
                if (!questGate(data, quest)) return false;
            }
        }
        for (QuestAvailability.FactionGate faction : gate.factions()) {
            int standing = data == null ? 0 : data.getFactionStanding(faction.factionId());
            boolean isStance = stance(standing) == faction.stance();
            if (isStance != faction.matches()) return false;
        }
        Scoreboard board = player.getScoreboard();
        for (QuestAvailability.ScoreGate score : gate.scores()) {
            Objective objective = board.getObjective(score.objective());
            if (objective == null) return false;
            int value = board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.getScoreboardName()),
                    objective).get();
            boolean ok = switch (score.compare()) {
                case SMALLER -> value < score.value();
                case EQUAL -> value == score.value();
                case BIGGER -> value > score.value();
            };
            if (!ok) return false;
        }
        return true;
    }

    private static boolean questGate(XenoPlayerData data, QuestAvailability.QuestGate gate) {
        if (gate.state() == QuestAvailability.QuestState.ALWAYS || gate.questId().isEmpty()) {
            return true;
        }
        boolean active = data.quests().actives().stream().anyMatch(q -> q.id().equals(gate.questId()));
        boolean done = data.quests().hasCompleted(gate.questId());
        return switch (gate.state()) {
            case ALWAYS -> true;
            case AFTER, COMPLETED -> done;
            case BEFORE, CAN_START -> !done && !active;
            case ACTIVE -> active;
            case NOT_ACTIVE -> !active;
        };
    }

    private static QuestAvailability.Stance stance(int points) {
        if (points >= 500) return QuestAvailability.Stance.FRIENDLY;
        if (points <= -500) return QuestAvailability.Stance.HOSTILE;
        return QuestAvailability.Stance.NEUTRAL;
    }

    private static boolean inside(ServerPlayer player, QuestStep step) {
        if (!step.dimension().isEmpty()
                && !step.dimension().equals(player.level().dimension().location().toString())) {
            return false;
        }
        double limit = step.radius() > 0 ? step.radius() : 4.0;
        double dx = player.getX() - step.x();
        double dy = player.getY() - step.y();
        double dz = player.getZ() - step.z();
        return dx * dx + dy * dy + dz * dz <= limit * limit;
    }

    private static boolean matchesArea(QuestStep step, Entity victim, boolean trainingDummy) {
        QuestObjective.Goal goal = new QuestObjective.Goal(
                step.goal().parameter().contains("player") ? QuestObjective.KILL_PLAYERS
                        : step.goal().parameter().isBlank() ? QuestObjective.KILL_MOBS
                        : QuestObjective.KILL_TYPE,
                step.goal().parameter());
        return goal.countsKill(victim, trainingDummy);
    }

    private static boolean hasItems(ServerPlayer player, QuestStep step) {
        Item item = QuestReward.resolveItem(step.goal().parameter());
        if (item == null) return false;
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (matches(stack, item, step)) found += stack.getCount();
        }
        return found >= step.target();
    }

    private static void removeItems(ServerPlayer player, QuestStep step) {
        Item item = QuestReward.resolveItem(step.goal().parameter());
        if (item == null) return;
        int left = step.target();
        for (int slot = 0; slot < player.getInventory().items.size() && left > 0; slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (!matches(stack, item, step)) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
        }
    }

    private static boolean matches(ItemStack stack, Item item, QuestStep step) {
        if (stack.isEmpty() || stack.getItem() != item) return false;
        if (!step.ignoreDamage() && stack.isDamageableItem() && stack.getDamageValue() > 0) return false;
        if (!step.ignoreNbt()
                && !stack.getComponents().equals(item.getDefaultInstance().getComponents())) {
            return false;
        }
        return ResourceLocation.tryParse(step.goal().parameter()) != null;
    }

    private static boolean advance(ActiveQuest quest, StepAmount amount) {
        List<QuestStep> steps = ParallelQuests.stepsFor(quest.id());
        if (steps.isEmpty() || quest.ready()) return false;
        int[] required = new int[steps.size()];
        boolean changed = false;
        for (int i = 0; i < steps.size(); i++) {
            required[i] = steps.get(i).target();
            int give = amount.amount(steps.get(i), i);
            if (give > 0 && quest.stepProgress(i) < required[i]) {
                changed = true;
                if (quest.addStepProgress(i, give, required)) {
                    return true;
                }
            }
        }
        return changed;
    }

    @FunctionalInterface
    private interface StepAmount {
        int amount(QuestStep step, int index);
    }
}
