package net.bullettrain.xenopixelsmod.npc.script.api;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.features.progression.ActiveQuest;
import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.minecraft.server.level.ServerPlayer;

/** Narrow counterpart for the MyNPCs quest lookup used by the bundled examples. */
public final class ScriptApi {
    public Quests getQuests() { return new Quests(); }

    /** The quest store entry for a CustomNPCs / My NPCs quest number; see {@code XenoScriptIds}. */
    public static String questId(int sourceSlot) {
        return net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoScriptIds.questId(sourceSlot);
    }

    public static final class Quests {
        public Quest get(int sourceSlot) {
            String id = questId(sourceSlot);
            return id == null || ParallelQuests.definition(id) == null ? null : new Quest(id);
        }
    }

    public record Quest(String id) {
        public Objective[] getObjectives(ScriptPlayer player) {
            if (player == null) return new Objective[0];
            ParallelQuests.QuestDef def = ParallelQuests.definition(id);
            if (def == null) return new Objective[0];
            ActiveQuest active = XenoCapabilities.get((ServerPlayer) player.unwrap())
                    .map(data -> data.quests().active(id)).orElse(null);
            Objective[] out = new Objective[def.steps().size()];
            for (int i = 0; i < out.length; i++) {
                int index = i;
                boolean complete = active != null && active.stepProgress(index) >= def.steps().get(index).target();
                out[i] = new Objective(complete);
            }
            return out;
        }
    }

    public record Objective(boolean completed) {
        public boolean isCompleted() { return completed; }
    }
}
