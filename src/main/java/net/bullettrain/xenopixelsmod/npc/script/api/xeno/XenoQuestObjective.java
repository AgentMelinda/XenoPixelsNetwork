package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.features.progression.ActiveQuest;
import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.bullettrain.xenopixelsmod.features.progression.ProgressionEvents;
import net.bullettrain.xenopixelsmod.features.progression.QuestStep;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import xenoapi.npcs.api.handler.data.IQuestObjective;

import java.util.List;
import java.util.Locale;

/**
 * One objective of one player's quest. Progress is the player's live step progress; a finished
 * quest reads as complete, one not taken as zero.
 */
final class XenoQuestObjective implements IQuestObjective {
    private final ServerPlayer player;
    private final String questId;
    private final int index;

    XenoQuestObjective(ServerPlayer player, String questId, int index) {
        this.player = player;
        this.questId = questId;
        this.index = index;
    }

    private QuestStep step() {
        List<QuestStep> steps = ParallelQuests.stepsFor(questId);
        return index < steps.size() ? steps.get(index) : null;
    }

    private ActiveQuest active() {
        return XenoCapabilities.get(player).map(d -> d.quests().active(questId)).orElse(null);
    }

    @Override
    public int getProgress() {
        ActiveQuest active = active();
        if (active != null) return active.stepProgress(index);
        boolean done = XenoCapabilities.get(player).map(d -> d.quests().hasCompleted(questId)).orElse(false);
        return done ? getMaxProgress() : 0;
    }

    /** Only while the quest is active; reaching every target finishes it as native progress would. */
    @Override
    public void setProgress(int progress) {
        XenoApiAdapters.requireServerThread(player.level());
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        ActiveQuest active = data == null ? null : data.quests().active(questId);
        if (active == null) throw new IllegalStateException("IQuestObjective.setProgress: the player is not on " + questId);
        active.setStepProgress(index, progress, getMaxProgress());
        ProgressionEvents.finishIfDone(player, data, active);
    }

    @Override
    public int getMaxProgress() {
        QuestStep step = step();
        return step == null ? 1 : step.target();
    }

    @Override public boolean isCompleted() { return getProgress() >= getMaxProgress(); }

    @Override
    public String getText() {
        QuestStep step = step();
        if (step == null) return "";
        String kind = step.goal().type().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String what = step.goal().parameter().isBlank() ? kind : kind + " " + step.goal().parameter();
        return what + ": " + getProgress() + "/" + getMaxProgress();
    }

    @Override public Component getMCText() { return Component.literal(getText()); }
}
