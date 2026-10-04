package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.bullettrain.xenopixelsmod.features.progression.QuestObjective;
import net.bullettrain.xenopixelsmod.features.progression.QuestReward;
import net.bullettrain.xenopixelsmod.npc.importer.SlotIndex;
import xenoapi.npcs.api.handler.data.IQuest;
import xenoapi.npcs.api.handler.data.IQuestCategory;

import java.util.ArrayList;
import java.util.List;

/** Native quests share a category by their {@code category} field; imports use their source folder. */
final class XenoQuestCategory implements IQuestCategory {
    private final String name;

    XenoQuestCategory(String name) {
        this.name = name == null ? "" : name;
    }

    @Override
    public List<IQuest> quests() {
        List<IQuest> out = new ArrayList<>();
        for (String id : ParallelQuests.ids()) {
            ParallelQuests.QuestDef def = ParallelQuests.definition(id);
            if (def != null && def.category().equals(name)) out.add(new XenoQuestAdapter(def.id()));
        }
        return out;
    }

    @Override public String getName() { return name; }

    /** Renaming a category rewrites the category of every quest in it; each is then saved. */
    @Override
    public void setName(String next) {
        String value = XenoApiAdapters.boundedText("IQuestCategory.setName", next, 64);
        for (IQuest quest : quests()) {
            XenoQuestAdapter adapter = (XenoQuestAdapter) quest;
            ParallelQuests.QuestDef d = adapter.def();
            XenoQuestAdapter renamed = new XenoQuestAdapter(d.id(), new ParallelQuests.QuestDef(d.id(), d.title(),
                    d.desc(), d.target(), d.goal(), d.reward(), value, d.logText(), d.completeText(),
                    d.completionMode(), d.completerNpc(), d.repeat(), d.steps(), d.nextQuest(), d.randomReward(),
                    d.mail(), d.availability(), d.completionPalette(), d.completionFrame(), d.targetHunts()), null);
            renamed.save();
        }
    }

    /**
     * A new, unsaved manual quest in this category. It has an id but no CustomNPCs number; call
     * {@code save()} to keep it.
     */
    @Override
    public IQuest create() {
        String group = name.isBlank() ? "scripted" : new SlotIndex().assign(0, name);
        String base = group + "_quest";
        String id = base;
        for (int n = 2; ParallelQuests.definition(id) != null; n++) id = base + "_" + n;
        ParallelQuests.QuestDef draft = new ParallelQuests.QuestDef(id, "New Quest", "", 1,
                new QuestObjective.Goal(QuestObjective.MANUAL, ""), QuestReward.DEFAULT, name, "", "",
                net.bullettrain.xenopixelsmod.features.progression.QuestCompletionMode.INSTANT, "");
        return new XenoQuestAdapter(id, draft, group);
    }
}
