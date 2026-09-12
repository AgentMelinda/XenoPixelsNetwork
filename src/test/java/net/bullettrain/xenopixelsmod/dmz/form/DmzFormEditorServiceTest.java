package net.bullettrain.xenopixelsmod.dmz.form;

import com.dragonminez.common.config.FormConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzFormEditorServiceTest {

    // -- identifier and payload validation ---------------------------------

    @Test
    void identifiersAreNormalisedAndMalformedOnesRejected() {
        assertEquals("xeno_custom", DmzFormEditorService.id("  Xeno_Custom  ", "group"));
        assertEquals("form.one-2", DmzFormEditorService.id("Form.One-2", "form"));
        for (String bad : List.of("", "   ", "bad group", "bad/group", "..", "../escape",
                "group$", "grou\\p")) {
            assertThrows(IllegalArgumentException.class,
                    () -> DmzFormEditorService.id(bad, "group"), bad);
        }
    }

    @Test
    void nonFiniteNumbersAndOversizedPayloadsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DmzFormEditorService.validateJson(
                JsonParser.parseString("{\"a\":" + Double.MAX_VALUE + "0}"), 0));

        StringBuilder longString = new StringBuilder("\"");
        longString.append("x".repeat(4097)).append('"');
        assertThrows(IllegalArgumentException.class, () -> DmzFormEditorService.validateJson(
                JsonParser.parseString("{\"a\":" + longString + "}"), 0));

        StringBuilder bigList = new StringBuilder("[");
        for (int i = 0; i < 513; i++) bigList.append(i == 0 ? "1" : ",1");
        bigList.append(']');
        assertThrows(IllegalArgumentException.class, () -> DmzFormEditorService.validateJson(
                JsonParser.parseString("{\"a\":" + bigList + "}"), 0));

        String deep = "{\"a\":".repeat(20) + "1" + "}".repeat(20);
        assertThrows(IllegalArgumentException.class,
                () -> DmzFormEditorService.validateJson(JsonParser.parseString(deep), 0));

        // A realistic payload passes untouched.
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "human");
        DmzFormEditorService.validateJson(JsonParser.parseString(document.formJson()), 0);
    }

    @Test
    void formNamesMustMatchTheirKeyAndRegistryIdsMustParse() {
        FormConfig config = group("xeno_custom", "radiant");
        assertEquals(1, DmzFormEditorService.validateForms(config));

        config.getForms().get("radiant").setName("something_else");
        assertThrows(IllegalArgumentException.class, () -> DmzFormEditorService.validateForms(config));

        FormConfig badItem = group("xeno_custom", "radiant");
        badItem.getForms().get("radiant").getTriggerItemCosts()
                .add(triggerCost("Not A Valid Id"));
        assertThrows(IllegalArgumentException.class,
                () -> DmzFormEditorService.validateForms(badItem));

        FormConfig goodItem = group("xeno_custom", "radiant");
        goodItem.getForms().get("radiant").getTriggerItemCosts()
                .add(triggerCost("minecraft:diamond"));
        DmzFormEditorService.validateForms(goodItem);
    }

    @Test
    void theHighestUnlockLevelDrivesTheSkillCostArrayLength() {
        FormConfig config = group("xeno_custom", "radiant");
        config.getForms().get("radiant").setUnlockOnSkillLevel(3);
        assertEquals(3, DmzFormEditorService.validateForms(config));

        DmzFormMetadata metadata = metadata("human", "xeno_custom", "radiant_skill");
        metadata.skillCosts = new ArrayList<>(List.of(500));
        DmzFormEditorService.normalizeMetadata(metadata, config, 3);
        assertEquals(List.of(500, 0, 0), metadata.skillCosts);

        metadata.skillCosts = new ArrayList<>(List.of(500, 600, 700, 800, 900));
        DmzFormEditorService.normalizeMetadata(metadata, config, 3);
        assertEquals(List.of(500, 600, 700), metadata.skillCosts);
    }

    // -- localisation normalisation ----------------------------------------

    @Test
    void localeEntriesAreNormalisedAndAlwaysKeepAnEnglishFallback() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("es-ES", "  Ascension Radiante  ");
        source.put("bad locale", "ignored");
        source.put("fr_fr", "   ");
        source.put("de_de", "x".repeat(200));

        Map<String, String> names = DmzFormEditorService.normalizeNames(source, "radiant");
        assertEquals("Ascension Radiante", names.get("es_es"));
        assertFalse(names.containsKey("bad locale"));
        assertFalse(names.containsKey("fr_fr"), "blank values are dropped");
        assertFalse(names.containsKey("de_de"), "over-long values are dropped");
        assertEquals("radiant", names.get("en_us"), "the internal id is the last-resort name");

        assertEquals(Map.of("en_us", "radiant"),
                DmzFormEditorService.normalizeNames(null, "radiant"));
    }

    @Test
    void disablingMasterLearningClearsEveryLearningSource() {
        FormConfig config = group("xeno_custom", "radiant");
        DmzFormMetadata metadata = metadata("human", "xeno_custom", "radiant_skill");
        metadata.masterLearningEnabled = false;
        metadata.anyNativeMaster = true;
        metadata.nativeMasters = new ArrayList<>(List.of("roshi"));
        DmzFormMetadata.TrainerRef trainer = new DmzFormMetadata.TrainerRef();
        trainer.uuid = UUID.randomUUID().toString();
        metadata.customNpcTrainers = new ArrayList<>(List.of(trainer));

        DmzFormEditorService.normalizeMetadata(metadata, config, 1);
        assertFalse(metadata.anyNativeMaster);
        assertTrue(metadata.nativeMasters.isEmpty());
        assertTrue(metadata.customNpcTrainers.isEmpty());
    }

    @Test
    void skillMasterTrainerIsKeptWhenMasterLearningStaysOn() {
        FormConfig config = group("xeno_custom", "radiant");
        DmzFormMetadata metadata = metadata("human", "xeno_custom", "radiant_skill");
        metadata.masterLearningEnabled = true;
        DmzFormMetadata.TrainerRef trainer = new DmzFormMetadata.TrainerRef();
        trainer.uuid = UUID.randomUUID().toString();
        trainer.skillMaster = true;
        metadata.customNpcTrainers = new ArrayList<>(List.of(trainer));

        DmzFormEditorService.normalizeMetadata(metadata, config, 1);
        assertEquals(1, metadata.customNpcTrainers.size());
        assertTrue(metadata.customNpcTrainers.get(0).skillMaster);
    }

    @Test
    void metadataForRemovedFormsIsDroppedAndMalformedMasterIdsFiltered() {
        FormConfig config = group("xeno_custom", "radiant");
        DmzFormMetadata metadata = metadata("human", "xeno_custom", "radiant_skill");
        metadata.form("stale").names.put("en_us", "Stale");
        metadata.masterLearningEnabled = true;
        metadata.nativeMasters = new ArrayList<>(List.of("Roshi", "bad master", "roshi"));

        DmzFormEditorService.normalizeMetadata(metadata, config, 1);
        assertFalse(metadata.forms.containsKey("stale"));
        assertTrue(metadata.forms.containsKey("radiant"));
        assertEquals(List.of("roshi"), metadata.nativeMasters);
    }

    // -- DragonMineZ skills and per-race cost patching ----------------------

    @Test
    void normalFormsRegisterUnderFormSkillsAndStackFormsUnderStackSkills() {
        DmzFormMetadata metadata = metadata("human", "xeno_custom", "radiant_skill");
        metadata.skillCosts = new ArrayList<>(List.of(500, 900));

        JsonObject normal = DmzFormEditorService.patchSkillsJson(
                new JsonObject(), metadata, null, DmzFormKind.NORMAL, 2);
        assertEquals("[\"radiant_skill\"]", normal.getAsJsonArray("formSkills").toString());
        assertFalse(normal.has("stackSkills"));
        assertEquals("[500,900]", normal.getAsJsonObject("skills")
                .getAsJsonObject("radiant_skill").getAsJsonArray("costs").toString());
        assertTrue(normal.getAsJsonObject("skills").getAsJsonObject("radiant_skill")
                .has("allowedRaces"));

        DmzFormMetadata stackMeta = metadata("", "xeno_stack", "radiant_stack");
        JsonObject stack = DmzFormEditorService.patchSkillsJson(
                new JsonObject(), stackMeta, null, DmzFormKind.STACK, 1);
        assertEquals("[\"radiant_stack\"]", stack.getAsJsonArray("stackSkills").toString());
        assertFalse(stack.has("formSkills"));
    }

    @Test
    void existingSkillEntriesAreExtendedRatherThanReplaced() {
        JsonObject existing = JsonParser.parseString("""
                {"kiSkills":["kamehameha"],
                 "formSkills":["superforms"],
                 "skills":{"kamehameha":{"costs":[2000],"allowedRaces":["human"]}},
                 "skillOfferings":{"roshi":["kamehameha"]}}
                """).getAsJsonObject();
        DmzFormMetadata metadata = metadata("human", "xeno_custom", "radiant_skill");
        metadata.masterLearningEnabled = true;
        metadata.nativeMasters = new ArrayList<>(List.of("roshi"));
        metadata.skillCosts = new ArrayList<>(List.of(750));

        JsonObject patched = DmzFormEditorService.patchSkillsJson(
                existing, metadata, null, DmzFormKind.NORMAL, 1);
        assertEquals("[\"kamehameha\"]", patched.getAsJsonArray("kiSkills").toString());
        assertEquals("[\"superforms\",\"radiant_skill\"]",
                patched.getAsJsonArray("formSkills").toString());
        assertEquals("[2000]", patched.getAsJsonObject("skills")
                .getAsJsonObject("kamehameha").getAsJsonArray("costs").toString());
        assertEquals("[\"kamehameha\",\"radiant_skill\"]",
                patched.getAsJsonObject("skillOfferings").getAsJsonArray("roshi").toString());
    }

    @Test
    void anyNativeMasterUsesDragonMineZsDefaultOfferingBucket() {
        DmzFormMetadata metadata = metadata("human", "xeno_custom", "radiant_skill");
        metadata.masterLearningEnabled = true;
        metadata.anyNativeMaster = true;
        metadata.nativeMasters = new ArrayList<>(List.of("roshi"));

        JsonObject patched = DmzFormEditorService.patchSkillsJson(
                new JsonObject(), metadata, null, DmzFormKind.NORMAL, 1);
        JsonObject offerings = patched.getAsJsonObject("skillOfferings");
        assertEquals("[\"radiant_skill\"]", offerings.getAsJsonArray("default").toString());
        assertEquals("[\"radiant_skill\"]", offerings.getAsJsonArray("roshi").toString());
    }

    @Test
    void deselectingAMasterRemovesOnlyThisFormTypeFromThatOffering() {
        JsonObject existing = JsonParser.parseString(
                "{\"skillOfferings\":{\"roshi\":[\"kamehameha\",\"radiant_skill\"],"
                        + "\"goku\":[\"radiant_skill\"]}}").getAsJsonObject();
        DmzFormMetadata old = metadata("human", "xeno_custom", "radiant_skill");
        old.masterLearningEnabled = true;
        old.nativeMasters = new ArrayList<>(List.of("roshi", "goku"));

        DmzFormMetadata next = metadata("human", "xeno_custom", "radiant_skill");
        next.masterLearningEnabled = true;
        next.nativeMasters = new ArrayList<>(List.of("goku"));

        JsonObject patched = DmzFormEditorService.patchSkillsJson(
                existing, next, old, DmzFormKind.NORMAL, 1);
        JsonObject offerings = patched.getAsJsonObject("skillOfferings");
        assertEquals("[\"kamehameha\"]", offerings.getAsJsonArray("roshi").toString());
        assertEquals("[\"radiant_skill\"]", offerings.getAsJsonArray("goku").toString());
    }

    @Test
    void perRaceTpPricesUseTheBuyFromMasterShapeDragonMineZReads() {
        DmzFormMetadata metadata = metadata("human", "xeno_custom", "radiant_skill");
        metadata.buyFromMaster = true;
        metadata.skillCosts = new ArrayList<>(List.of(500, 900));

        JsonObject root = JsonParser.parseString(
                "{\"raceName\":\"human\",\"formSkillsCosts\":{\"superforms\":"
                        + "{\"buyFromMaster\":false,\"prices\":[1000]}}}").getAsJsonObject();
        JsonObject patched = DmzFormEditorService.patchRaceCharacterJson(root, metadata, 2);
        JsonObject costs = patched.getAsJsonObject("formSkillsCosts");
        assertEquals("human", patched.get("raceName").getAsString());
        assertTrue(costs.has("superforms"), "unrelated race form costs survive");
        JsonObject entry = costs.getAsJsonObject("radiant_skill");
        assertTrue(entry.get("buyFromMaster").getAsBoolean());
        assertEquals("[500,900]", entry.getAsJsonArray("prices").toString());
    }

    // -- file transaction ---------------------------------------------------

    @Test
    void configPathsStayInsideTheDragonMineZRoot(@TempDir Path root) {
        Path dmz = root.resolve("dragonminez");
        assertEquals(dmz.resolve("skills.json").toAbsolutePath().normalize(),
                DmzFormEditorService.configPath(dmz, "skills"));
        assertEquals(dmz.resolve("races").resolve("human").resolve("forms")
                        .resolve("xeno_custom.json").toAbsolutePath().normalize(),
                DmzFormEditorService.configPath(dmz, "races/human/forms/xeno_custom"));
        assertEquals(dmz.resolve("forms").resolve("xeno_stack.json").toAbsolutePath().normalize(),
                DmzFormEditorService.configPath(dmz, "forms/xeno_stack"));
        assertThrows(IllegalArgumentException.class,
                () -> DmzFormEditorService.configPath(dmz, "../../escaped"));
        assertThrows(IllegalArgumentException.class,
                () -> DmzFormEditorService.configPath(dmz, "races/../../escaped"));
    }

    @Test
    void oneSessionBacksUpEachFileExactlyOnce(@TempDir Path configDir) throws IOException {
        Path skills = configDir.resolve("dragonminez").resolve("skills.json");
        Files.createDirectories(skills.getParent());
        Files.writeString(skills, "{\"first\":true}", StandardCharsets.UTF_8);
        String session = UUID.randomUUID().toString();

        DmzFormEditorService.backupOnce(skills, configDir, session);
        Files.writeString(skills, "{\"second\":true}", StandardCharsets.UTF_8);
        DmzFormEditorService.backupOnce(skills, configDir, session);

        Path backups = configDir.resolve("xenopixelsmod").resolve("dmz-form-editor").resolve("backups");
        List<Path> copies;
        try (var walk = Files.walk(backups)) {
            copies = walk.filter(Files::isRegularFile).toList();
        }
        assertEquals(1, copies.size(), "a session must not overwrite its own pre-edit snapshot");
        assertEquals("{\"first\":true}", Files.readString(copies.get(0)));

        // A second session takes its own snapshot of the now-current bytes.
        DmzFormEditorService.backupOnce(skills, configDir, UUID.randomUUID().toString());
        try (var walk = Files.walk(backups)) {
            assertEquals(2, walk.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void aMissingFileIsNotBackedUp(@TempDir Path configDir) throws IOException {
        Path absent = configDir.resolve("dragonminez").resolve("nothing.json");
        DmzFormEditorService.backupOnce(absent, configDir, UUID.randomUUID().toString());
        assertFalse(Files.exists(configDir.resolve("xenopixelsmod")));
    }

    @Test
    void writesAreAtomicReplacementsThatLeaveNoTempFiles(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("nested").resolve("skills.json");
        DmzFormEditorService.atomicWrite(target, "{\"a\":1}");
        assertEquals("{\"a\":1}", Files.readString(target));
        DmzFormEditorService.atomicWrite(target, "{\"a\":2}");
        assertEquals("{\"a\":2}", Files.readString(target));
        try (var files = Files.list(target.getParent())) {
            assertEquals(List.of(target.getFileName().toString()),
                    files.map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test
    void aFailedTransactionRestoresEveryFileItHadAlreadyWritten(@TempDir Path dir)
            throws IOException {
        Path existing = dir.resolve("skills.json");
        Path created = dir.resolve("races").resolve("human").resolve("forms").resolve("new.json");
        Files.writeString(existing, "{\"original\":true}");

        Map<Path, byte[]> oldBytes = new LinkedHashMap<>();
        List<Path> written = new ArrayList<>();
        DmzFormEditorService.remember(existing, oldBytes);
        DmzFormEditorService.atomicWrite(existing, "{\"edited\":true}");
        written.add(existing);
        DmzFormEditorService.remember(created, oldBytes);
        DmzFormEditorService.atomicWrite(created, "{\"brand\":\"new\"}");
        written.add(created);

        DmzFormEditorService.rollback(oldBytes, written);
        assertEquals("{\"original\":true}", Files.readString(existing),
                "an edited file goes back to its prior bytes");
        assertFalse(Files.exists(created), "a file the transaction created is removed again");
    }

    // -- helpers ------------------------------------------------------------

    private static FormConfig group(String groupName, String formId) {
        FormConfig config = new FormConfig();
        config.setConfigVersion(FormConfig.CURRENT_VERSION);
        config.setGroupName(groupName);
        config.setFormType("radiant_skill");
        FormConfig.FormData data = new FormConfig.FormData();
        data.setName(formId);
        Map<String, FormConfig.FormData> forms = new LinkedHashMap<>();
        forms.put(formId, data);
        config.setForms(forms);
        return config;
    }

    private static FormConfig.FormData.TriggerItemCost triggerCost(String itemId) {
        return DmzFormMetadataRegistry.gson().fromJson(
                "{\"itemId\":\"" + itemId + "\",\"count\":1,\"consume\":true}",
                FormConfig.FormData.TriggerItemCost.class);
    }

    private static DmzFormMetadata metadata(String race, String group, String formType) {
        DmzFormMetadata metadata = new DmzFormMetadata();
        metadata.race = race;
        metadata.group = group;
        metadata.formType = formType;
        metadata.form("radiant").names.put("en_us", "Radiant");
        return metadata;
    }

    @Test
    void modelScalingStillRoundTripsThroughTheRawArrayForm() {
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "human");
        document.update("modelScaling", "[1.0,1.0,1.0]");
        assertArrayEquals(new Float[]{1.0F, 1.0F, 1.0F}, document.previewData().getModelScaling());
    }
}
