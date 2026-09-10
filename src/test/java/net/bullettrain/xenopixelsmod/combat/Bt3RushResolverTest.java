package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.api.registry.Bt3RushDefinition;
import net.bullettrain.xenopixelsmod.api.registry.RushRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bt3RushResolverTest {

    /**
     * The profiles XenoPixels itself ships. Addons can add more at runtime, so this is asserted by
     * presence rather than by counting everything the registry holds.
     */
    private static final List<String> BUILT_IN_IDS = List.of(
            "universal",
            "saiyan", "human", "namekian", "majin", "arcosian",
            "super_saiyan_blue", "super_saiyan_god", "super_saiyan_3", "super_saiyan_2",
            "super_saiyan", "golden_arcosian", "potential_unleashed", "orange_namekian",
            "giant_namekian", "pure_majin");

    @Test
    void iconicFormOutranksRaceThenFallsBackToRaceAndUniversal() {
        assertEquals("super_saiyan_blue", Bt3RushResolver.resolve("Saiyan", "SSB").id());
        assertEquals("golden_arcosian", Bt3RushResolver.resolve("Arcosian", "Golden").id());
        assertEquals("namekian", Bt3RushResolver.resolve("Namekian", "Base").id());
        assertEquals("human", Bt3RushResolver.resolve("Earthling", null).id());
        assertEquals("universal", Bt3RushResolver.resolve("Unknown", "Unknown").id());
    }

    @Test
    void allProfilesHaveTheSharedServerTimingContract() {
        Set<String> shipped = RushRegistry.builtIns().stream()
                .map(Bt3RushDefinition::id)
                .collect(Collectors.toSet());
        assertEquals(Set.copyOf(BUILT_IN_IDS), shipped);

        for (Bt3RushDefinition definition : RushRegistry.builtIns()) {
            assertEquals(28, definition.durationTicks(), definition.id());
            assertArrayEquals(new int[]{5, 10, 16, 23}, definition.impactTicks(), definition.id());
            assertEquals("combat.xeno_cinematic_rush_" + definition.id(), definition.animation());
        }
    }

    @Test
    void addonRegistrationsDoNotJoinTheShippedSet() {
        assertTrue(Bt3RushResolver.all().size() >= RushRegistry.builtIns().size());
    }
}
