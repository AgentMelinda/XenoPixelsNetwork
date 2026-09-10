package net.bullettrain.xenopixelsmod.compat.npc.clone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the converter against real clone files saved by both mods, rather than hand-built tags.
 *
 * <p>The fixtures under {@code src/test/resources/clones} are genuine saves — two CustomNPCs NPCs
 * and one from My NPCs — so the schema being converted is the schema that actually ships, not one
 * inferred from documentation.
 */
class NpcCloneConverterTest {

    private static CompoundTag fixture(String name) {
        try (InputStream in = NpcCloneConverterTest.class.getResourceAsStream("/clones/" + name)) {
            assertNotNull(in, "missing fixture: " + name);
            return TagParser.parseTag(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException | com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("could not read " + name, e);
        }
    }

    private static CompoundTag goku() {
        return fixture("goku_customnpcs.snbt");
    }

    private static CompoundTag mable() {
        return fixture("mable_mynpcs.snbt");
    }

    /** Every string anywhere in the tree, so a stray namespace cannot hide in a nested list. */
    private static List<String> allStrings(Tag tag) {
        List<String> out = new ArrayList<>();
        collect(tag, out);
        return out;
    }

    private static void collect(Tag tag, List<String> out) {
        if (tag instanceof StringTag) {
            out.add(tag.getAsString());
        } else if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                collect(compound.get(key), out);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                collect(child, out);
            }
        }
    }

    @Test
    void theFixturesAreWhatWeThinkTheyAre() {
        assertTrue(NpcCloneConverter.isCustomNpcsClone(goku()));
        assertTrue(NpcCloneConverter.isMyNpcsClone(mable()));
        assertFalse(NpcCloneConverter.isCustomNpcsClone(mable()));
    }

    @Test
    void theEntityIdIsRewritten() {
        assertEquals(NpcCloneConverter.MYNPCS_ID, NpcCloneConverter.convert(goku()).getString("id"));
    }

    @Test
    void noCustomNpcsNamespaceSurvivesAnywhere() {
        for (String value : allStrings(NpcCloneConverter.convert(goku()))) {
            assertFalse(value.startsWith("customnpcs:"), "left behind: " + value);
        }
    }

    @Test
    void otherModsNamespacesAreLeftAlone() {
        // The Vegeta clone carries a DragonMineZ item in its inventory and vanilla attribute ids.
        // A blunt string replace over the file would have been happy to mangle these.
        List<String> values = allStrings(NpcCloneConverter.convert(fixture("vegeta_customnpcs.snbt")));
        assertTrue(values.contains("dragonminez:senzu_bean"), "the DMZ item should be untouched");
        assertTrue(values.stream().anyMatch(v -> v.startsWith("minecraft:generic.")),
                "vanilla attribute ids should be untouched");
    }

    @Test
    void theSkinTextureFollowsTheNamespace() {
        assertTrue(allStrings(NpcCloneConverter.convert(goku())).stream()
                        .anyMatch(v -> v.equals("mynpcs:textures/entity/humanmale/steve.png")),
                "the skin path should be re-namespaced, not just the entity id");
    }

    @Test
    void keysMyNpcsCannotReadAreDropped() {
        CompoundTag converted = NpcCloneConverter.convert(goku());
        for (String key : List.of("CompanionOwnerName", "CompanionStage", "CompanionInventory",
                "foodLevel", "foodTickTimer", "neoforge:attachments")) {
            assertFalse(converted.contains(key), "should have been dropped: " + key);
        }
    }

    @Test
    void keysMyNpcsExpectsAreSeeded() {
        CompoundTag converted = NpcCloneConverter.convert(goku());
        for (String key : List.of("CNPC_persistantData", "FtbQuestCompleteEnabled",
                "FtbQuestCompleteId", "FtbQuestJobRequired", "FtbQuestRequiredEnabled",
                "FtbQuestRequiredId", "FtbQuestRoleRequired")) {
            assertTrue(converted.contains(key), "should have been seeded: " + key);
        }
    }

    @Test
    void theSeededDefaultsMatchANativeClone() {
        CompoundTag converted = NpcCloneConverter.convert(goku());
        CompoundTag native_ = mable();
        for (String key : List.of("FtbQuestCompleteEnabled", "FtbQuestJobRequired",
                "FtbQuestRequiredEnabled", "FtbQuestRoleRequired")) {
            assertEquals(native_.getBoolean(key), converted.getBoolean(key), key);
        }
        assertEquals(native_.getString("FtbQuestCompleteId"), converted.getString("FtbQuestCompleteId"));
        assertEquals(native_.getString("FtbQuestRequiredId"), converted.getString("FtbQuestRequiredId"));
    }

    @Test
    void everythingElseSurvives() {
        // The point of the converter is that it is small: the NPC's own configuration has to come
        // through untouched or the clone is not the same NPC.
        CompoundTag before = goku();
        CompoundTag after = NpcCloneConverter.convert(before);
        for (String key : before.getAllKeys()) {
            if (List.of("CompanionAge", "CompanionCanAge", "CompanionDefendOwner", "CompanionExp",
                    "CompanionHasInv", "CompanionID", "CompanionInventory", "CompanionJob",
                    "CompanionOwner", "CompanionOwnerName", "CompanionStage", "CompanionTalents",
                    "foodExhaustionLevel", "foodLevel", "foodSaturationLevel", "foodTickTimer",
                    "neoforge:attachments", "id").contains(key)) {
                continue;
            }
            assertTrue(after.contains(key), "lost in conversion: " + key);
        }
    }

    @Test
    void aNativeCloneIsUntouched() {
        CompoundTag before = mable();
        assertEquals(before, NpcCloneConverter.convert(before));
    }

    @Test
    void convertingTwiceChangesNothingTheSecondTime() {
        CompoundTag once = NpcCloneConverter.convert(goku());
        assertEquals(once, NpcCloneConverter.convert(once));
    }

    @Test
    void theCallersTagIsNotModified() {
        CompoundTag original = goku();
        CompoundTag untouched = goku();
        NpcCloneConverter.convert(original);
        assertEquals(untouched, original, "convert must not mutate its input");
    }

    @Test
    void droppedKeysAreReportable() {
        List<String> dropped = NpcCloneConverter.droppedKeys(goku());
        assertTrue(dropped.contains("CompanionOwnerName"));
        assertTrue(dropped.contains("foodLevel"));
        assertTrue(NpcCloneConverter.droppedKeys(mable()).isEmpty(),
                "a native clone has nothing to drop");
    }
}
