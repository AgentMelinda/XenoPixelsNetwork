package net.bullettrain.xenopixelsmod.npc.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * What an import did, and what it could not do.
 *
 * <p>An import that reports only a count is an import an operator has to take on trust. Every
 * dropped field, renamed id and failed file is recorded with enough detail to go and look, because
 * the failure mode of a migration is not a crash — it is content that quietly did not arrive.
 */
public final class NpcImportReport {

    /** One file that could not be read or converted. */
    public record Failure(String category, String entry, String reason) {
        @Override
        public String toString() {
            return category + "/" + entry + ": " + reason;
        }
    }

    /** Subjects listed inline before the line is truncated to a count. */
    private static final int MAX_LISTED_SUBJECTS = 6;

    private final List<String> entries = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final java.util.Map<String, List<String>> grouped = new java.util.LinkedHashMap<>();
    private final List<Failure> failures = new ArrayList<>();
    private final java.util.Map<String, DialogTreeImport.Ref> dialogRefs = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> factionRefs = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> questRefs = new java.util.LinkedHashMap<>();

    /** Records one entry written. */
    public void imported(String category, String id) {
        entries.add(category + "/" + id);
    }

    /** Records something dropped, renamed or otherwise not carried across verbatim. */
    public void note(String note) {
        if (note != null && !note.isBlank()) {
            notes.add(note);
        }
    }

    /** Records one entry that did not make it, and why. */
    public void failed(String category, String entry, String reason) {
        failures.add(new Failure(category, entry, reason));
    }

    /** Source dialog slot to the imported shared tree containing it. */
    public void mapDialog(String mod, int sourceSlot, DialogTreeImport.Ref target) {
        if (mod != null && target != null) dialogRefs.put(mod + ":" + sourceSlot, target);
    }

    public DialogTreeImport.Ref dialog(String mod, int sourceSlot) {
        return dialogRefs.get(mod + ":" + sourceSlot);
    }

    public void mapFaction(String mod, int sourceSlot, String targetId) {
        if (mod != null && targetId != null) factionRefs.put(mod + ":" + sourceSlot, targetId);
    }

    public String faction(String mod, int sourceSlot) {
        return factionRefs.get(mod + ":" + sourceSlot);
    }

    public void mapQuest(String mod, int sourceSlot, String targetId) {
        if (mod != null && targetId != null) questRefs.put(mod + ":" + sourceSlot, targetId);
    }

    public String quest(String mod, int sourceSlot) {
        return questRefs.get(mod + ":" + sourceSlot);
    }

    /**
     * Records a note that will repeat across many entries, folded into one line.
     *
     * <p>The first real import produced twenty-five notes for five factions: four identical drop
     * lines per faction, plus a rename apiece. Every line was true and, after the first of each
     * kind, none of it was information. A report nobody reads is the same as no report, and for a
     * migration that is the failure that matters.
     *
     * @param what    the thing that happened, identical across entries
     * @param subject which entry it happened to
     */
    public void noteGrouped(String what, String subject) {
        if (what == null || what.isBlank()) {
            return;
        }
        grouped.computeIfAbsent(what, key -> new ArrayList<>())
                .add(subject == null ? "?" : subject);
    }

    public int imported() {
        return entries.size();
    }

    public List<String> entries() {
        return List.copyOf(entries);
    }

    /**
     * Every note, with repeated ones folded into a single counted line.
     *
     * <p>Singular notes come first and are never folded: an unresolved hostility affects one
     * faction and is the most important thing a faction import can say.
     */
    public List<String> notes() {
        List<String> out = new ArrayList<>(notes);
        for (var entry : grouped.entrySet()) {
            List<String> subjects = entry.getValue();
            StringBuilder line = new StringBuilder(entry.getKey())
                    .append(" ×").append(subjects.size()).append(" (");
            for (int i = 0; i < Math.min(MAX_LISTED_SUBJECTS, subjects.size()); i++) {
                if (i > 0) {
                    line.append(", ");
                }
                line.append(subjects.get(i));
            }
            if (subjects.size() > MAX_LISTED_SUBJECTS) {
                line.append(", +").append(subjects.size() - MAX_LISTED_SUBJECTS).append(" more");
            }
            out.add(line.append(')').toString());
        }
        return List.copyOf(out);
    }

    public List<Failure> failures() {
        return List.copyOf(failures);
    }

    /** One line an operator can read in chat, with the detail available underneath. */
    public String summary() {
        return entries.size() + " imported, " + notes().size() + " note(s), "
                + failures.size() + " failed";
    }
}
