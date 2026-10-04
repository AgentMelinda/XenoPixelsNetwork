package net.bullettrain.xenopixelsmod.npc.spawn;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decisions a natural spawn rule makes, with no server and no level.
 *
 * <p>Everything that could go wrong with ambient spawning is a pure decision — does this biome admit
 * this rule, does this roll pick this entry, is this payload worth storing at all — so all of it is
 * provable here. What is not provable headlessly is the placement itself, and this file does not
 * claim it is: see {@link NpcNaturalSpawnWiringTest} for the guards on the code that touches a level.
 */
class NpcNaturalSpawnTest {

    private static NpcNaturalSpawn rule(String id, int weight, String cloneId, String... biomes) {
        return new NpcNaturalSpawn(id, id, List.of(biomes), weight, 2, cloneId,
                NpcNaturalSpawn.TIME_ANY);
    }

    @Test
    void whatIsSavedIsWhatIsLoaded() {
        NpcNaturalSpawn original = new NpcNaturalSpawn("plains_wanderer", "Plains Wanderer",
                List.of("minecraft:plains", "minecraft:sunflower_plains"), 25, 4, "guide",
                NpcNaturalSpawn.TIME_NIGHT);

        NpcNaturalSpawn loaded = NpcNaturalSpawn.load("plains_wanderer", original.save());

        assertEquals(original, loaded);
    }

    @Test
    void aRuleWithNoBiomesRoundTripsAsNoBiomes() {
        // "Anywhere" has to survive the store. If an empty list were written as an absent key and
        // read back as something else, the editor would show a rule as configured for biomes it
        // never had.
        NpcNaturalSpawn anywhere = new NpcNaturalSpawn("roadside", "Roadside", List.of(), 10, 1,
                "traveller", NpcNaturalSpawn.TIME_ANY);

        assertEquals(anywhere, NpcNaturalSpawn.load("roadside", anywhere.save()));
        assertTrue(anywhere.save().getList("Biomes", 9).isEmpty());
    }

    @Test
    void everyMemberIsWrittenSoAnExplicitValueCannotDrift() {
        CompoundTag tag = rule("x", 1, "clone").save();
        for (String key : List.of("Name", "Biomes", "Weight", "CloneTab", "CloneId", "Time")) {
            assertTrue(tag.contains(key), "save() must write " + key);
        }
    }

    @Test
    void aStoredRuleMissingEverythingFallsBackRatherThanVanishing() {
        // A file already on disk is not something a player can fix from a chat message, so reading
        // is forgiving even though writing is strict.
        NpcNaturalSpawn loaded = NpcNaturalSpawn.load("half_written", new CompoundTag());

        assertEquals("half_written", loaded.title());
        assertEquals(NpcNaturalSpawn.DEFAULT_WEIGHT, loaded.weight());
        assertEquals(NpcNaturalSpawn.TIME_ANY, loaded.time());
        assertEquals(net.bullettrain.xenopixelsmod.npc.store.XenoNpcClones.MIN_TAB,
                loaded.cloneTab());
        assertFalse(loaded.usable());
    }

    @Test
    void weightIsClampedIntoTheRangeTheStepperOffers() {
        assertEquals(NpcNaturalSpawn.MIN_WEIGHT, rule("a", 0, "c").weight());
        assertEquals(NpcNaturalSpawn.MIN_WEIGHT, rule("a", -50, "c").weight());
        assertEquals(NpcNaturalSpawn.MAX_WEIGHT, rule("a", 99999, "c").weight());
        assertEquals(37, rule("a", 37, "c").weight());
    }

    @Test
    void cloneTabIsClampedIntoTheRangeTheCloneLibraryHas() {
        assertEquals(net.bullettrain.xenopixelsmod.npc.store.XenoNpcClones.MIN_TAB,
                new NpcNaturalSpawn("a", "a", List.of(), 10, 0, "c", "any").cloneTab());
        assertEquals(net.bullettrain.xenopixelsmod.npc.store.XenoNpcClones.MAX_TAB,
                new NpcNaturalSpawn("a", "a", List.of(), 10, 99, "c", "any").cloneTab());
    }

    @Test
    void biomesAreLowerCasedDeduplicatedAndBounded() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            many.add("MINECRAFT:PLAINS_" + i);
        }
        many.add("minecraft:plains_1");

        NpcNaturalSpawn cleaned = new NpcNaturalSpawn("a", "a", many, 10, 1, "c", "any");

        assertEquals(NpcNaturalSpawn.MAX_BIOMES, cleaned.biomes().size());
        assertTrue(cleaned.biomes().contains("minecraft:plains_1"));
        assertEquals(cleaned.biomes().size(), List.copyOf(cleaned.biomes()).stream().distinct().count());
    }

    @Test
    void anUnknownTimeBecomesAnyRatherThanSilentlyNeverFiring() {
        assertEquals(NpcNaturalSpawn.TIME_ANY,
                new NpcNaturalSpawn("a", "a", List.of(), 10, 1, "c", "dusk").time());
        assertEquals(NpcNaturalSpawn.TIME_DAY,
                new NpcNaturalSpawn("a", "a", List.of(), 10, 1, "c", "DAY").time());
    }

    @Test
    void noBiomesMeansAnywhereAndNotNowhere() {
        NpcNaturalSpawn anywhere = rule("a", 10, "c");
        assertTrue(anywhere.matchesBiome("minecraft:soul_sand_valley"));
        assertTrue(anywhere.matchesBiome(null));

        NpcNaturalSpawn picked = rule("a", 10, "c", "minecraft:plains");
        assertTrue(picked.matchesBiome("minecraft:plains"));
        assertTrue(picked.matchesBiome("MINECRAFT:PLAINS"), "stored lower case, matched lower case");
        assertFalse(picked.matchesBiome("minecraft:forest"));
    }

    @Test
    void timeRestrictionsAdmitOnlyWhatTheySay() {
        assertTrue(new NpcNaturalSpawn("a", "a", List.of(), 10, 1, "c",
                NpcNaturalSpawn.TIME_DAY).matchesTime(true));
        assertFalse(new NpcNaturalSpawn("a", "a", List.of(), 10, 1, "c",
                NpcNaturalSpawn.TIME_DAY).matchesTime(false));
        assertTrue(new NpcNaturalSpawn("a", "a", List.of(), 10, 1, "c",
                NpcNaturalSpawn.TIME_NIGHT).matchesTime(false));
        assertTrue(new NpcNaturalSpawn("a", "a", List.of(), 10, 1, "c",
                NpcNaturalSpawn.TIME_ANY).matchesTime(false));
    }

    @Test
    void theWeightedPickLandsOnTheRuleTheRollBelongsTo() {
        List<NpcNaturalSpawn> rules = List.of(rule("rare", 1, "c"), rule("common", 9, "c"));

        assertEquals("rare", NpcNaturalSpawn.pick(rules, 0).id());
        assertEquals("common", NpcNaturalSpawn.pick(rules, 1).id());
        assertEquals("common", NpcNaturalSpawn.pick(rules, 9).id());
        assertEquals(10, NpcNaturalSpawn.totalWeight(rules));
    }

    @Test
    void anOutOfRangeRollStillPicksSomethingRatherThanThrowingMidTick() {
        List<NpcNaturalSpawn> rules = List.of(rule("a", 3, "c"), rule("b", 4, "c"));

        assertEquals("a", NpcNaturalSpawn.pick(rules, -7).id());
        assertEquals("b", NpcNaturalSpawn.pick(rules, 999).id());
    }

    @Test
    void nothingToChooseBetweenIsAnsweredWithNullNotAnEmptyListCrash() {
        assertEquals(0, NpcNaturalSpawn.totalWeight(List.of()));
        assertNull(NpcNaturalSpawn.pick(List.of(), 0));
        assertNull(NpcNaturalSpawn.pick(null, 0));
    }

    @Test
    void eligibilityDropsRulesThatCouldNotPlaceAnything() {
        List<NpcNaturalSpawn> rules = List.of(
                new NpcNaturalSpawn("ghost", "Ghost", List.of(), 10, 1, "", "any"),
                rule("plains_only", 5, "c", "minecraft:plains"),
                rule("anywhere", 5, "c"));

        List<NpcNaturalSpawn> eligible = NpcNaturalSpawn.eligible(rules, "minecraft:plains", true);

        assertEquals(List.of("plains_only", "anywhere"),
                eligible.stream().map(NpcNaturalSpawn::id).toList());
        assertTrue(NpcNaturalSpawn.eligible(rules, "minecraft:basalt_deltas", true).stream()
                .allMatch(r -> r.id().equals("anywhere")));
    }

    @Test
    void aPayloadThatCouldNeverSpawnAnythingIsRefusedWithAReason() {
        assertNotNull(NpcNaturalSpawn.rejectPayload(null));

        CompoundTag noClone = new CompoundTag();
        noClone.putInt("Weight", 10);
        assertTrue(NpcNaturalSpawn.rejectPayload(noClone).contains("clone"));

        CompoundTag badTab = new CompoundTag();
        badTab.putString("CloneId", "guide");
        badTab.putInt("CloneTab", 42);
        assertTrue(NpcNaturalSpawn.rejectPayload(badTab).contains("tab"));

        CompoundTag badWeight = new CompoundTag();
        badWeight.putString("CloneId", "guide");
        badWeight.putInt("Weight", 0);
        assertTrue(NpcNaturalSpawn.rejectPayload(badWeight).contains("weight"));

        CompoundTag badTime = new CompoundTag();
        badTime.putString("CloneId", "guide");
        badTime.putString("Time", "midday");
        assertTrue(NpcNaturalSpawn.rejectPayload(badTime).contains("time"));

        CompoundTag namespaceless = new CompoundTag();
        namespaceless.putString("CloneId", "guide");
        ListTag biomes = new ListTag();
        biomes.add(StringTag.valueOf("plains"));
        namespaceless.put("Biomes", biomes);
        assertTrue(NpcNaturalSpawn.rejectPayload(namespaceless).contains("namespace"));
    }

    @Test
    void aWellFormedPayloadIsAcceptedAndWhatTheEditorWritesIsWellFormed() {
        NpcNaturalSpawn rule = new NpcNaturalSpawn("outpost", "Outpost",
                List.of("minecraft:plains"), 12, 3, "guard", NpcNaturalSpawn.TIME_DAY);

        assertNull(NpcNaturalSpawn.rejectPayload(rule.save()));
    }

    @Test
    void aRefusedCloneIdIsRefusedAsASpawnRuleToo() {
        CompoundTag tag = new CompoundTag();
        tag.putString("CloneId", "../escape");
        assertNotNull(NpcNaturalSpawn.rejectPayload(tag));
    }

    @Test
    void describeNamesTheCloneTheWeightAndTheTime() {
        String line = new NpcNaturalSpawn("outpost", "Outpost", List.of("minecraft:plains"), 12, 3,
                "guard", NpcNaturalSpawn.TIME_DAY).describe();

        assertTrue(line.contains("guard"));
        assertTrue(line.contains("12"));
        assertTrue(line.contains("day"));
        assertTrue(line.contains("1 biome"));
    }

    @Test
    void aBlankDraftHasSomethingToShowButCannotBeStoredYet() {
        NpcNaturalSpawn draft = NpcNaturalSpawn.blank("new_rule");

        assertEquals("new_rule", draft.title());
        assertFalse(draft.usable());
        assertNotNull(NpcNaturalSpawn.rejectPayload(draft.save()));
    }
}
