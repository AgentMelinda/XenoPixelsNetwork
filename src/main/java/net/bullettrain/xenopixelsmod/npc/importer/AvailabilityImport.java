package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.features.progression.QuestAvailability;
import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;

/**
 * Source ordinals, read from {@code noppes.npcs.constants.EnumAvailability*} in
 * {@code CustomNPCs-Unofficial-NeoForge-1.21.1.20251230.jar}: quest 0 Always / 1 After / 2 Before /
 * 3 Active / 4 NotActive / 5 Completed / <b>6 CanStart</b>; dialog 0 Always / 1 After / 2 Before;
 * faction type 0 Always / 1 Is / 2 IsNot; stance 0 Friendly / 1 Neutral / 2 Hostile. The xenoapi
 * {@code IAvailability} javadoc stops at 5 for quests, which is why a real save that uses 6 read as
 * an unmappable state here until it was checked against the jar.
 */
public final class AvailabilityImport {
    private AvailabilityImport() {}

    /**
     * Converts one source tag's {@code Availability*} fields.
     *
     * @param entry what the gate is attached to, for the report line when it cannot be carried
     *              ("quests/mynpcs_1_q3"). An unresolvable reference is stored gated shut - safe, but
     *              invisible in play unless it is named - while a condition the source itself always
     *              passed, or always failed, is carried across as the author left it.
     */
    public static QuestAvailability convert(CompoundTag raw, String mod, NpcImportReport report, String entry) {
        var quests = new ArrayList<QuestAvailability.QuestGate>();
        var dialogs = new ArrayList<QuestAvailability.DialogGate>();
        var factions = new ArrayList<QuestAvailability.FactionGate>();
        var scores = new ArrayList<QuestAvailability.ScoreGate>();
        for (int i = 1; i <= 4; i++) {
            String suffix = i == 1 ? "" : Integer.toString(i);
            String q = "AvailabilityQuest" + suffix;
            int state = raw.getInt(q);
            if (state != 0) {
                int slot = raw.getInt(q + "Id");
                if (state < 1 || state >= QuestAvailability.QuestState.values().length) {
                    return blocked(report, entry, "availability " + q + "=" + state
                            + " is not a quest state the source has; slot=" + slot);
                }
                QuestAvailability.QuestState questState = QuestAvailability.QuestState.values()[state];
                if (slot < 0) {
                    if (negatedQuestState(questState)) {
                        report.noteGrouped("availability " + q + " referenced no quest at all, which the"
                                + " source let through for every player; condition dropped", entry);
                    } else {
                        return blocked(report, entry, "availability " + q + " " + questState
                                + " referenced no quest at all; that gate was already impossible in the"
                                + " source, so the entry was never offered");
                    }
                } else {
                    String id = report.quest(mod, slot);
                    if (id == null) {
                        return blocked(report, entry, "availability " + q + " " + questState
                                + " references quest slot " + slot + ", which did not import");
                    }
                    quests.add(new QuestAvailability.QuestGate(id, questState));
                }
            }
            String d = "AvailabilityDialog" + suffix;
            state = raw.getInt(d);
            if (state != 0) {
                int slot = raw.getInt(d + "Id");
                if (state < 1 || state >= QuestAvailability.DialogState.values().length) {
                    return blocked(report, entry, "availability " + d + "=" + state
                            + " is not a dialog state the source has; slot=" + slot);
                }
                QuestAvailability.DialogState dialogState = QuestAvailability.DialogState.values()[state];
                if (slot < 0) {
                    if (dialogState == QuestAvailability.DialogState.BEFORE) {
                        report.noteGrouped("availability " + d + " referenced no dialog at all, which the"
                                + " source let through for every player; condition dropped", entry);
                    } else {
                        return blocked(report, entry, "availability " + d + " " + dialogState
                                + " referenced no dialog at all; that gate was already impossible in the"
                                + " source, so the entry was never offered");
                    }
                } else {
                    var ref = report.dialog(mod, slot);
                    if (ref == null) {
                        return blocked(report, entry, "availability " + d + " " + dialogState
                                + " references dialog slot " + slot + ", which did not import");
                    }
                    dialogs.add(new QuestAvailability.DialogGate(ref.group() + "/" + ref.id(), dialogState));
                }
            }
        }
        for (int i = 1; i <= 2; i++) {
            String suffix = i == 1 ? "" : "2";
            String f = "AvailabilityFaction" + suffix;
            int state = raw.getInt(f);
            if (state != 0) {
                int slot = raw.getInt(f + "Id");
                int stance = raw.getInt(f + "Stance");
                if (state < 1 || state > 2 || stance < 0 || stance > 2) {
                    return blocked(report, entry, "availability " + f + "=" + state
                            + " is not a faction state the source has; stance=" + stance);
                }
                if (slot < 0) {
                    report.noteGrouped("availability " + f + " referenced no faction at all, which the"
                            + " source let through for every player; condition dropped", entry);
                } else {
                    String id = report.faction(mod, slot);
                    if (id == null) {
                        return blocked(report, entry, "availability " + f + " references faction slot "
                                + slot + ", which did not import");
                    }
                    factions.add(new QuestAvailability.FactionGate(id,
                            QuestAvailability.Stance.values()[stance], state == 1));
                }
            }
            String s = "AvailabilityScoreboard" + suffix;
            String objective = raw.getString(s + "Objective");
            if (!objective.isBlank()) {
                int compare = raw.getInt(s + "Type");
                if (compare < 0 || compare > 2) {
                    return blocked(report, entry, "unmappable availability " + s + "Type=" + compare);
                }
                scores.add(new QuestAvailability.ScoreGate(objective, QuestAvailability.Compare.values()[compare], raw.getInt(s + "Value")));
            }
        }
        int daytime = raw.getInt("AvailabilityDayTime");
        if (daytime < 0 || daytime > 2) {
            return blocked(report, entry, "unmappable AvailabilityDayTime=" + daytime);
        }
        QuestAvailability gate = new QuestAvailability(quests, dialogs,
                daytime == 1 ? "night" : daytime == 2 ? "day" : "always",
                factions, scores, raw.getInt("AvailabilityMinPlayerLevel"));
        if (!QuestAvailability.NONE.equals(gate)) {
            // The rule is enforced now, but the operator still has to see which one the author
            // meant, because a converted gate that reads differently in play is otherwise silent.
            report.note("availability conditions converted:" + describe(gate));
        }
        return gate;
    }

    private static String describe(QuestAvailability gate) {
        StringBuilder out = new StringBuilder();
        for (QuestAvailability.QuestGate quest : gate.quests()) {
            out.append(" quest ").append(quest.questId()).append(' ').append(quest.state());
        }
        for (QuestAvailability.DialogGate dialog : gate.dialogs()) {
            out.append(" dialog ").append(dialog.dialogId()).append(' ').append(dialog.state());
        }
        for (QuestAvailability.FactionGate faction : gate.factions()) {
            out.append(" faction ").append(faction.factionId()).append(' ')
                    .append(faction.matches() ? "is" : "is not").append(' ').append(faction.stance());
        }
        for (QuestAvailability.ScoreGate score : gate.scores()) {
            out.append(" score ").append(score.objective()).append(' ').append(score.compare())
                    .append(' ').append(score.value());
        }
        if (!"always".equals(gate.daytime())) out.append(" only at ").append(gate.daytime());
        if (gate.minLevel() > 0) out.append(" level>=").append(gate.minLevel());
        return out.toString();
    }

    /**
     * A gate nothing can satisfy, recorded as blocking {@code entry}.
     *
     * <p>Gating shut is the safe direction: an availability the converter could not read is not the
     * same as no availability, and offering a quest the author hid behind a condition the importer
     * could not resolve would hand out content early. The cost is that the entry now looks fine in
     * the store and invisible in play, which is exactly what the report line is for.
     *
     * <p>Not used where the source itself evaluated the condition as impossible (see
     * {@link #negatedQuestState}) or as always true - those two are the author's own doing, and
     * copying their outcome is parity, not a loss.
     */
    private static QuestAvailability blocked(NpcImportReport report, String entry, String reason) {
        report.blocked(entry, reason + "; stored with availability no player can meet");
        return new QuestAvailability(java.util.List.of(), java.util.List.of(
                new QuestAvailability.DialogGate("<unresolved-source-reference>", QuestAvailability.DialogState.AFTER)),
                "always", java.util.List.of(), java.util.List.of(), 0);
    }

    /**
     * Whether the source evaluated this quest condition as {@code not (something the player has)}.
     *
     * <p>An author who never picked a quest leaves the id at -1. Verified in the jar:
     * {@code PlayerQuestController.isQuestFinished/isQuestActive/canQuestBeAccepted} are all a map
     * lookup of the number and answer false for -1, so BEFORE and NOT_ACTIVE - which pass when the
     * answer is false - held for every player. Dropping those two keeps the entry as visible as it
     * was; the other four were already unsatisfiable, and reporting them as such tells the operator
     * the dialog was dead before the migration ran.
     */
    private static boolean negatedQuestState(QuestAvailability.QuestState state) {
        return state == QuestAvailability.QuestState.BEFORE
                || state == QuestAvailability.QuestState.NOT_ACTIVE;
    }
}
