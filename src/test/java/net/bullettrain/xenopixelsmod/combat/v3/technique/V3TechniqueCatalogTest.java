package net.bullettrain.xenopixelsmod.combat.v3.technique;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import net.bullettrain.xenopixelsmod.combat.v3.anim.V3AnimationCatalog;

class V3TechniqueCatalogTest {
    private static String one(String body) {
        return "{\"schema\":1,\"techniques\":[" + body + "]}";
    }

    private static String withAlias(String alias, String target, String body) {
        return "{\"schema\":2,\"compatibilityAliases\":{\"" + alias + "\":\"" + target
                + "\"},\"techniques\":[" + body + "]}";
    }

    private static String technique(String id, String kiTechnique, String beats, String cost, String duration) {
        return "{\"id\":\"" + id + "\",\"name\":\"Test\",\"type\":\"beam\",\"durationTicks\":" + duration
                + ",\"kiCost\":" + cost + ",\"cooldownTicks\":100,\"kiTechnique\":" + kiTechnique
                + ",\"animationStatus\":\"archetype_placeholder\",\"sourceStartsMs\":[1000],\"beats\":[" + beats + "]}";
    }

    private static final String END = "{\"kind\":\"END\",\"tick\":20,\"duration\":0,\"payload\":\"\",\"value\":0}";
    private static final String CHARGE = "{\"kind\":\"KI_CHARGE\",\"tick\":2,\"duration\":0,\"payload\":\"\",\"value\":0},";
    private static final String RELEASE = "{\"kind\":\"KI_RELEASE\",\"tick\":10,\"duration\":0,\"payload\":\"\",\"value\":0},";

    private static List<V3TechniqueDefinition> parse(String json) {
        return V3TechniqueCatalog.parse(new StringReader(json));
    }

    @Test void shippedCatalogLoadsWithUniqueOwnedIdsAndOrderedBeats() {
        List<V3TechniqueDefinition> all = V3TechniqueCatalog.loadBundled();
        assertTrue(all.size() >= 300, "observed attack occurrences must not be collapsed by label");
        Set<String> ids = new HashSet<>();
        for (V3TechniqueDefinition technique : all) {
            assertTrue(technique.id().startsWith("xenopixelsmod:bt3_"), technique.id());
            assertTrue(ids.add(technique.id()), "duplicate " + technique.id());
            int last = -1;
            for (V3Beat beat : technique.beats()) {
                assertTrue(beat.tick() >= last, technique.id() + " beats out of order");
                last = beat.tick();
            }
            assertEquals(V3Beat.Kind.END, technique.beats().getLast().kind(), technique.id());
            assertTrue(technique.durationTicks() >= last);
            boolean fires = technique.beats().stream().anyMatch(b -> b.kind() == V3Beat.Kind.KI_RELEASE);
            assertEquals(fires, technique.kiTechnique() != null, technique.id() + " projectile mapping");
            assertEquals(1, technique.sourceStartsMs().size(), technique.id() + " must retain one occurrence interval");
        }
    }

    @Test void repeatedLabelsKeepDistinctOccurrenceIds() {
        List<V3TechniqueDefinition> kamehamehas = V3TechniqueCatalog.loadBundled().stream()
                .filter(technique -> "Kamehameha".equals(technique.sourceLabel()))
                .toList();
        assertTrue(kamehamehas.size() > 3);
        assertEquals(kamehamehas.size(), kamehamehas.stream().map(V3TechniqueDefinition::id).distinct().count());
        assertTrue(kamehamehas.stream().map(V3TechniqueDefinition::id)
                .anyMatch("xenopixelsmod:bt3_early_kid_goku_kamehameha_15"::equals));
    }

    @Test void placeholdersAndReferenceTimedDraftsNeverCountAsFinishedChoreography() {
        List<V3TechniqueDefinition> all = V3TechniqueCatalog.loadBundled();
        long finished = all.stream().filter(V3TechniqueDefinition::choreographyComplete).count();
        long placeholders = all.stream().filter(t -> "archetype_placeholder".equals(t.animationStatus())).count();
        long drafts = all.stream().filter(t -> "reference_timed_unverified".equals(t.animationStatus())).count();
        long authored = all.stream().filter(t -> "authored_gameplay_unverified".equals(t.animationStatus())).count();
        assertEquals(all.size(), placeholders + drafts + authored,
                "every occurrence stays in an unfinished evidence class until compared");
        assertTrue(drafts > 0, "reference-timed drafts must remain explicitly unverified");
        assertEquals(0, finished, "no entry has had its own clip compared against the reference yet");
    }

    private static V3TechniqueDefinition posed(String clip, int start, int end) {
        return new V3TechniqueDefinition("xenopixelsmod:bt3_pose_validation", "Test", "Test", "melee", end,
                0, 0, null, "reference_timed_unverified", List.of(0L), List.of(
                new V3Beat(V3Beat.Kind.POSE, start, 0, clip, 0),
                new V3Beat(V3Beat.Kind.END, end, 0, "", 0)));
    }

    @Test void missingOccurrencePoseIsRefusedWithoutRejectingLegacyOrStudioNames() {
        assertThrows(IllegalArgumentException.class,
                () -> posed(V3AnimationCatalog.PREFIX + "missing_occurrence_fire", 0, 100));
        assertDoesNotThrow(() -> posed("combat.one_handed_punch_right", 0, 20));
        assertDoesNotThrow(() -> posed("combat.xeno_studio_operator_clip", 0, 20));
        assertDoesNotThrow(() -> posed("combat.xeno_jab_right_v3", 0, 20));
    }

    @Test void occurrencePoseMustFitFromItsStartThroughTheTechniqueEnd() {
        assertFalse(V3AnimationCatalog.allNames().isEmpty(), "test needs an authored occurrence clip");
        String clip = V3AnimationCatalog.allNames().iterator().next();
        int ticks = V3AnimationCatalog.durationTicks(clip);
        int start = 7;
        assertDoesNotThrow(() -> posed(clip, start, start + ticks));
        assertThrows(IllegalArgumentException.class, () -> posed(clip, start, start + ticks - 1));
    }

    private static V3TechniqueDefinition withHold(List<V3Beat> beats) {
        return new V3TechniqueDefinition("xenopixelsmod:bt3_hold_validation", "Test", "Test", "beam", 100,
                0, 0, "kamehameha", "reference_timed_unverified", List.of(0L), beats);
    }

    @Test void kiWindupGateMustOccurOnceInsideAnEnergyChargeReleasePair() {
        var charge = new V3Beat(V3Beat.Kind.KI_CHARGE, 1, 0, "", 0);
        var hold = new V3Beat(V3Beat.Kind.KI_HOLD, 2, 0, "", 0);
        var secondHold = new V3Beat(V3Beat.Kind.KI_HOLD, 3, 0, "", 0);
        var release = new V3Beat(V3Beat.Kind.KI_RELEASE, 5, 0, "", 0);
        var end = new V3Beat(V3Beat.Kind.END, 100, 0, "", 0);
        assertDoesNotThrow(() -> withHold(List.of(charge, hold, release, end)));
        assertDoesNotThrow(() -> withHold(List.of(charge, release, end)), "legacy release gate is unchanged");
        assertThrows(IllegalArgumentException.class, () -> withHold(List.of(hold, release, end)), "no charge");
        assertThrows(IllegalArgumentException.class,
                () -> withHold(List.of(charge, hold, secondHold, release, end)), "multiple gates");
        assertThrows(IllegalArgumentException.class,
                () -> withHold(List.of(charge, release, new V3Beat(V3Beat.Kind.KI_HOLD, 6, 0, "", 0), end)),
                "gate after release");
        assertThrows(IllegalArgumentException.class, () -> withHold(List.of(charge, hold, end)), "no release");
        assertThrows(IllegalArgumentException.class, () -> new V3TechniqueDefinition(
                "xenopixelsmod:bt3_physical_hold", "Test", "Test", "melee", 100,
                0, 0, null, "archetype_placeholder", List.of(0L), List.of(hold, end)), "physical gate");
    }

    @Test void authoredHoldGateCannotFollowAnEarlierEnergyReleaseInTheSameCast() {
        var chargeFirst = new V3Beat(V3Beat.Kind.KI_CHARGE, 1, 0, "", 0);
        var releaseFirst = new V3Beat(V3Beat.Kind.KI_RELEASE, 2, 0, "", 0);
        var chargeSecond = new V3Beat(V3Beat.Kind.KI_CHARGE, 3, 0, "", 0);
        var holdSecond = new V3Beat(V3Beat.Kind.KI_HOLD, 4, 0, "", 0);
        var releaseSecond = new V3Beat(V3Beat.Kind.KI_RELEASE, 5, 0, "", 0);
        var end = new V3Beat(V3Beat.Kind.END, 100, 0, "", 0);
        assertThrows(IllegalArgumentException.class,
                () -> withHold(List.of(chargeFirst, releaseFirst, chargeSecond, holdSecond, releaseSecond, end)),
                "a global hold gate must stop before the cast's first release");
        assertDoesNotThrow(() -> withHold(List.of(chargeFirst, releaseFirst, chargeSecond, releaseSecond, end)),
                "legacy multi-release timelines keep their original admission");
    }

    @Test void validTechniqueParses() {
        var parsed = parse(one(technique("xenopixelsmod:bt3_x", "\"kamehameha\"", CHARGE + RELEASE + END, "40", "20")));
        assertEquals(1, parsed.size());
        assertEquals("kamehameha", parsed.getFirst().kiTechnique());
        assertEquals(3, parsed.getFirst().beats().size());
    }

    @Test void unknownBeatKindIsRefused() {
        String bad = "{\"kind\":\"RUN_SCRIPT\",\"tick\":1,\"duration\":0,\"payload\":\"x\",\"value\":0},";
        assertThrows(IllegalArgumentException.class,
                () -> parse(one(technique("xenopixelsmod:bt3_x", "null", bad + END, "10", "20"))));
    }

    @Test void negativeOrNonFiniteNumbersAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> parse(one(technique("xenopixelsmod:bt3_x", "null", END, "-1", "20"))));
        assertThrows(IllegalArgumentException.class,
                () -> parse(one(technique("xenopixelsmod:bt3_x", "null", END, "1e999", "20"))));
        assertThrows(IllegalArgumentException.class,
                () -> parse(one(technique("xenopixelsmod:bt3_x", "null", END, "10", "-5"))));
        assertThrows(IllegalArgumentException.class,
                () -> parse(one(technique("xenopixelsmod:bt3_x", "null", END, "10", "5"))),
                "duration shorter than its last beat");
    }

    @Test void releaseWithoutAProjectileOrWithoutAChargeIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> parse(one(technique("xenopixelsmod:bt3_x", "null", CHARGE + RELEASE + END, "10", "20"))));
        assertThrows(IllegalArgumentException.class,
                () -> parse(one(technique("xenopixelsmod:bt3_x", "\"kamehameha\"", RELEASE + END, "10", "20"))));
    }

    @Test void foreignOrDuplicateIdsAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> parse(one(technique("dragonminez:kamehameha", "null", END, "10", "20"))));
        String same = technique("xenopixelsmod:bt3_x", "null", END, "10", "20");
        assertThrows(IllegalArgumentException.class, () -> parse(one(same + "," + same)));
    }

    @Test void compatibilityAliasMustPointAtAShippedOccurrence() {
        String target = "xenopixelsmod:bt3_x";
        String alias = "xenopixelsmod:bt3_old_x";
        assertEquals(1, parse(withAlias(alias, target, technique(target, "null", END, "10", "20"))).size());
        assertThrows(IllegalArgumentException.class,
                () -> parse(withAlias(alias, "xenopixelsmod:bt3_missing",
                        technique(target, "null", END, "10", "20"))));
        assertThrows(IllegalArgumentException.class,
                () -> parse(withAlias(target, "xenopixelsmod:bt3_y",
                        technique(target, "null", END, "10", "20") + ","
                                + technique("xenopixelsmod:bt3_y", "null", END, "10", "20"))));
    }

    @Test void registrationIsAdditiveIdempotentAndReportsCollisions() {
        var parsed = parse(one(technique("xenopixelsmod:bt3_a", "null", END, "10", "20") + ","
                + technique("xenopixelsmod:bt3_b", "null", END, "10", "20")));
        Map<String, Object> registry = new HashMap<>();
        Object external = new Object();
        registry.put("xenopixelsmod:bt3_b", external);
        registry.put("other:thing", "untouched");

        List<String> conflicts = V3TechniqueCatalog.registerInto(parsed, registry, t -> "v3:" + t.id(), Set.of());
        assertEquals(List.of("xenopixelsmod:bt3_b"), conflicts);
        assertSame(external, registry.get("xenopixelsmod:bt3_b"), "an existing definition is never overwritten");
        assertEquals("v3:xenopixelsmod:bt3_a", registry.get("xenopixelsmod:bt3_a"));
        assertEquals("untouched", registry.get("other:thing"));

        Object first = registry.get("xenopixelsmod:bt3_a");
        // A reload sees its own earlier entries as owned, not as collisions.
        List<String> again = V3TechniqueCatalog.registerInto(parsed, registry, t -> "v3-again", Set.of("xenopixelsmod:bt3_a"));
        assertEquals(List.of("xenopixelsmod:bt3_b"), again);
        assertSame(first, registry.get("xenopixelsmod:bt3_a"));
    }
    @Test void shippedNamesAreEnglishAndKeepTheLabelTheReferenceShows() {
        java.util.regex.Pattern italian = java.util.regex.Pattern.compile(
                "(?i)\\b(onda|raggio|sfera|attacco|att\\.|colpo|pugno|impeto|cannone|lampo|esplosione|fendente|fend\\."
                        + "|tempesta|barriera|bomba|bomb\\.|freccia|pioggia|artiglio|impatto|potenza|di|del|della|dello|il|per)\\b");
        for (V3TechniqueDefinition technique : V3TechniqueCatalog.loadBundled()) {
            assertFalse(italian.matcher(technique.name()).find(), "not English: " + technique.name());
            assertNotNull(technique.sourceLabel(), technique.id());
            assertFalse(technique.sourceLabel().isBlank(), technique.id());
        }
    }

    @Test void renamedIdsKeepAdditiveSaveCompatibility() {
        V3TechniqueCatalog.load();
        assertEquals("xenopixelsmod:bt3_literal_abbreviated_title_maximu_raff_energ_max_potenza_588",
                V3TechniqueCatalog.canonicalId("xenopixelsmod:bt3_raff_energ_max_potenza"));
        assertEquals("xenopixelsmod:bt3_early_goku_super_saiyan_3_onda_super_esplosiva_345",
                V3TechniqueCatalog.canonicalId("XENOPIXELSMOD:BT3_SUPER_ONDA_ESPLOSIVA"));
        assertEquals("xenopixelsmod:bt3_early_kid_goku_kamehameha_15",
                V3TechniqueCatalog.canonicalId("xenopixelsmod:bt3_kamehameha"));
        assertNotNull(V3TechniqueCatalog.find("xenopixelsmod:bt3_raff_energ_max_potenza"));
        assertNotNull(V3TechniqueCatalog.find("xenopixelsmod:bt3_super_onda_esplosiva"));
    }

    @Test void authoredCameraOffsetsLoadAndMalformedOrOverlappingShotsRefuseTheCatalog() {
        String base = technique("xenopixelsmod:bt3_x", "null", END, "10", "20");
        String shot = "{\"tick\":0,\"duration\":20,\"position\":[4,2,-3],\"look\":[0,0,0],\"focus\":0.5,\"easing\":\"SMOOTH\"}";
        String authored = base.substring(0, base.length() - 1) + ",\"camera\":[" + shot + "]}";
        assertEquals(1, parse(one(authored)).getFirst().camera().size());
        assertTrue(parse(one(base)).getFirst().camera().isEmpty(), "unfinished entries have no invented camera");
        assertThrows(IllegalArgumentException.class, () -> parse(one(authored.replace("[4,2,-3]", "[4,2]"))));
        assertThrows(IllegalArgumentException.class, () -> parse(one(authored.replace("SMOOTH", "UNKNOWN"))));
        assertThrows(IllegalArgumentException.class, () -> parse(one(base.substring(0, base.length() - 1)
                + ",\"camera\":[" + shot + "," + shot + "]}")));
    }
}
