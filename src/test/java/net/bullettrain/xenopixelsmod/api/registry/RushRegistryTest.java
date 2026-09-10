package net.bullettrain.xenopixelsmod.api.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what an addon author actually depends on. The registry is static and never cleared, so
 * these ids are deliberately distinctive and must not be substrings of anything built in.
 */
class RushRegistryTest {

    @Test
    void aRegisteredFormResolvesAndIsRetrievableById() {
        Bt3RushDefinition definition =
                Bt3RushDefinition.standard("xenotest_ultra_instinct", "xenotest_ultra_instinct");
        RushRegistry.registerForm(definition, "xenotest ultra instinct", "xenotest ui");

        assertSame(definition, RushRegistry.resolve("Saiyan", "Xenotest_Ultra_Instinct"));
        assertSame(definition, RushRegistry.resolve("Saiyan", "xenotest ui"));
        assertSame(definition, RushRegistry.byId("xenotest_ultra_instinct"));
        assertTrue(RushRegistry.all().contains(definition));
    }

    @Test
    void registeringAtDefaultPrecedenceCannotStealABuiltInForm() {
        // "xenotest super saiyan" contains "super saiyan", so the built-in claims it first. This is
        // the shadowing trap the API documents.
        RushRegistry.registerForm(
                Bt3RushDefinition.standard("xenotest_shadowed", "xenotest_shadowed"),
                "xenotest super saiyan");

        assertEquals("super_saiyan", RushRegistry.resolve("Saiyan", "xenotest super saiyan").id());
    }

    @Test
    void beforeBuiltInsPrecedenceWinsOverASubstringMatch() {
        Bt3RushDefinition definition =
                Bt3RushDefinition.standard("xenotest_ssj4", "xenotest_ssj4");
        RushRegistry.registerForm(definition, RushRegistry.Precedence.BEFORE_BUILT_INS,
                "xenotest super saiyan 4");

        assertSame(definition, RushRegistry.resolve("Saiyan", "Xenotest Super Saiyan 4"));
        // The built-in is still reachable for a form that is not the more specific one.
        assertEquals("super_saiyan", RushRegistry.resolve("Saiyan", "Super Saiyan").id());
    }

    @Test
    void aRegisteredRaceResolvesWhenNoFormMatches() {
        Bt3RushDefinition definition =
                Bt3RushDefinition.standard("xenotest_android", "xenotest_android");
        RushRegistry.registerRace(definition, "xenotest android");

        assertSame(definition, RushRegistry.resolve("Xenotest Android", "Base"));
    }

    @Test
    void unknownIdAndBlankInputFallBackToUniversal() {
        assertSame(RushRegistry.universal(), RushRegistry.byId("no_such_rush"));
        assertSame(RushRegistry.universal(), RushRegistry.byId(null));
        assertSame(RushRegistry.universal(), RushRegistry.byId("  "));
    }

    @Test
    void aMalformedDefinitionIsRejectedAtConstruction() {
        // Impact ticks must ascend and stay inside the duration, so a bad definition fails at
        // registration rather than midway through a rush.
        assertThrows(IllegalArgumentException.class,
                () -> new Bt3RushDefinition("bad", "anim", 10, new int[]{5, 5}));
        assertThrows(IllegalArgumentException.class,
                () -> new Bt3RushDefinition("bad", "anim", 10, new int[]{12}));
        assertThrows(IllegalArgumentException.class,
                () -> new Bt3RushDefinition("", "anim", 10, new int[]{5}));
    }
}
