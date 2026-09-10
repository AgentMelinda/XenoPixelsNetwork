package net.bullettrain.xenopixelsmod.dmz;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzAddonPatchLoaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void addsNewSkillsAndOfferingsWithoutReplacingExistingValues() {
        JsonObject original = json("""
                {"formSkills":["superforms"],"skillOfferings":{"goku":["fly"]},
                 "skills":{"existing":{"costs":[99]}}}
                """);
        JsonObject patch = json("""
                {"formSkillsAdd":["xenoaddon_forms"],
                 "skillOfferings":{"whis":["xenoaddon_forms"]},
                 "skillCosts":{"xenoaddon_forms":{"costs":[1,2],"allowedRaces":[]},
                                "existing":{"costs":[1]}},
                 "exclusiveFormSkills":{"xenoaddon_forms":["whis"]}}
                """);

        JsonObject merged = DmzAddonPatchLoader.merge(original,
                List.of(new DmzAddonPatchLoader.PatchDocument("test", patch)));

        assertEquals(99, merged.getAsJsonObject("skills").getAsJsonObject("existing")
                .getAsJsonArray("costs").get(0).getAsInt());
        assertTrue(merged.getAsJsonArray("formSkills").toString().contains("xenoaddon_forms"));
        assertTrue(merged.getAsJsonObject("skillOfferings").getAsJsonArray("whis")
                .toString().contains("xenoaddon_forms"));
        assertFalse(original.getAsJsonArray("formSkills").toString().contains("xenoaddon_forms"));
    }

    @Test
    void mergeIsIdempotentAgainstAlreadyPatchedConfiguration() {
        JsonObject patch = json("""
                {"formSkillsAdd":["xenoaddon_forms"],
                 "skillCosts":{"xenoaddon_forms":{"costs":[1],"allowedRaces":[]}}}
                """);
        DmzAddonPatchLoader.PatchDocument document = new DmzAddonPatchLoader.PatchDocument("test", patch);
        JsonObject once = DmzAddonPatchLoader.merge(new JsonObject(), List.of(document));
        JsonObject twice = DmzAddonPatchLoader.merge(once, List.of(document));
        assertEquals(once, twice);
    }

    @Test
    void rejectsDeletesForbiddenIdsAndMissingCosts() {
        assertThrows(IllegalArgumentException.class, () -> merge("""
                {"formSkillsRemove":["x"]}
                """));
        assertThrows(IllegalArgumentException.class, () -> merge("""
                {"formSkillsAdd":["addon_super_form"],
                 "skillCosts":{"addon_super_form":{"costs":[1]}}}
                """));
        assertThrows(IllegalArgumentException.class, () -> merge("""
                {"formSkillsAdd":["addon_form"]}
                """));
        assertThrows(IllegalArgumentException.class, () -> merge("""
                {"formSkillsAdd":["addon_form"],
                 "skillCosts":{"addon_form":{"allowedRaces":[]}}}
                """));
    }

    @Test
    void rejectsDuplicateOwnershipAcrossAddons() {
        JsonObject first = json("""
                {"formSkillsAdd":["addon_form"],
                 "skillCosts":{"addon_form":{"costs":[1]}}}
                """);
        JsonObject second = first.deepCopy();
        assertThrows(IllegalArgumentException.class, () -> DmzAddonPatchLoader.merge(new JsonObject(), List.of(
                new DmzAddonPatchLoader.PatchDocument("first", first),
                new DmzAddonPatchLoader.PatchDocument("second", second))));
    }

    @Test
    void exclusivityCannotTargetAnExistingSkill() {
        assertThrows(IllegalArgumentException.class, () -> merge("""
                {"exclusiveFormSkills":{"superforms":["whis"]}}
                """));
    }

    @Test
    void writesAtomicallyAndSecondApplicationDoesNothing() throws Exception {
        Path skills = temporaryDirectory.resolve("skills.json");
        Files.writeString(skills, "{}", StandardCharsets.UTF_8);
        JsonObject patch = json("""
                {"formSkillsAdd":["addon_form"],
                 "skillCosts":{"addon_form":{"costs":[1]}}}
                """);
        List<DmzAddonPatchLoader.PatchDocument> patches = List.of(
                new DmzAddonPatchLoader.PatchDocument("test", patch));

        assertTrue(DmzAddonPatchLoader.apply(skills, patches));
        assertFalse(DmzAddonPatchLoader.apply(skills, patches));
        assertTrue(Files.readString(skills).contains("addon_form"));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void backsUpEveryExistingFileBeforeMutation() throws Exception {
        Path root = temporaryDirectory.resolve("dragonminez");
        Path skills = root.resolve("skills.json");
        Path saiyan = root.resolve("races/saiyan/character.json");
        Files.createDirectories(saiyan.getParent());
        Files.writeString(skills, "skills-original", StandardCharsets.UTF_8);
        Files.writeString(saiyan, "saiyan-original", StandardCharsets.UTF_8);

        DmzContentBootstrap.backupAffectedFiles(root);

        Path backupBase;
        try (var backups = Files.list(root.resolve(".xenopixels-backups"))) {
            backupBase = backups.findFirst().orElseThrow();
        }
        assertEquals("skills-original", Files.readString(backupBase.resolve("skills.json")));
        assertEquals("saiyan-original", Files.readString(
                backupBase.resolve("races/saiyan/character.json")));
    }

    private static JsonObject merge(String patch) {
        return DmzAddonPatchLoader.merge(new JsonObject(), List.of(
                new DmzAddonPatchLoader.PatchDocument("test", json(patch))));
    }

    private static JsonObject json(String source) {
        return JsonParser.parseString(source).getAsJsonObject();
    }
}
