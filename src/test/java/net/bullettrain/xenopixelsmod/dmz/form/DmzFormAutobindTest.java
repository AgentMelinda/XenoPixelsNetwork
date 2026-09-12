package net.bullettrain.xenopixelsmod.dmz.form;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers which forms a save binds to a skill master, and which it must leave alone. */
class DmzFormAutobindTest {

    @Test
    void firstSaveOfAGroupBindsEveryFormItContains() {
        DmzFormMetadata current = metadata();
        current.forms.put("ssj", new DmzFormMetadata.FormEntry());
        current.forms.put("ssj2", new DmzFormMetadata.FormEntry());
        UUID trainer = UUID.randomUUID();
        current.customNpcTrainers.add(trainerRef(trainer));

        assertEquals(List.of("ssj", "ssj2"), DmzFormAutobind.addedForms(null, current));
    }

    @Test
    void onlyFormsAbsentFromThePreviousMetadataCountAsAdded() {
        DmzFormMetadata previous = metadata();
        previous.forms.put("ssj", new DmzFormMetadata.FormEntry());
        DmzFormMetadata current = metadata();
        current.forms.put("ssj", new DmzFormMetadata.FormEntry());
        current.forms.put("ssj2", new DmzFormMetadata.FormEntry());

        assertEquals(List.of("ssj2"), DmzFormAutobind.addedForms(previous, current));
    }

    @Test
    void resavingAnExistingFormDoesNotDuplicateOrWidenTheOffer() {
        UUID trainer = UUID.randomUUID();
        DmzFormMetadata metadata = metadata();
        metadata.forms.put("ssj", new DmzFormMetadata.FormEntry());
        DmzFormMetadata.TrainerRef ref = trainerRef(trainer);
        metadata.customNpcTrainers.add(ref);

        // An empty offeredForms means "every form in the group"; the new form is already offered
        // implicitly, so binding must leave the list empty rather than narrowing it to one form.
        assertNull(DmzFormAutobind.bindNewForms(metadata, trainer, List.of("ssj")),
                "an implicit all-forms offer needs no write");
        assertTrue(ref.offeredForms.isEmpty());

        assertNull(DmzFormAutobind.bindNewForms(metadata, trainer, List.of()));
        assertTrue(ref.offeredForms.isEmpty());
    }

    @Test
    void bindingAppendsOnlyMissingFormsAndReportsTheChange() {
        UUID trainer = UUID.randomUUID();
        DmzFormMetadata metadata = metadata();
        DmzFormMetadata.TrainerRef ref = trainerRef(trainer);
        ref.offeredForms.add("ssj");
        metadata.customNpcTrainers.add(ref);

        DmzFormMetadata.TrainerRef changed =
                DmzFormAutobind.bindNewForms(metadata, trainer, List.of("ssj", "ssj2"));

        assertSame(ref, changed);
        assertEquals(List.of("ssj", "ssj2"), ref.offeredForms);
    }

    @Test
    void anUndesignatedTrainerIsNeverBound() {
        UUID trainer = UUID.randomUUID();
        DmzFormMetadata metadata = metadata();
        metadata.customNpcTrainers.add(trainerRef(UUID.randomUUID()));

        assertNull(DmzFormAutobind.bindNewForms(metadata, trainer, List.of("ssj")));
    }

    @Test
    void bindingIsBoundedByTheOfferedFormLimit() {
        UUID trainer = UUID.randomUUID();
        DmzFormMetadata metadata = metadata();
        DmzFormMetadata.TrainerRef ref = trainerRef(trainer);
        for (int i = 0; i < DmzFormAutobind.MAX_OFFERED_FORMS; i++) {
            ref.offeredForms.add("form" + i);
        }
        metadata.customNpcTrainers.add(ref);

        DmzFormAutobind.bindNewForms(metadata, trainer, List.of("overflow"));

        assertEquals(DmzFormAutobind.MAX_OFFERED_FORMS, ref.offeredForms.size());
        assertFalse(ref.offeredForms.contains("overflow"));
    }

    @Test
    void addedFormsToleratesNullAndBlankIds() {
        DmzFormMetadata current = metadata();
        current.forms.put("ssj", new DmzFormMetadata.FormEntry());
        current.forms.put("", new DmzFormMetadata.FormEntry());

        assertEquals(List.of("ssj"), DmzFormAutobind.addedForms(null, current));
        assertEquals(List.of(), DmzFormAutobind.addedForms(null, null));

        DmzFormMetadata empty = metadata();
        assertEquals(List.of(), DmzFormAutobind.addedForms(null, empty));
    }

    @Test
    void autobindSkipsTrainersWithoutAUsableUuid() {
        DmzFormMetadata previous = metadata();
        DmzFormMetadata current = metadata();
        current.forms.put("ssj", new DmzFormMetadata.FormEntry());
        DmzFormMetadata.TrainerRef broken = trainerRef(null);
        broken.uuid = "not-a-uuid";
        broken.offeredForms.add("existing");
        current.customNpcTrainers.add(broken);

        DmzFormEditorService.autobindNewForms(current, previous);

        assertEquals(List.of("existing"), broken.offeredForms,
                "an unparseable uuid is skipped, not partially bound");
    }

    @Test
    void autobindOnlyTouchesDesignatedSkillMasters() {
        UUID master = UUID.randomUUID();
        UUID plain = UUID.randomUUID();
        DmzFormMetadata previous = metadata();
        DmzFormMetadata current = metadata();
        current.forms.put("ssj", new DmzFormMetadata.FormEntry());
        DmzFormMetadata.TrainerRef masterRef = trainerRef(master, true);
        masterRef.offeredForms.add("existing");
        DmzFormMetadata.TrainerRef plainRef = trainerRef(plain, false);
        plainRef.offeredForms.add("existing");
        current.customNpcTrainers.add(masterRef);
        current.customNpcTrainers.add(plainRef);

        DmzFormEditorService.autobindNewForms(current, previous);

        assertEquals(List.of("existing", "ssj"), masterRef.offeredForms);
        assertEquals(List.of("existing"), plainRef.offeredForms,
                "a non-master trainer is never bound");
    }

    @Test
    void ensureMasterInsertsASkillMasterAndTurnsTheModuleOn() {
        DmzFormMetadata metadata = metadata();
        metadata.masterLearningEnabled = false;
        UUID trainer = UUID.randomUUID();

        DmzFormMetadata.TrainerRef ref = DmzFormAutobind.ensureMaster(
                metadata, trainer, "Dende", "minecraft:overworld");

        assertEquals(trainer.toString(), ref.uuid);
        assertEquals("Dende", ref.name);
        assertEquals("minecraft:overworld", ref.dimension);
        assertTrue(ref.skillMaster);
        assertTrue(metadata.masterLearningEnabled);
        assertSame(ref, DmzFormAutobind.ensureMaster(metadata, trainer, "Dende", "minecraft:overworld"),
                "a second call must reuse the same record");
        assertEquals(1, metadata.customNpcTrainers.size());
    }

    private static DmzFormMetadata metadata() {
        DmzFormMetadata metadata = new DmzFormMetadata();
        metadata.race = "saiyan";
        metadata.group = "customforms";
        metadata.formType = "customforms";
        metadata.masterLearningEnabled = true;
        metadata.skillCosts.add(100);
        metadata.customNpcTrainers = new ArrayList<>();
        metadata.forms = new java.util.LinkedHashMap<>();
        return metadata;
    }

    private static DmzFormMetadata.TrainerRef trainerRef(UUID id) {
        return trainerRef(id, true);
    }

    private static DmzFormMetadata.TrainerRef trainerRef(UUID id, boolean skillMaster) {
        DmzFormMetadata.TrainerRef ref = new DmzFormMetadata.TrainerRef();
        ref.uuid = id == null ? "" : id.toString();
        ref.name = "Elder";
        ref.dimension = "minecraft:overworld";
        ref.skillMaster = skillMaster;
        return ref;
    }
}