package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Bt3RushResolverTest {

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
        Set<String> ids = Bt3RushResolver.all().stream()
                .map(Bt3RushDefinition::id)
                .collect(Collectors.toSet());
        assertEquals(16, ids.size());
        for (Bt3RushDefinition definition : Bt3RushResolver.all()) {
            assertEquals(28, definition.durationTicks(), definition.id());
            assertArrayEquals(new int[]{5, 10, 16, 23}, definition.impactTicks(), definition.id());
            assertEquals("combat.xeno_cinematic_rush_" + definition.id(), definition.animation());
        }
    }
}
