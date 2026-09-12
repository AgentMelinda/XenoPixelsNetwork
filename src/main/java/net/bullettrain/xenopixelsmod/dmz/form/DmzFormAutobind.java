package net.bullettrain.xenopixelsmod.dmz.form;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Binds a newly created form to the skill-master NPC that should teach it.
 *
 * <p>Pure so the editor transaction can call it before anything is written, and so the
 * bind/rollback rules are testable without a server. The caller applies the returned metadata
 * inside the same backup-and-rollback block as the form file, so a failed write cannot leave a
 * form that exists with no master, or a master offering a form that was never written.
 *
 * <p>Only forms that are genuinely new are added: re-saving an existing form must not duplicate
 * entries in {@code offeredForms}, and an empty {@code offeredForms} means "every form in the
 * group", so a trainer that offers nothing specific must stay that way rather than being
 * narrowed to just the new form.
 */
public final class DmzFormAutobind {
    private DmzFormAutobind() {
    }

    /**
     * Adds {@code addedForms} to the master's offered list.
     *
     * @param metadata   the metadata about to be written; mutated in place
     * @param trainerId  the designated skill-master NPC, or {@code null} to do nothing
     * @param addedForms form ids present now that were absent before, in form-id order
     * @return the trainer record that was changed, or {@code null} when nothing changed
     */
    /**
     * Inserts or promotes {@code trainerId} as a skill master on {@code metadata}.
     *
     * <p>Used when the wand NPC already has the skill-master role: creating a form on that NPC
     * must list it as a trainer, otherwise {@link #bindNewForms} has nothing to attach the new
     * form to. An existing record is reused so title/body/offers already typed in the role editor
     * are not wiped. {@code masterLearningEnabled} is turned on because a skill-master role with
     * the module off cannot open a menu.
     */
    public static DmzFormMetadata.TrainerRef ensureMaster(DmzFormMetadata metadata, UUID trainerId,
                                                          String name, String dimension) {
        if (metadata == null || trainerId == null) return null;
        if (metadata.customNpcTrainers == null) {
            metadata.customNpcTrainers = new java.util.ArrayList<>();
        }
        DmzFormMetadata.TrainerRef ref = DmzSkillMaster.trainerRef(metadata, trainerId);
        if (ref == null) {
            ref = new DmzFormMetadata.TrainerRef();
            ref.uuid = trainerId.toString();
            metadata.customNpcTrainers.add(ref);
        }
        if (name != null && !name.isBlank()) ref.name = name;
        if (dimension != null && !dimension.isBlank()) ref.dimension = dimension;
        ref.skillMaster = true;
        metadata.masterLearningEnabled = true;
        return ref;
    }

    public static DmzFormMetadata.TrainerRef bindNewForms(DmzFormMetadata metadata, UUID trainerId,
                                                          Collection<String> addedForms) {
        if (metadata == null || trainerId == null || addedForms == null || addedForms.isEmpty()) {
            return null;
        }
        DmzFormMetadata.TrainerRef ref = DmzSkillMaster.trainerRef(metadata, trainerId);
        if (ref == null) return null;
        if (ref.offeredForms == null) ref.offeredForms = new java.util.ArrayList<>();
        // An empty list means "every form in the group" — see DmzSkillMaster.offeredForms — so a
        // new form is already offered implicitly. Appending it would narrow the offer to just that
        // form, which is a behaviour change rather than a bind.
        if (ref.offeredForms.isEmpty()) return null;
        boolean changed = false;
        for (String form : addedForms) {
            if (form == null || form.isBlank() || ref.offeredForms.contains(form)) continue;
            if (ref.offeredForms.size() >= MAX_OFFERED_FORMS) break;
            ref.offeredForms.add(form);
            changed = true;
        }
        return changed ? ref : null;
    }

    /**
     * Form ids that exist in {@code current} but not in {@code previous}, in iteration order.
     *
     * <p>An absent previous map counts as "no forms", so the first save of a group binds every
     * form it contains.
     */
    public static List<String> addedForms(DmzFormMetadata previous, DmzFormMetadata current) {
        if (current == null || current.forms == null || current.forms.isEmpty()) return List.of();
        List<String> added = new java.util.ArrayList<>();
        for (String form : current.forms.keySet()) {
            if (form == null || form.isBlank()) continue;
            if (previous == null || previous.forms == null || !previous.forms.containsKey(form)) {
                added.add(form);
            }
        }
        return added;
    }

    /** Upper bound on form ids one trainer may list, matching the editor's list bound. */
    public static final int MAX_OFFERED_FORMS = 256;
}