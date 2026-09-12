package net.bullettrain.xenopixelsmod.dmz.form;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two rules the role and the interaction path both depend on: the role-id sentinel is
 * collision-free against every native role id, and the skill-master predicate cannot disagree with
 * itself.
 */
class DmzSkillMasterTest {

    // ---- role id allocation -------------------------------------------------------------

    @Test
    void sentinelIsAboveEveryNativeRawIdInBothMods() {
        assertTrue(DmzSkillMaster.SENTINEL_ROLE_ID > DmzSkillMaster.MYNPCS_MAX_NATIVE_RAW,
                "sentinel must exceed MyNPCs' 0..11 chain");
        assertTrue(DmzSkillMaster.SENTINEL_ROLE_ID > DmzSkillMaster.CUSTOMNPCS_MAX_NATIVE_RAW,
                "sentinel must exceed CustomNPCs' 0..9 chain");
    }

    @Test
    void sentinelIsNotAnyNativeRoleTypeConstant() {
        int maxNativeConstant = DmzSkillMaster.NATIVE_ROLE_NAMES.size();
        for (int id = 0; id < maxNativeConstant; id++) {
            assertFalse(DmzSkillMaster.isSentinel(id),
                    "sentinel must not equal native RoleType ordinal " + id);
        }
    }

    @Test
    void sentinelStillAliasesANativeRoleUnderCustomNpcsNormalization() {
        // Documents the hazard the HEAD injection exists to avoid: CustomNPCs collapses every
        // input into 0..7, so the sentinel would silently become RoleDialog if it ever reached
        // the native chain. There is deliberately no sentinel that survives normalization.
        assertEquals(7, DmzSkillMaster.customNpcsNormalized(DmzSkillMaster.SENTINEL_ROLE_ID));
        for (int id = 0; id <= DmzSkillMaster.MYNPCS_MAX_NATIVE_RAW; id++) {
            int normalized = DmzSkillMaster.customNpcsNormalized(id);
            assertTrue(normalized >= 0 && normalized <= 7,
                    "normalization must stay inside 0..7 for native id " + id);
        }
    }

    @Test
    void normalizationMatchesTheVerifiedBytecodeShape() {
        // if (id >= 8) id -= 2; then id %= 8;
        assertEquals(0, DmzSkillMaster.customNpcsNormalized(0));
        assertEquals(1, DmzSkillMaster.customNpcsNormalized(1));
        assertEquals(7, DmzSkillMaster.customNpcsNormalized(7));
        assertEquals(6, DmzSkillMaster.customNpcsNormalized(8));   // 8 -> 6
        assertEquals(7, DmzSkillMaster.customNpcsNormalized(9));   // 9 -> 7
        assertEquals(4, DmzSkillMaster.customNpcsNormalized(14));  // 14 -> 12 -> 12 % 8 = 4
    }

    @Test
    void unknownIdsDegradeToNoRole() {
        assertEquals(DmzSkillMaster.ROLE_NONE, DmzSkillMaster.resolveUnknownId(DmzSkillMaster.SENTINEL_ROLE_ID));
        assertEquals(DmzSkillMaster.ROLE_NONE, DmzSkillMaster.resolveUnknownId(4242));
        assertEquals(DmzSkillMaster.ROLE_NONE, DmzSkillMaster.resolveUnknownId(-1));
        // A real native id still resolves to itself.
        assertEquals(7, DmzSkillMaster.resolveUnknownId(7));
        assertEquals(11, DmzSkillMaster.resolveUnknownId(11));
    }

    // ---- module predicate ---------------------------------------------------------------

    @Test
    void moduleIsOnOnlyWithLearningAndAType() {
        DmzFormMetadata metadata = metadata("customforms");
        assertTrue(DmzSkillMaster.moduleEnabled(metadata));

        DmzFormMetadata noLearning = metadata("customforms");
        noLearning.masterLearningEnabled = false;
        assertFalse(DmzSkillMaster.moduleEnabled(noLearning));

        DmzFormMetadata noType = metadata("");
        assertFalse(DmzSkillMaster.moduleEnabled(noType));

        // Skill costs are deliberately not part of the gate: the cost entry is written when the
        // form type is registered, so requiring one here made a fresh designation inert and the
        // master menu unreachable.
        DmzFormMetadata noCosts = metadata("customforms");
        noCosts.skillCosts = new ArrayList<>();
        assertTrue(DmzSkillMaster.moduleEnabled(noCosts));

        assertFalse(DmzSkillMaster.moduleEnabled(null));
    }

    @Test
    void designationAloneIsEnoughToBeASkillMaster() {
        UUID trainer = UUID.randomUUID();
        DmzFormMetadata metadata = metadata("customforms");
        metadata.customNpcTrainers.add(trainerRef(trainer, true));

        assertTrue(DmzSkillMaster.designated(metadata, trainer));
        assertTrue(DmzSkillMaster.isSkillMaster(metadata, trainer));

        // The per-trainer flag stands on its own: turning the group's master-learning module off
        // must not silently un-designate a trainer the operator explicitly marked.
        metadata.masterLearningEnabled = false;
        assertTrue(DmzSkillMaster.designated(metadata, trainer));
        assertTrue(DmzSkillMaster.isSkillMaster(metadata, trainer));

        // An undesignated trainer is never a master, however healthy the group's module is.
        assertFalse(DmzSkillMaster.isSkillMaster(metadata("customforms"), trainer));
    }

    @Test
    void undesignatedTrainerIsNeverASkillMaster() {
        UUID trainer = UUID.randomUUID();
        DmzFormMetadata metadata = metadata("customforms");
        metadata.customNpcTrainers.add(trainerRef(UUID.randomUUID(), true));

        assertFalse(DmzSkillMaster.designated(metadata, trainer));
        assertFalse(DmzSkillMaster.isSkillMaster(metadata, trainer));
        assertFalse(DmzSkillMaster.isSkillMaster(metadata, null));
        assertFalse(DmzSkillMaster.isSkillMaster(null, trainer));
    }

    @Test
    void skillMasterAnywhereScansEveryGroup() {
        UUID trainer = UUID.randomUUID();
        List<DmzFormMetadata> groups = new ArrayList<>();

        // Group 1 has the module on but does not designate this trainer, so the scan finds nothing.
        groups.add(metadata("inert"));
        assertFalse(DmzSkillMaster.isSkillMasterAnywhere(groups, trainer));

        // Group 2 designates the trainer, so the scan now finds a master.
        DmzFormMetadata designated = metadata("customforms");
        designated.customNpcTrainers.add(trainerRef(trainer, true));
        groups.add(designated);
        assertTrue(DmzSkillMaster.isSkillMasterAnywhere(groups, trainer));
        assertFalse(DmzSkillMaster.isSkillMasterAnywhere(groups, UUID.randomUUID()));
        assertFalse(DmzSkillMaster.isSkillMasterAnywhere(null, trainer));
    }

    // ---- menu text ----------------------------------------------------------------------

    @Test
    void menuTitleFallsBackThroughLocaleThenGroupName() {
        UUID trainer = UUID.randomUUID();
        DmzFormMetadata metadata = metadata("customforms");
        metadata.groupNames.put("en_us", "Form Master");
        metadata.groupNames.put("de_de", "Formmeister");
        DmzFormMetadata.TrainerRef ref = trainerRef(trainer, true);
        metadata.customNpcTrainers.add(ref);

        assertEquals("Form Master", DmzSkillMaster.menuTitle(metadata, trainer, "en_us", "id"));
        assertEquals("Formmeister", DmzSkillMaster.menuTitle(metadata, trainer, "de_de", "id"));
        // Unknown locale falls through to en_us, then to the group name, never to the raw id.
        assertEquals("Form Master", DmzSkillMaster.menuTitle(metadata, trainer, "fr_fr", "id"));

        ref.menuTitle = "Elder Kai";
        assertEquals("Elder Kai", DmzSkillMaster.menuTitle(metadata, trainer, "de_de", "id"));

        // No trainer record at all still resolves the group name rather than the fallback.
        assertEquals("Form Master", DmzSkillMaster.menuTitle(metadata, UUID.randomUUID(), "en_us", "id"));
        assertEquals("fallback",
                DmzSkillMaster.menuTitle(metadata(null), null, "en_us", "fallback"));
    }

    @Test
    void menuBodyResolvesLocaleThenEnUs() {
        UUID trainer = UUID.randomUUID();
        DmzFormMetadata metadata = metadata("customforms");
        DmzFormMetadata.TrainerRef ref = trainerRef(trainer, true);
        ref.menuBody.put("en_us", "Choose a form.");
        ref.menuBody.put("de_de", "Waehle eine Form.");
        metadata.customNpcTrainers.add(ref);

        assertEquals("Choose a form.", DmzSkillMaster.menuBody(metadata, trainer, "en_us"));
        assertEquals("Waehle eine Form.", DmzSkillMaster.menuBody(metadata, trainer, "de_de"));
        assertEquals("Choose a form.", DmzSkillMaster.menuBody(metadata, trainer, "ja_jp"));
        assertEquals("", DmzSkillMaster.menuBody(metadata, UUID.randomUUID(), "en_us"));
        assertEquals("", DmzSkillMaster.menuBody(null, trainer, "en_us"));
    }

    @Test
    void offeredFormsDefaultsToTheWholeGroup() {
        UUID trainer = UUID.randomUUID();
        DmzFormMetadata metadata = metadata("customforms");
        metadata.forms.put("ssj", new DmzFormMetadata.FormEntry());
        metadata.forms.put("ssj2", new DmzFormMetadata.FormEntry());
        DmzFormMetadata.TrainerRef ref = trainerRef(trainer, true);
        metadata.customNpcTrainers.add(ref);

        assertEquals(List.of("ssj", "ssj2"), DmzSkillMaster.offeredForms(metadata, trainer));

        ref.offeredForms.add("ssj2");
        assertEquals(List.of("ssj2"), DmzSkillMaster.offeredForms(metadata, trainer));

        assertEquals(List.of(), DmzSkillMaster.offeredForms(null, trainer));
        assertEquals(List.of(), DmzSkillMaster.offeredForms(metadata(null), trainer));
    }

    @Test
    void sanitizeBodyBoundsLocalesLengthsAndBlanks() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("en_us", "Body");
        body.put("de-DE", "Koerper");
        body.put("", "dropped");
        body.put("fr_fr", "   ");
        String long1 = "x".repeat(DmzSkillMaster.MAX_MENU_BODY_LENGTH + 50);
        body.put("es_es", long1);

        Map<String, String> cleaned = DmzSkillMaster.sanitizeBody(body);

        assertEquals("Body", cleaned.get("en_us"));
        assertEquals("Koerper", cleaned.get("de_de"), "dashes normalize to underscores");
        assertFalse(cleaned.containsKey(""));
        assertFalse(cleaned.containsKey("fr_fr"), "blank values are dropped");
        assertEquals(DmzSkillMaster.MAX_MENU_BODY_LENGTH, cleaned.get("es_es").length());

        // Locale count is bounded.
        Map<String, String> many = new LinkedHashMap<>();
        for (int i = 0; i < DmzSkillMaster.MAX_MENU_LOCALES + 8; i++) {
            many.put("l" + i, "value " + i);
        }
        assertEquals(DmzSkillMaster.MAX_MENU_LOCALES, DmzSkillMaster.sanitizeBody(many).size());
        assertEquals(0, DmzSkillMaster.sanitizeBody(null).size());
    }

    @Test
    void trainerLookupIsCaseInsensitiveAndNullSafe() {
        UUID trainer = UUID.randomUUID();
        DmzFormMetadata metadata = metadata("customforms");
        DmzFormMetadata.TrainerRef ref = trainerRef(trainer, true);
        ref.uuid = trainer.toString().toUpperCase(java.util.Locale.ROOT);
        metadata.customNpcTrainers.add(ref);

        assertNotNull(DmzSkillMaster.trainerRef(metadata, trainer));
        assertSame(ref, DmzSkillMaster.trainerRef(metadata, trainer));
        assertNull(DmzSkillMaster.trainerRef(metadata, null));
        assertNull(DmzSkillMaster.trainerRef(null, trainer));

        // A null list entry must not abort the scan.
        metadata.customNpcTrainers.add(0, null);
        assertSame(ref, DmzSkillMaster.trainerRef(metadata, trainer));
    }

    // ---- selector round trip ------------------------------------------------------------

    @Test
    void appendedSelectorSitsOnePastTheStockEntries() {
        // The two mods ship different list lengths, so the position is derived from the array the
        // mod itself built rather than hardcoded.
        assertEquals(8, DmzSkillMaster.appendedSelectorIndex(8));
        assertEquals(11, DmzSkillMaster.appendedSelectorIndex(11));
    }

    @Test
    void appendedSelectorMapsToTheSentinelAndBack() {
        // CustomNPCs: eight stock entries, appended position 8.
        assertEquals(DmzSkillMaster.SENTINEL_ROLE_ID, DmzSkillMaster.roleIdForSelector(8, 8));
        assertEquals(8, DmzSkillMaster.selectorIndex(DmzSkillMaster.SENTINEL_ROLE_ID, 8, 8));

        // MyNPCs: eleven stock entries, appended position 11.
        assertEquals(DmzSkillMaster.SENTINEL_ROLE_ID, DmzSkillMaster.roleIdForSelector(11, 11));
        assertEquals(11, DmzSkillMaster.selectorIndex(DmzSkillMaster.SENTINEL_ROLE_ID, 11, 11));

        assertTrue(DmzSkillMaster.isSelectorIndex(8, 8));
        assertTrue(DmzSkillMaster.isSelectorIndex(11, 11));
    }

    @Test
    void nativeSelectorPositionsPassThroughUnchanged() {
        for (int i = 0; i < 8; i++) {
            assertFalse(DmzSkillMaster.isSelectorIndex(i, 8),
                    "position " + i + " is a native entry, not the appended one");
            assertEquals(i, DmzSkillMaster.roleIdForSelector(i, 8));
        }
    }

    @Test
    void noNativePositionEverMapsToTheSentinel() {
        int[] lengths = {8, 11};
        for (int length : lengths) {
            for (int i = 0; i < length; i++) {
                assertFalse(DmzSkillMaster.isSentinel(DmzSkillMaster.roleIdForSelector(i, length)),
                        "native position " + i + " of " + length + " must not yield the sentinel");
            }
        }
    }

    @Test
    void shownSelectorIndexPrefersTheStoredSentinelOverAZeroConstructorValue() {
        // MyNPCs looks the stored id up in stock ROLE_TYPE_IDS and passes 0 when it is absent.
        assertEquals(8, DmzSkillMaster.shownSelectorIndex(DmzSkillMaster.SENTINEL_ROLE_ID, 0, 8));
        assertEquals(11, DmzSkillMaster.shownSelectorIndex(DmzSkillMaster.SENTINEL_ROLE_ID, 0, 11));
        // CustomNPCs passes the stored id as the constructor value; both agree.
        assertEquals(8, DmzSkillMaster.shownSelectorIndex(DmzSkillMaster.SENTINEL_ROLE_ID,
                DmzSkillMaster.SENTINEL_ROLE_ID, 8));
        // A native constructor index is kept so stock roles still render as the host computed them.
        assertEquals(3, DmzSkillMaster.shownSelectorIndex(3, 3, 8));
        assertEquals(3, DmzSkillMaster.shownSelectorIndex(0, 3, 8));
    }

    @Test
    void selectorDisplayFallsBackToNoneForOutOfRangeValues() {
        // A stored sentinel shows the appended entry, not a position the widget cannot render.
        assertEquals(8, DmzSkillMaster.selectorIndex(DmzSkillMaster.SENTINEL_ROLE_ID, 9001, 8));
        // An in-range native value is shown as-is.
        assertEquals(3, DmzSkillMaster.selectorIndex(3, 3, 8));
        // A native value outside the array degrades to NONE rather than an unrenderable position.
        assertEquals(DmzSkillMaster.ROLE_NONE, DmzSkillMaster.selectorIndex(4242, 4242, 8));
        assertEquals(DmzSkillMaster.ROLE_NONE, DmzSkillMaster.selectorIndex(-1, -1, 8));
    }

    @Test
    void selectorRulesTolerateAnEmptyStockList() {
        assertFalse(DmzSkillMaster.isSelectorIndex(0, 0));
        assertEquals(0, DmzSkillMaster.roleIdForSelector(0, 0));
    }

    // ---- helpers ------------------------------------------------------------------------

    private static DmzFormMetadata metadata(String formType) {
        DmzFormMetadata metadata = new DmzFormMetadata();
        metadata.race = "saiyan";
        metadata.group = "customforms";
        metadata.formType = formType;
        metadata.masterLearningEnabled = true;
        metadata.buyFromMaster = true;
        metadata.skillCosts.add(100);
        metadata.skillCosts.add(200);
        return metadata;
    }

    private static DmzFormMetadata.TrainerRef trainerRef(UUID id, boolean skillMaster) {
        DmzFormMetadata.TrainerRef ref = new DmzFormMetadata.TrainerRef();
        ref.uuid = id.toString();
        ref.name = "Elder";
        ref.dimension = "minecraft:overworld";
        ref.skillMaster = skillMaster;
        return ref;
    }
}