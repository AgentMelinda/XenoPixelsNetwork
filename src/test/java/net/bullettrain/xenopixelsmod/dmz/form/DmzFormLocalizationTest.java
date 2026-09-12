package net.bullettrain.xenopixelsmod.dmz.form;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Guards the translation keys and the locale fallback chain the DragonMineZ screens rely on.
 *
 * <p>The key shapes are copied from dragonminez-2.1.3: {@code FormSelectNode#label} builds
 * {@code race.dragonminez.<race>.form.<group>.<form>} and
 * {@code race.dragonminez.stack.form.<group>.<form>}, while {@code SkillsMenuScreen} and
 * {@code QuestTreeScreen} build the matching {@code .group.} keys.
 */
class DmzFormLocalizationTest {
    @AfterEach
    void clearRegistry() {
        DmzFormMetadataRegistry.applySnapshot("{}");
    }

    @Test
    void keyShapesMatchDragonMineZ() {
        assertEquals("race.dragonminez.human.form.xeno_custom.radiant",
                DmzFormMetadataRegistry.formKey(DmzFormKind.NORMAL, "human", "xeno_custom", "radiant"));
        assertEquals("race.dragonminez.stack.form.xeno_stack.surge",
                DmzFormMetadataRegistry.formKey(DmzFormKind.STACK, "", "xeno_stack", "surge"));
        assertEquals("race.dragonminez.human.group.xeno_custom",
                DmzFormMetadataRegistry.groupKey(DmzFormKind.NORMAL, "human", "xeno_custom"));
        assertEquals("race.dragonminez.stack.group.xeno_stack",
                DmzFormMetadataRegistry.groupKey(DmzFormKind.STACK, "", "xeno_stack"));
    }

    @Test
    void labelsResolveCurrentLocaleThenEnglishThenNothing() {
        install();

        assertEquals("Ascension Radiante", DmzFormMetadataRegistry.translate(
                "race.dragonminez.human.form.xeno_custom.radiant", "es_es"));
        assertEquals("Ascension Radiante", DmzFormMetadataRegistry.translate(
                "race.dragonminez.human.form.xeno_custom.radiant", "es-ES"));
        assertEquals("Radiant Ascension", DmzFormMetadataRegistry.translate(
                "race.dragonminez.human.form.xeno_custom.radiant", "fr_fr"));
        assertEquals("Radiant Line", DmzFormMetadataRegistry.translate(
                "race.dragonminez.human.group.xeno_custom", "fr_fr"));
        assertEquals("Surge", DmzFormMetadataRegistry.translate(
                "race.dragonminez.stack.form.xeno_stack.surge", "en_us"));
    }

    @Test
    void everyKeyThisModDoesNotOwnFallsThroughToVanilla() {
        install();
        assertNull(DmzFormMetadataRegistry.translate(
                "race.dragonminez.human.form.superforms.ssj1", "en_us"));
        assertNull(DmzFormMetadataRegistry.translate(
                "race.dragonminez.saiyan.form.xeno_custom.radiant", "en_us"));
        assertNull(DmzFormMetadataRegistry.translate("item.minecraft.diamond", "en_us"));
        assertNull(DmzFormMetadataRegistry.translate(null, "en_us"));
        assertNull(DmzFormMetadataRegistry.translate("", "en_us"));
    }

    @Test
    void clearingTheSnapshotRetiresTheKeysItInstalled() {
        install();
        DmzFormMetadataRegistry.applySnapshot("{}");
        assertNull(DmzFormMetadataRegistry.translate(
                "race.dragonminez.human.form.xeno_custom.radiant", "en_us"));
    }

    @Test
    void metadataFilesLandInTheirVersionedTreeAndCannotEscapeIt(@TempDir Path root) {
        assertEquals(root.resolve("normal").resolve("human").resolve("xeno_custom.json")
                        .toAbsolutePath().normalize(),
                DmzFormMetadataRegistry.metadataPath(root, DmzFormKind.NORMAL, "human", "xeno_custom"));
        assertEquals(root.resolve("stack").resolve("xeno_stack.json").toAbsolutePath().normalize(),
                DmzFormMetadataRegistry.metadataPath(root, DmzFormKind.STACK, "", "xeno_stack"));

        assertThrows(IllegalArgumentException.class, () -> DmzFormMetadataRegistry.metadataPath(
                root, DmzFormKind.NORMAL, "../../etc", "xeno_custom"));
        assertThrows(IllegalArgumentException.class, () -> DmzFormMetadataRegistry.metadataPath(
                root, DmzFormKind.NORMAL, "human", "../../escaped"));
        assertThrows(IllegalArgumentException.class, () -> DmzFormMetadataRegistry.metadataPath(
                root, DmzFormKind.STACK, "", "../escaped"));
    }

    private static void install() {
        DmzFormMetadata normal = new DmzFormMetadata();
        normal.race = "human";
        normal.group = "xeno_custom";
        normal.formType = "radiant_skill";
        normal.groupNames.put("en_us", "Radiant Line");
        normal.form("radiant").names.put("en_us", "Radiant Ascension");
        normal.form("radiant").names.put("es_es", "Ascension Radiante");

        DmzFormMetadata stack = new DmzFormMetadata();
        stack.race = "";
        stack.group = "xeno_stack";
        stack.formType = "radiant_stack";
        stack.form("surge").names.put("en_us", "Surge");

        JsonObject snapshot = new JsonObject();
        snapshot.add("normal:human:xeno_custom", DmzFormMetadataRegistry.gson().toJsonTree(normal));
        snapshot.add("stack::xeno_stack", DmzFormMetadataRegistry.gson().toJsonTree(stack));
        DmzFormMetadataRegistry.applySnapshot(snapshot.toString());
    }
}
