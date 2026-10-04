package net.bullettrain.xenopixelsmod.dmz.form;

import com.dragonminez.common.config.FormConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-D6a — race form-group maker IO. KD15: existing races only; no new race folders.
 * Saves remain {@code FormEditorNetwork.save} → {@link DmzFormEditorService#save}.
 */
class RaceFormGroupMakerIoTest {
    private static final String GODS_RESOURCE =
            "/data/xenopixelsmod/dmz/races/saiyan/forms/xenopixels_gods_forms.json";

    @AfterEach
    void clearMetadataRegistry() {
        DmzFormMetadataRegistry.applySnapshot("{}");
    }

    @Test
    void rejectsUnknownRaceId() {
        var result = RaceFormGroupGuard.validateRace("not_a_real_race_id");
        assertFalse(result.ok());
    }

    @Test
    void acceptsKnownSaiyanGodsGroupName() {
        var result = RaceFormGroupGuard.validateGroup("saiyan", "xenopixels_gods_forms");
        assertTrue(result.ok());
    }

    @Test
    void rejectsUnknownGroupOnKnownRace() {
        var result = RaceFormGroupGuard.validateGroup("saiyan", "not_an_installed_group");
        assertFalse(result.ok());
    }

    @Test
    void doesNotCreateUnknownRaceFolder() throws Exception {
        Path racesRoot = Path.of("src/main/resources/data/xenopixelsmod/dmz/races");
        Path unknown = racesRoot.resolve("not_a_real_race_id");
        assertFalse(Files.exists(unknown), "guard must not create races/not_a_real_race_id/");
        RaceFormGroupGuard.validateRace("not_a_real_race_id");
        assertFalse(Files.exists(unknown), "validateRace must not create a race folder");
    }

    @Test
    void godsFormsJsonRoundTripsThroughFormConfig() throws Exception {
        String json;
        try (InputStream in = RaceFormGroupMakerIoTest.class.getResourceAsStream(GODS_RESOURCE)) {
            assertNotNull(in, "missing classpath resource " + GODS_RESOURCE);
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        FormConfig config = DmzFormMetadataRegistry.gson().fromJson(json, FormConfig.class);
        assertNotNull(config);
        assertEquals("xenopixels_gods_forms", config.getGroupName());
        assertEquals("xenopixels_divinity", config.getFormType());
        assertTrue(config.getForms().containsKey("ssg"));

        String again = DmzFormMetadataRegistry.gson().toJson(config);
        FormConfig round = DmzFormMetadataRegistry.gson().fromJson(again, FormConfig.class);
        assertEquals(config.getGroupName(), round.getGroupName());
        assertEquals(config.getFormType(), round.getFormType());
        assertEquals(config.getForms().keySet(), round.getForms().keySet());

        JsonObject root = JsonParser.parseString(again).getAsJsonObject();
        assertEquals("xenopixels_gods_forms", root.get("groupName").getAsString());
        assertTrue(root.getAsJsonObject("forms").has("ssg"));
    }

    @Test
    void editorSaveApiRemainsFormEditorNetworkSave() throws Exception {
        // Contract for Task 8 / maker: maker IO validates identity only; persistence is still
        // FormEditorNetwork.save → DmzFormEditorService.save (bootstrap backup unchanged).
        var method = Class.forName("net.bullettrain.xenopixelsmod.network.form.FormEditorNetwork")
                .getMethod("save",
                        DmzFormKind.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        long.class,
                        String.class);
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
    }
}
