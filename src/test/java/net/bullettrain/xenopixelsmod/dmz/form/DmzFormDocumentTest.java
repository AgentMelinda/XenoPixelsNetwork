package net.bullettrain.xenopixelsmod.dmz.form;

import com.dragonminez.common.config.FormConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzFormDocumentTest {
    @AfterEach
    void clearMetadataRegistry() {
        DmzFormMetadataRegistry.applySnapshot("{}");
    }

    @Test
    void editorExposesEveryDragonMineZFormDataSetting() {
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "human");
        Set<String> fieldKeys = document.fields().stream()
                .map(DmzFormDocument.Field::key)
                .collect(Collectors.toSet());
        JsonObject serializedDefaults = DmzFormMetadataRegistry.gson()
                .toJsonTree(new FormConfig.FormData()).getAsJsonObject();

        for (String key : serializedDefaults.keySet()) {
            if ("name".equals(key)) continue;
            if ("modelScaling".equals(key)) {
                // Surfaced as the uniform control plus one field per axis.
                assertTrue(fieldKeys.contains(DmzFormDocument.SCALE_UNIFORM));
                assertTrue(fieldKeys.containsAll(Set.of("$scaleX", "$scaleY", "$scaleZ")));
                continue;
            }
            if (serializedDefaults.get(key).isJsonObject()) {
                // Nested objects are flattened one level so their colours get pickers too.
                for (String nested : serializedDefaults.getAsJsonObject(key).keySet()) {
                    assertTrue(fieldKeys.contains(key + "." + nested),
                            "missing nested form field: " + key + "." + nested);
                }
                continue;
            }
            assertTrue(fieldKeys.contains(key), "missing form field: " + key);
        }
        assertTrue(fieldKeys.containsAll(Set.of("$displayName", "$locales", "$groupName",
                "$groupLocales", "$formIcon", "$formTypeIcon", "$skillCosts", "$nativeMasters",
                "$customTrainers")));
    }

    @Test
    void everyScalarAndNestedListTypeSurvivesAJsonRoundTrip() {
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "human");
        document.update("unlockOnSkillLevel", "3");
        document.update("keepBaseFormHeadBones", "true");
        document.update("hasLightnings", "true");
        document.update("auraColor", "#12AB34");
        document.update("tintIntensity", "0.65");
        document.update("strMultiplier", "2.5");
        document.update("formRequisiteType", "any");
        document.update("incompatibleWith", "[\"ultimate.ultimate\",\"kaioken.kaioken\"]");
        document.update("shareMasteryWith", "[\"superforms.ssj\"]");
        document.update("triggerItemCosts",
                "[{\"itemId\":\"minecraft:diamond\",\"itemTag\":\"\",\"nbt\":\"\",\"count\":4,\"consume\":true}]");
        document.update("durationItemCosts",
                "[{\"itemId\":\"minecraft:emerald\",\"itemTag\":\"\",\"nbt\":\"\",\"durationSeconds\":30}]");
        document.update("mobEffects",
                "[{\"effectId\":\"minecraft:speed\",\"amplifier\":2,\"durationTicks\":-1,"
                        + "\"ambient\":false,\"visible\":true,\"showIcon\":true}]");
        document.update("outlineShader.enabled", "true");
        document.update("outlineShader.primaryColor", "#FF00FF");
        document.update("outlineShader.outlineThickness", "2.25");

        FormConfig.FormData data = document.previewData();
        assertEquals(3, data.getUnlockOnSkillLevel().intValue());
        assertTrue(data.isKeepBaseFormHeadBones());
        assertEquals("#12AB34", data.getAuraColor());
        assertEquals(0.65, data.getTintIntensity(), 1.0E-6);
        assertEquals(2.5, data.getStrMultiplier(), 1.0E-6);
        assertEquals("any", data.getFormRequisiteType());
        assertEquals(List.of("ultimate.ultimate", "kaioken.kaioken"), data.getIncompatibleWith());
        assertEquals(List.of("superforms.ssj"), data.getShareMasteryWith());
        assertEquals(1, data.getTriggerItemCosts().size());
        assertEquals("minecraft:diamond", data.getTriggerItemCosts().get(0).getItemId());
        assertEquals(4, data.getTriggerItemCosts().get(0).getCount());
        assertEquals(1, data.getDurationItemCosts().size());
        assertEquals(30, data.getDurationItemCosts().get(0).getDurationSeconds());
        assertEquals(1, data.getMobEffects().size());
        assertEquals("minecraft:speed", data.getMobEffects().get(0).getEffectId());
        assertEquals(2, data.getMobEffects().get(0).getAmplifier());
        assertTrue(data.getOutlineShader().isEnabled());
        assertEquals("#FF00FF", data.getOutlineShader().getPrimaryColor());
        assertEquals(2.25, data.getOutlineShader().getOutlineThickness(), 1.0E-6);
    }

    @Test
    void colourFieldsAreOfferedAPickerAndOtherTypesAreNot() {
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "human");
        var kinds = document.fields().stream().collect(
                Collectors.toMap(DmzFormDocument.Field::key, DmzFormDocument.Field::kind));
        for (String colour : List.of("bodyColor1", "bodyColor2", "bodyColor3", "extraFormColor",
                "hairColor", "eye1Color", "eye2Color", "auraColor", "extraAuraColor",
                "lightningColor", "tintColor", "outlineShader.primaryColor",
                "outlineShader.secondaryColor")) {
            assertEquals(DmzFormDocument.Kind.COLOR, kinds.get(colour), colour);
        }
        assertEquals(DmzFormDocument.Kind.BOOL, kinds.get("keepBaseFormHeadBones"));
        assertEquals(DmzFormDocument.Kind.BOOL, kinds.get("formStackable"));
        assertEquals(DmzFormDocument.Kind.NUMBER, kinds.get("strMultiplier"));
        assertEquals(DmzFormDocument.Kind.NUMBER, kinds.get("unlockOnSkillLevel"));
        assertEquals(DmzFormDocument.Kind.JSON, kinds.get("incompatibleWith"));
        assertEquals(DmzFormDocument.Kind.JSON, kinds.get("mobEffects"));
        assertEquals(DmzFormDocument.Kind.TEXT, kinds.get("customModel"));
        assertEquals(DmzFormDocument.Kind.SCALE, kinds.get(DmzFormDocument.SCALE_UNIFORM));
        assertEquals(DmzFormDocument.Kind.SCALE, kinds.get("$scaleY"));
    }

    @Test
    void uniformAndPerAxisBodySizeBothWriteModelScaling() {
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "human");
        document.update(DmzFormDocument.SCALE_UNIFORM, "1.5");
        assertArrayEquals(new Float[]{1.5F, 1.5F, 1.5F}, document.previewData().getModelScaling());

        document.update("$scaleY", "2.0");
        assertArrayEquals(new Float[]{1.5F, 2.0F, 1.5F}, document.previewData().getModelScaling());
        assertEquals(1.5F + 1.0F / 6.0F, document.uniformScale(), 1.0E-5);

        // Out-of-range and non-numeric body sizes never reach the config.
        document.update("$scaleX", "-4");
        assertEquals(0.05F, document.scale(0), 1.0E-6);
        assertThrows(NumberFormatException.class, () -> document.update("$scaleZ", "huge"));
    }

    @Test
    void newFormSupportsNamingLocalizationAndBodyScaling() {
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "human");
        document.update("$group", "xeno_custom");
        document.update("$form", "radiant");
        document.update("$formType", "radiant_skill");
        document.update("$displayName", "Radiant Ascension");
        document.update("$locales", "{\"en_us\":\"Radiant Ascension\",\"es_es\":\"Ascension Radiante\"}");
        document.update("$groupName", "Radiant Line");
        document.update("modelScaling", "[1.25,1.1,0.95]");

        JsonObject root = JsonParser.parseString(document.formJson()).getAsJsonObject();
        assertEquals("xeno_custom", root.get("groupName").getAsString());
        assertEquals("radiant_skill", root.get("formType").getAsString());
        assertTrue(root.getAsJsonObject("forms").has("radiant"));
        assertEquals("radiant", root.getAsJsonObject("forms").getAsJsonObject("radiant")
                .get("name").getAsString());
        assertArrayEquals(new Float[]{1.25F, 1.1F, 0.95F}, document.previewData().getModelScaling());

        DmzFormMetadata metadata = DmzFormMetadataRegistry.gson()
                .fromJson(document.metadataJson(), DmzFormMetadata.class);
        assertEquals("Radiant Ascension", metadata.forms.get("radiant").names.get("en_us"));
        assertEquals("Ascension Radiante", metadata.forms.get("radiant").names.get("es_es"));
        assertEquals("Radiant Line", metadata.groupNames.get("en_us"));
    }

    @Test
    void draftsStayLocalUntilTheirIdentityFieldsValidate() {
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "human");
        assertFalse(document.created());
        assertNull(document.identityError(), "the default draft identity should already be valid");

        document.update("$group", "Bad Group");
        assertNotNull(document.identityError());
        assertTrue(document.identityError().contains("Group id"));

        document.update("$group", "good_group");
        document.update("$form", "");
        assertNotNull(document.identityError());

        document.update("$form", "good_form");
        document.update("$race", "");
        assertNotNull(document.identityError());
        document.update("$race", "saiyan");
        assertNull(document.identityError());
    }

    @Test
    void creatingOverAGroupTheStudioAlreadyOwnsIsRejected() {
        DmzFormMetadata existing = new DmzFormMetadata();
        existing.race = "human";
        existing.group = "xeno_custom";
        existing.formType = "radiant_skill";
        JsonObject snapshot = new JsonObject();
        snapshot.add("normal:human:xeno_custom", DmzFormMetadataRegistry.gson().toJsonTree(existing));
        DmzFormMetadataRegistry.applySnapshot(snapshot.toString());

        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "human");
        document.update("$group", "xeno_custom");
        assertNotNull(document.identityError());
        assertTrue(document.identityError().contains("already owned"));
    }

    @Test
    void stackDraftsCarryNoRaceAndUseTheStackKeySpace() {
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.STACK, "human");
        assertEquals("", document.race());
        assertNull(document.identityError());
        document.update("$race", "saiyan");
        assertEquals("", document.race(), "stack forms must not take a race");
    }

    @Test
    void identityFieldsFreezeOnceTheFormExistsOnTheServer() {
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "human");
        document.update("$group", "xeno_custom");
        document.update("$form", "radiant");
        document.markCreated();

        document.update("$group", "other_group");
        document.update("$form", "other_form");
        document.update("$race", "saiyan");
        assertEquals("xeno_custom", document.group());
        assertEquals("radiant", document.form());
        assertEquals("human", document.race());
    }

    @Test
    void metadataResolvesLocalizedNamesAndBothIconLayouts() {
        DmzFormMetadata metadata = new DmzFormMetadata();
        metadata.race = "human";
        metadata.group = "xeno_custom";
        metadata.formType = "radiant_skill";
        metadata.formTypeIcon = "xeno_form_02";
        metadata.form("radiant").icon = "xeno_form_03";
        metadata.form("radiant").names.put("en_us", "Radiant Ascension");
        metadata.form("radiant").names.put("es_es", "Ascension Radiante");
        JsonObject snapshot = new JsonObject();
        snapshot.add("normal:human:xeno_custom",
                DmzFormMetadataRegistry.gson().toJsonTree(metadata));
        DmzFormMetadataRegistry.applySnapshot(snapshot.toString());

        assertEquals("Ascension Radiante", DmzFormMetadataRegistry.displayName(
                DmzFormKind.NORMAL, "human", "xeno_custom", "radiant", "es-ES"));
        assertEquals("Radiant Ascension", DmzFormMetadataRegistry.displayName(
                DmzFormKind.NORMAL, "human", "xeno_custom", "radiant", "fr_fr"));
        assertEquals("xenopixelsmod:textures/gui/radial/xeno_form_03.png",
                DmzFormMetadataRegistry.formIcon(
                        DmzFormKind.NORMAL, "human", "xeno_custom", "radiant").toString());
        assertEquals("xenopixelsmod:textures/gui/radial/xeno_form_02.png",
                DmzFormMetadataRegistry.formTypeIcon("radiant_skill").toString());
        assertEquals("xenopixelsmod:textures/gui/icons/xeno_form_02.png",
                DmzFormMetadataRegistry.formTypeSkillIcon("radiant_skill").toString());
    }

    @Test
    void iconResolutionFallsBackFromPerFormToFormTypeToNothing() {
        DmzFormMetadata metadata = new DmzFormMetadata();
        metadata.race = "human";
        metadata.group = "xeno_custom";
        metadata.formType = "radiant_skill";
        metadata.formTypeIcon = "xeno_form_02";
        metadata.form("radiant").icon = "";
        JsonObject snapshot = new JsonObject();
        snapshot.add("normal:human:xeno_custom",
                DmzFormMetadataRegistry.gson().toJsonTree(metadata));
        DmzFormMetadataRegistry.applySnapshot(snapshot.toString());

        // No per-form icon configured, so the radial falls through to DragonMineZ's own lookup.
        assertNull(DmzFormMetadataRegistry.formIcon(
                DmzFormKind.NORMAL, "human", "xeno_custom", "radiant"));
        assertEquals("xenopixelsmod:textures/gui/radial/xeno_form_02.png",
                DmzFormMetadataRegistry.formTypeIcon("radiant_skill").toString());
        assertNull(DmzFormMetadataRegistry.formTypeIcon("superforms"));
        assertNull(DmzFormMetadataRegistry.formIcon(
                DmzFormKind.NORMAL, "human", "unknown_group", "radiant"));
    }

    @Test
    void anExplicitTexturePathOrNamespaceIsKeptVerbatim() {
        DmzFormMetadata metadata = new DmzFormMetadata();
        metadata.race = "human";
        metadata.group = "xeno_custom";
        metadata.formType = "radiant_skill";
        metadata.form("radiant").icon = "dragonminez:textures/gui/icons/superforms.png";
        JsonObject snapshot = new JsonObject();
        snapshot.add("normal:human:xeno_custom",
                DmzFormMetadataRegistry.gson().toJsonTree(metadata));
        DmzFormMetadataRegistry.applySnapshot(snapshot.toString());

        assertEquals("dragonminez:textures/gui/icons/superforms.png",
                DmzFormMetadataRegistry.formIcon(
                        DmzFormKind.NORMAL, "human", "xeno_custom", "radiant").toString());
    }

    @Test
    void copyAsNewRewritesIdentityOntoAnUnprotectedGroup() {
        DmzFormDocument source = createdSource();
        DmzFormDocument copy = source.copyAsNewDraft();

        assertTrue(!copy.group().equals(source.group()), "duplicate must take a new group id");
        assertTrue(!copy.form().equals(source.form()), "duplicate must take a new form id");
        assertFalse(copy.created(), "a duplicate starts as an unsaved draft");
        assertNull(copy.identityError(), "the generated group must not be owned already");
    }

    @Test
    void copyAsNewCarriesEveryFormSettingAndMetadata() {
        DmzFormDocument source = createdSource();
        DmzFormDocument copy = source.copyAsNewDraft();

        JsonObject expected = JsonParser.parseString(source.formJson()).getAsJsonObject()
                .getAsJsonObject("forms").getAsJsonObject(source.form()).deepCopy();
        JsonObject actual = JsonParser.parseString(copy.formJson()).getAsJsonObject()
                .getAsJsonObject("forms").getAsJsonObject(copy.form()).deepCopy();
        expected.remove("name");
        actual.remove("name");
        assertEquals(expected, actual, "the duplicate must keep every DragonMineZ form setting");

        assertEquals(DmzFormKind.NORMAL, copy.kind());
        assertEquals("human", copy.race());
        assertEquals(copy.group(), copy.metadata().formType,
                "DragonMineZ derives the skill type from the group, so the copy needs its own");
        assertEquals(0L, copy.revision(), "a duplicate is a fresh draft");
        assertTrue(copy.metadata().customNpcTrainers.isEmpty(),
                "trainer associations belong to the source group, not to the copy");
        assertEquals("Radiant Ascension Copy",
                copy.metadata().form(copy.form()).names.get("en_us"));
        assertEquals("Xeno Src Copy", copy.metadata().groupNames.get("en_us"));
    }

    @Test
    void aDuplicateOfABundledGroupIsNotBundled() {
        DmzFormDocument source = DmzFormDocument.create(DmzFormKind.NORMAL, "saiyan");
        source.update("$group", "xenopixels_fan_ss");
        source.markCreated();
        assertTrue(DmzFormProtection.isBundled(
                DmzFormKind.NORMAL, "saiyan", source.group()));

        DmzFormDocument copy = source.copyAsNewDraft();
        assertFalse(DmzFormProtection.isBundled(DmzFormKind.NORMAL, copy.race(), copy.group()),
                "a generated group id is never bundled");
        assertNull(copy.identityError(),
                "and it is not owned yet, so the copy is writable without an override");
    }

    @Test
    void stackDuplicatesDropTheRace() {
        DmzFormDocument source = DmzFormDocument.create(DmzFormKind.STACK, "human");
        source.markCreated();
        DmzFormDocument copy = source.copyAsNewDraft();

        assertEquals(DmzFormKind.STACK, copy.kind());
        assertEquals("", copy.race(), "stack forms are race-agnostic");
    }

    @Test
    void repeatedDuplicatesNeverShareAnId() {
        DmzFormDocument source = createdSource();
        DmzFormDocument first = source.copyAsNewDraft();
        DmzFormDocument second = source.copyAsNewDraft();

        assertTrue(!first.group().equals(second.group()));
        assertTrue(!first.form().equals(second.form()));
        assertNull(first.identityError());
        assertNull(second.identityError());
    }

    /** A saved normal form owned by the studio, with names and a trainer association. */
    private static DmzFormDocument createdSource() {
        DmzFormDocument source = DmzFormDocument.create(DmzFormKind.NORMAL, "human");
        source.update("$group", "xeno_src");
        source.markCreated();
        source.revision(7L);
        source.metadata().groupNames.put("en_us", "Xeno Src");
        source.metadata().form(source.form()).names.put("en_us", "Radiant Ascension");
        source.metadata().customNpcTrainers.add(new DmzFormMetadata.TrainerRef());
        JsonObject snapshot = new JsonObject();
        snapshot.add("normal:human:xeno_src",
                DmzFormMetadataRegistry.gson().toJsonTree(source.metadata()));
        DmzFormMetadataRegistry.applySnapshot(snapshot.toString());
        return source;
    }
}
