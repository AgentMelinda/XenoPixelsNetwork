package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.features.progression.QuestAvailability;
import net.bullettrain.xenopixelsmod.features.progression.QuestRuntime;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.minecraft.server.level.ServerPlayer;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.handler.data.IAvailability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The conditions on one native dialog node, as XenoAPI's {@link IAvailability}.
 *
 * <p>Every getter reads the node its {@link XenoDialogAdapter} is opened at, and every setter writes
 * that node, so the change lands in the draft a script commits with {@code IDialog.save()} - the same
 * path the editor uses, and invisible to players until then.
 *
 * <p>Script ids are CustomNPCs numbers; native content is keyed by strings, so each id crosses
 * through {@link XenoScriptIds} in both directions. A number that nothing carries, or that two
 * entries claim, is refused rather than guessed at: an availability naming the wrong quest would hide
 * the dialog behind a condition nobody set.
 *
 * <p>Slots are positional. Setting slot 3 on an empty list leaves 0-2 as no-condition rather than
 * sliding the condition down, because {@code getQuest(i)} documents {@code i} as the slot, and
 * {@code remove*(i)} empties that slot instead of shortening the list. A no-condition slot reads back
 * as {@code -1}. Faction and score gates carry no "always" state of their own, so an emptied one is
 * stored as a blank id, which {@link QuestRuntime} reads as no condition.
 */
final class XenoNodeAvailability implements IAvailability {
    private static final int MAX_QUESTS = 4;
    private static final int MAX_DIALOGS = 4;
    private static final int MAX_FACTIONS = 2;
    private static final int MAX_SCORES = 2;

    private final XenoDialogAdapter owner;

    XenoNodeAvailability(XenoDialogAdapter owner) {
        this.owner = owner;
    }

    private QuestAvailability gate() {
        return owner.current().availability();
    }

    private void write(List<QuestAvailability.QuestGate> quests, List<QuestAvailability.DialogGate> dialogs,
                       String daytime, List<QuestAvailability.FactionGate> factions,
                       List<QuestAvailability.ScoreGate> scores, int minLevel) {
        XenoDialogue.Node node = owner.current();
        owner.replace(new XenoDialogue.Node(node.text(), node.options(), node.palette(),
                new QuestAvailability(quests, dialogs, daytime, factions, scores, minLevel), node.sourceRef()));
    }

    /** A copy of {@code current} with {@code value} at {@code index}, empty slots before it filled in. */
    private static <T> List<T> put(List<T> current, int index, int max, String what, T value, Supplier<T> filler) {
        slot(what, index, max);
        List<T> out = new ArrayList<>(current);
        while (out.size() <= index) {
            out.add(filler.get());
        }
        out.set(index, value);
        return out;
    }

    private static <T> T at(List<T> list, int index, int max, String what) {
        slot(what, index, max);
        return index < list.size() ? list.get(index) : null;
    }

    private static void slot(String what, int index, int max) {
        if (index < 0 || index >= max) {
            throw new IllegalArgumentException("IAvailability." + what + ": index must be 0-" + (max - 1));
        }
    }

    @Override
    public boolean isAvailable(IPlayer player) {
        if (!(XenoApiAdapters.unwrap(player) instanceof ServerPlayer target)) {
            throw new IllegalArgumentException("IAvailability.isAvailable: player cannot be null");
        }
        return QuestRuntime.available(target, XenoCapabilities.get(target).orElse(null), gate());
    }

    @Override
    public int getDaytime() {
        return switch (gate().daytime()) {
            case "night" -> 1;
            case "day" -> 2;
            default -> 0;
        };
    }

    @Override
    public void setDaytime(int type) {
        if (type < 0 || type > 2) throw new IllegalArgumentException("IAvailability.setDaytime: type must be 0-2");
        var g = gate();
        write(g.quests(), g.dialogs(), type == 1 ? "night" : type == 2 ? "day" : "always",
                g.factions(), g.scores(), g.minLevel());
    }

    @Override
    public int getMinPlayerLevel() {
        return gate().minLevel();
    }

    @Override
    public void setMinPlayerLevel(int level) {
        if (level < 0) throw new IllegalArgumentException("IAvailability.setMinPlayerLevel: level cannot be negative");
        var g = gate();
        write(g.quests(), g.dialogs(), g.daytime(), g.factions(), g.scores(), level);
    }

    @Override
    public int getDialog(int i) {
        QuestAvailability.DialogGate at = at(gate().dialogs(), i, MAX_DIALOGS, "getDialog");
        return at == null || at.state() == QuestAvailability.DialogState.ALWAYS
                ? XenoScriptIds.NONE : dialogSlot(at.dialogId());
    }

    /** type: 0 always, 1 after the dialog was seen, 2 before it was seen. */
    @Override
    public void setDialog(int i, int id, int type) {
        ordinal("setDialog", type, 2);
        QuestAvailability.DialogState state = QuestAvailability.DialogState.values()[type];
        // An always-slot needs no reference, so it is the one case that must not demand a number.
        String ref = state == QuestAvailability.DialogState.ALWAYS ? "" : requireDialog(id);
        var g = gate();
        write(g.quests(), put(g.dialogs(), i, MAX_DIALOGS, "setDialog",
                new QuestAvailability.DialogGate(ref, state),
                () -> new QuestAvailability.DialogGate("", QuestAvailability.DialogState.ALWAYS)),
                g.daytime(), g.factions(), g.scores(), g.minLevel());
    }

    @Override
    public void removeDialog(int i) {
        setDialog(i, XenoScriptIds.NONE, 0);
    }

    @Override
    public int getQuest(int i) {
        QuestAvailability.QuestGate at = at(gate().quests(), i, MAX_QUESTS, "getQuest");
        return at == null || at.state() == QuestAvailability.QuestState.ALWAYS
                ? XenoScriptIds.NONE : XenoScriptIds.questSlot(at.questId());
    }

    /** type: 0 always, 1 after, 2 before, 3 active, 4 not active, 5 completed. */
    @Override
    public void setQuest(int i, int id, int type) {
        ordinal("setQuest", type, 5);
        QuestAvailability.QuestState state = QuestAvailability.QuestState.values()[type];
        String questId = state == QuestAvailability.QuestState.ALWAYS ? "" : requireQuest(id);
        var g = gate();
        write(put(g.quests(), i, MAX_QUESTS, "setQuest",
                new QuestAvailability.QuestGate(questId, state),
                () -> new QuestAvailability.QuestGate("", QuestAvailability.QuestState.ALWAYS)),
                g.dialogs(), g.daytime(), g.factions(), g.scores(), g.minLevel());
    }

    @Override
    public void removeQuest(int i) {
        setQuest(i, XenoScriptIds.NONE, 0);
    }

    /** type: 0 always, 1 the player holds that stance, 2 they do not. stance: 0 friendly, 1 neutral, 2 hostile. */
    @Override
    public void setFaction(int i, int id, int type, int stance) {
        ordinal("setFaction", type, 2);
        ordinal("setFaction stance", stance, 2);
        QuestAvailability.Stance st = QuestAvailability.Stance.values()[stance];
        String factionId = type == 0 ? "" : requireFaction(id);
        var g = gate();
        write(g.quests(), g.dialogs(), g.daytime(),
                put(g.factions(), i, MAX_FACTIONS, "setFaction",
                        new QuestAvailability.FactionGate(factionId, st, type == 1),
                        () -> new QuestAvailability.FactionGate("", QuestAvailability.Stance.NEUTRAL, true)),
                g.scores(), g.minLevel());
    }

    @Override
    public void removeFaction(int i) {
        setFaction(i, XenoScriptIds.NONE, 0, 1);
    }

    /** type: 0 the score is smaller, 1 equal, 2 bigger. */
    @Override
    public void setScoreboard(int i, String objective, int type, int value) {
        ordinal("setScoreboard", type, 2);
        if (objective == null || objective.isBlank()) {
            throw new IllegalArgumentException("IAvailability.setScoreboard: objective cannot be blank");
        }
        String name = XenoApiAdapters.boundedText("IAvailability.setScoreboard", objective, 128);
        var g = gate();
        write(g.quests(), g.dialogs(), g.daytime(), g.factions(),
                put(g.scores(), i, MAX_SCORES, "setScoreboard",
                        new QuestAvailability.ScoreGate(name, QuestAvailability.Compare.values()[type], value),
                        () -> new QuestAvailability.ScoreGate("", QuestAvailability.Compare.EQUAL, 0)),
                g.minLevel());
    }

    private static void ordinal(String what, int value, int max) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("IAvailability." + what + ": value must be 0-" + max);
        }
    }

    private static String requireDialog(int slot) {
        XenoScriptIds.Ref ref = XenoScriptIds.dialog(slot);
        if (ref == null) {
            throw new CustomNPCsException("no dialog carries CustomNPCs number %s, or two of them do", slot);
        }
        return ref.group() + "/" + ref.id();
    }

    /** The CustomNPCs number of a stored {@code group/id} reference, or {@link XenoScriptIds#NONE}. */
    private static int dialogSlot(String ref) {
        int split = ref.indexOf('/');
        return split <= 0 ? XenoScriptIds.NONE
                : XenoScriptIds.slotOf(XenoNpcStoreCategory.DIALOGS, ref.substring(0, split), ref.substring(split + 1));
    }

    private static String requireQuest(int slot) {
        String id = XenoScriptIds.questId(slot);
        if (id == null) {
            throw new CustomNPCsException("no quest carries CustomNPCs number %s, or two of them do", slot);
        }
        return id;
    }

    private static String requireFaction(int slot) {
        String id = XenoScriptIds.factionId(slot);
        if (id == null) {
            throw new CustomNPCsException("no faction carries CustomNPCs number %s, or two of them do", slot);
        }
        return id;
    }
}
