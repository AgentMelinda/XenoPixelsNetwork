package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import xenoapi.npcs.api.handler.IQuestHandler;
import xenoapi.npcs.api.handler.data.IQuest;
import xenoapi.npcs.api.handler.data.IQuestCategory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Every native quest (store, datapack and built-in) as XenoAPI's {@link IQuestHandler}. */
public final class XenoQuestHandler implements IQuestHandler {
    @Override
    public List<IQuestCategory> categories() {
        Set<String> names = new LinkedHashSet<>();
        for (String id : ParallelQuests.ids()) {
            ParallelQuests.QuestDef def = ParallelQuests.definition(id);
            if (def != null) names.add(def.category());
        }
        List<IQuestCategory> out = new ArrayList<>();
        for (String name : names) out.add(new XenoQuestCategory(name));
        return out;
    }

    /** The quest carrying this CustomNPCs number, or null. */
    @Override
    public IQuest get(int id) {
        String quest = XenoScriptIds.questId(id);
        return quest == null || ParallelQuests.definition(quest) == null ? null : new XenoQuestAdapter(quest);
    }

    /** A quest by its native id, for Xeno-made quests that have no number. */
    public IQuest get(String id) {
        ParallelQuests.QuestDef def = ParallelQuests.definition(id);
        return def == null ? null : new XenoQuestAdapter(def.id());
    }
}
