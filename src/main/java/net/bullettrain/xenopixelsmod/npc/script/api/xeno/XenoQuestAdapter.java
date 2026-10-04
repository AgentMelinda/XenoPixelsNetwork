package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.bullettrain.xenopixelsmod.features.progression.QuestCompletionMode;
import net.bullettrain.xenopixelsmod.features.progression.QuestObjective;
import net.bullettrain.xenopixelsmod.features.progression.QuestRepeat;
import net.bullettrain.xenopixelsmod.features.progression.QuestReward;
import net.bullettrain.xenopixelsmod.features.progression.QuestStep;
import net.bullettrain.xenopixelsmod.features.progression.XenoQuests;
import net.bullettrain.xenopixelsmod.npc.importer.QuestImport;
import net.bullettrain.xenopixelsmod.npc.importer.SlotIndex;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.constants.QuestType;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.handler.data.IQuest;
import xenoapi.npcs.api.handler.data.IQuestCategory;
import xenoapi.npcs.api.handler.data.IQuestObjective;
import xenoapi.npcs.api.item.IItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A native quest definition as XenoAPI's {@link IQuest}. Setters change this view; {@link #save()}
 * writes it to the world store, which shadows a datapack or built-in quest of the same id.
 * {@link #getId()} is the CustomNPCs number an import recorded, or {@link XenoScriptIds#NONE}.
 */
public final class XenoQuestAdapter implements IQuest {
    static final int MAX_TEXT = 4096;
    private final String id;
    private ParallelQuests.QuestDef pending;
    /** Where save() writes a quest the store does not hold yet; null means "its category". */
    private final String newGroup;

    XenoQuestAdapter(String id) {
        this(id, null, null);
    }

    XenoQuestAdapter(String id, ParallelQuests.QuestDef draft, String newGroup) {
        this.id = id;
        this.pending = draft;
        this.newGroup = newGroup;
    }

    /** The native quest id behind this view. */
    public String nativeId() { return id; }

    ParallelQuests.QuestDef def() {
        if (pending != null) return pending;
        ParallelQuests.QuestDef live = ParallelQuests.definition(id);
        if (live == null) throw new CustomNPCsException("Quest %s no longer exists", id);
        return live;
    }

    /** Rebuilds the definition with the given parts; every other field is kept. */
    private static ParallelQuests.QuestDef copy(ParallelQuests.QuestDef d, String title, String logText,
                                                String completeText, String completer, String nextQuest,
                                                QuestReward reward, List<QuestStep> steps) {
        QuestCompletionMode mode = completer.isBlank() ? QuestCompletionMode.INSTANT : QuestCompletionMode.NPC;
        return new ParallelQuests.QuestDef(d.id(), title, d.desc(), steps.get(0).target(), steps.get(0).goal(),
                reward, d.category(), logText, completeText, mode, completer, d.repeat(), steps, nextQuest,
                d.randomReward(), d.mail(), d.availability(), d.completionPalette(), d.completionFrame(),
                d.targetHunts());
    }

    private void change(String title, String logText, String completeText, String completer, String next,
                        QuestReward reward, List<QuestStep> steps) {
        pending = copy(def(), title, logText, completeText, completer, next, reward, steps);
    }

    private ParallelQuests.QuestDef d() { return def(); }

    private static String text(String method, String value) {
        return XenoApiAdapters.boundedText(method, value, MAX_TEXT);
    }

    @Override public int getId() { return XenoScriptIds.questSlot(id); }
    @Override public String getName() { return d().title(); }

    @Override
    public void setName(String name) {
        var d = d();
        change(text("IQuest.setName", name), d.logText(), d.completeText(), d.completerNpc(), d.nextQuest(), d.reward(), d.steps());
    }

    /** The first objective's kind as a CustomNPCs {@link QuestType}; kinds it has none for read as MANUAL. */
    @Override
    public int getType() {
        return switch (d().goal().type()) {
            case ITEM -> QuestType.ITEM;
            case DIALOG -> QuestType.DIALOG;
            case KILL_MOBS, KILL_PLAYERS, KILL_TYPE, KILL_NPC -> QuestType.KILL;
            case LOCATION -> QuestType.LOCATION;
            case AREA_KILL -> QuestType.AREA_KILL;
            default -> QuestType.MANUAL;
        };
    }

    /** Replaces every objective with one of the new kind, as CustomNPCs resets them on a type change. */
    @Override
    public void setType(int type) {
        QuestObjective objective = switch (type) {
            case QuestType.ITEM -> QuestObjective.ITEM;
            case QuestType.DIALOG -> QuestObjective.DIALOG;
            case QuestType.KILL -> QuestObjective.KILL_TYPE;
            case QuestType.LOCATION -> QuestObjective.LOCATION;
            case QuestType.AREA_KILL -> QuestObjective.AREA_KILL;
            case QuestType.MANUAL -> QuestObjective.MANUAL;
            default -> throw new IllegalArgumentException("IQuest.setType: type must be 0-5");
        };
        var d = d();
        change(d.title(), d.logText(), d.completeText(), d.completerNpc(), d.nextQuest(), d.reward(),
                List.of(new QuestStep(new QuestObjective.Goal(objective, ""), 1)));
    }

    @Override public String getLogText() { return d().logText(); }

    @Override
    public void setLogText(String text) {
        var d = d();
        change(d.title(), text("IQuest.setLogText", text), d.completeText(), d.completerNpc(), d.nextQuest(), d.reward(), d.steps());
    }

    @Override public String getCompleteText() { return d().completeText(); }

    @Override
    public void setCompleteText(String text) {
        var d = d();
        change(d.title(), d.logText(), text("IQuest.setCompleteText", text), d.completerNpc(), d.nextQuest(), d.reward(), d.steps());
    }

    @Override
    public IQuest getNextQuest() {
        String next = d().nextQuest();
        return next.isEmpty() || ParallelQuests.definition(next) == null ? null : new XenoQuestAdapter(next);
    }

    @Override
    public void setNextQuest(IQuest quest) {
        String next = quest == null ? "" : nativeId(quest);
        var d = d();
        change(d.title(), d.logText(), d.completeText(), d.completerNpc(), next, d.reward(), d.steps());
    }

    static String nativeId(IQuest quest) {
        if (quest instanceof XenoQuestAdapter adapter) return adapter.id;
        throw new IllegalArgumentException("Foreign XenoAPI quest: " + quest.getClass().getName());
    }

    @Override
    public IQuestObjective[] getObjectives(IPlayer player) {
        if (!(XenoApiAdapters.unwrap(player) instanceof ServerPlayer target)) {
            throw new IllegalArgumentException("IQuest.getObjectives: player cannot be null");
        }
        List<QuestStep> steps = d().steps();
        IQuestObjective[] out = new IQuestObjective[steps.size()];
        for (int i = 0; i < out.length; i++) out[i] = new XenoQuestObjective(target, id, i);
        return out;
    }

    @Override public IQuestCategory getCategory() { return new XenoQuestCategory(d().category()); }

    /** Reward items; a reward id that no longer resolves is left out. */
    @Override
    public IItemStack[] getRewards() {
        List<IItemStack> out = new ArrayList<>();
        for (QuestReward.ItemGrant grant : d().reward().items()) {
            Item item = QuestReward.resolveItem(grant.id());
            if (item != null) out.add(XenoApiAdapters.wrap(new ItemStack(item, grant.count())));
        }
        return out.toArray(IItemStack[]::new);
    }

    /** Item and count only: native quest rewards are item ids, so components on a stack are not kept. */
    @Override
    public void setRewards(IItemStack[] items) {
        List<QuestReward.ItemGrant> grants = new ArrayList<>();
        if (items != null) {
            for (IItemStack item : items) {
                ItemStack stack = XenoApiAdapters.unwrap(item);
                if (stack.isEmpty()) continue;
                grants.add(new QuestReward.ItemGrant(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
            }
        }
        var d = d();
        QuestReward r = d.reward();
        change(d.title(), d.logText(), d.completeText(), d.completerNpc(), d.nextQuest(),
                new QuestReward(r.skillPoints(), r.experience(), grants, r.commands(), r.factionPoints()), d.steps());
    }

    @Override public String getNpcName() { return d().completerNpc(); }

    /** The NPC that completes it; a name makes it a hand-in quest, blank completes on the spot. */
    @Override
    public void setNpcName(String name) {
        var d = d();
        change(d.title(), d.logText(), d.completeText(), XenoApiAdapters.boundedText("IQuest.setNpcName", name, 64),
                d.nextQuest(), d.reward(), d.steps());
    }

    @Override public boolean getIsRepeatable() { return d().repeat() != QuestRepeat.NONE; }
    @Override public String[] getCommands() { return d().reward().commands().toArray(String[]::new); }

    /** Completion commands, run as the server with {@code {player}} replaced, as native rewards run. */
    @Override
    public void setCommands(String... commands) {
        List<String> list = new ArrayList<>();
        if (commands != null) {
            if (commands.length > 16) throw new IllegalArgumentException("IQuest.setCommands: at most 16 commands");
            for (String command : commands) {
                String value = XenoApiAdapters.boundedText("IQuest.setCommands", command, XenoApiAdapters.MAX_COMMAND);
                if (!value.isEmpty()) list.add(value);
            }
        }
        var d = d();
        QuestReward r = d.reward();
        change(d.title(), d.logText(), d.completeText(), d.completerNpc(), d.nextQuest(),
                new QuestReward(r.skillPoints(), r.experience(), r.items(), list, r.factionPoints()), d.steps());
    }

    /** The store group this quest lives in, or where a new one goes. */
    private String group(XenoNpcWorldStore store) {
        for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.QUESTS)) {
            if (entry.id().equals(id)) return entry.group();
        }
        String category = newGroup != null ? newGroup : d().category();
        return category.isBlank() ? "scripted" : new SlotIndex().assign(0, category);
    }

    /** Validates the definition as the editor's save does, then writes it to the world store. */
    @Override
    public void save() {
        XenoApiAdapters.requireServerThreadNow();
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) throw new IllegalStateException("IQuest.save needs a loaded world");
        ParallelQuests.QuestDef def = def();
        String json = QuestImport.toJson(def);
        try {
            XenoQuests.parse(id, com.google.gson.JsonParser.parseString(json));
        } catch (RuntimeException invalid) {
            throw new CustomNPCsException("IQuest.save: %s", invalid.getMessage());
        }
        String group = group(store);
        CompoundTag previous = store.get(XenoNpcStoreCategory.QUESTS, group, id);
        CompoundTag tag = previous == null ? new CompoundTag() : previous.copy();
        tag.putString("Name", def.title());
        tag.putString("DefinitionJson", json);
        String refusal = store.put(XenoNpcStoreCategory.QUESTS, group, id, tag);
        if (refusal != null) throw new CustomNPCsException("IQuest.save: %s", refusal);
        pending = null;
        if (net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() != null) {
            net.bullettrain.xenopixelsmod.network.ModNetwork.sendToAll(
                    net.bullettrain.xenopixelsmod.network.packet.SyncNpcStoreIndexPacket.current());
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof XenoQuestAdapter quest && quest.id.equals(id);
    }

    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return id; }
}
