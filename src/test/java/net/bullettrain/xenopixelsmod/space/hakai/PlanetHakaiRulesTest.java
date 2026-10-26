package net.bullettrain.xenopixelsmod.space.hakai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiRules.Mode;
import static net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiRules.Verdict;
import static org.junit.jupiter.api.Assertions.*;

class PlanetHakaiRulesTest {
    @Test void globalDestructibilityIsAnUpperCeilingAndBodyOverridesCanOnlyTightenIt() {
        assertEquals(PlanetHakaiRules.Destructibility.GATED,
                PlanetHakaiRules.effectiveDestructibility("gated", "free"));
        assertEquals(PlanetHakaiRules.Destructibility.NONE,
                PlanetHakaiRules.effectiveDestructibility("none", "free"));
        assertEquals(PlanetHakaiRules.Destructibility.ADMIN_ONLY,
                PlanetHakaiRules.effectiveDestructibility("free", "admin_only"));
        assertEquals(PlanetHakaiRules.Destructibility.FREE,
                PlanetHakaiRules.effectiveDestructibility("free", null));
        assertEquals(PlanetHakaiRules.Destructibility.NONE,
                PlanetHakaiRules.effectiveDestructibility("invalid", "free"));
        assertTrue(PlanetHakaiRules.acceptsFreeImpact("free", "x:mars", List.of(), false, false));
        assertFalse(PlanetHakaiRules.acceptsFreeImpact("gated", "x:mars", List.of(), false, false));
        assertFalse(PlanetHakaiRules.acceptsFreeImpact("free", "x:earth", List.of("x:earth"), false, false));
        assertFalse(PlanetHakaiRules.acceptsFreeImpact("free", "x:mars", List.of(), true, false));
        assertFalse(PlanetHakaiRules.acceptsFreeImpact("free", "x:mars", List.of(), false, true));
        assertEquals("xenopixelsmod:mars", PlanetHakaiRules.normalizeBodyId(" Mars "));
        assertEquals("dragonmines:namek", PlanetHakaiRules.normalizeBodyId("DRAGONMINES:Namek"));
        assertEquals("", PlanetHakaiRules.normalizeBodyId("  "));
    }

    private static final List<String> PROTECTED = List.of("xenopixelsmod:earth");

    @Test void modesParseLeniently() {
        assertEquals(Mode.OFF, Mode.parse(null));
        assertEquals(Mode.OFF, Mode.parse("nonsense"));
        assertEquals(Mode.ALL, Mode.parse(" ALL "));
        assertEquals(Mode.ERASABLE_ONLY, Mode.parse("erasable_only"));
        assertEquals("erasable_only", Mode.ERASABLE_ONLY.key());
    }

    @Test void verdictOrder() {
        assertEquals(Verdict.MODE_OFF, PlanetHakaiRules.verdict(Mode.OFF, "x:mars", true, PROTECTED, 4, 2, false));
        assertEquals(Verdict.ALREADY_ERASED, PlanetHakaiRules.verdict(Mode.ALL, "x:mars", true, PROTECTED, 4, 2, true));
        assertEquals(Verdict.PROTECTED, PlanetHakaiRules.verdict(Mode.ALL, "xenopixelsmod:earth", true, PROTECTED, 4, 2, false));
        assertEquals(Verdict.NOT_ERASABLE, PlanetHakaiRules.verdict(Mode.ERASABLE_ONLY, "x:mars", false, PROTECTED, 4, 2, false));
        assertEquals(Verdict.ALLOWED, PlanetHakaiRules.verdict(Mode.ERASABLE_ONLY, "x:mars", true, PROTECTED, 2, 2, false));
        assertEquals(Verdict.NO_PERMISSION, PlanetHakaiRules.verdict(Mode.ALL, "x:mars", false, PROTECTED, 0, 2, false));
        assertEquals(Verdict.ALLOWED, PlanetHakaiRules.verdict(Mode.ALL, "x:mars", false, PROTECTED, 0, 0, false));
    }

    @Test void sidecarDestructibilityPolicyAddsPerDimensionProtectionAndAdminGate() {
        var impact = PlanetHakaiRules.DestructionPath.ORDINARY_IMPACT;
        var hakai = PlanetHakaiRules.DestructionPath.SANCTIONED_HAKAI;
        var admin = PlanetHakaiRules.DestructionPath.ADMIN;
        var script = PlanetHakaiRules.DestructionPath.SCRIPTED_EVENT;

        assertEquals(Verdict.PROTECTED, PlanetHakaiRules.sidecarVerdict("none", 4, admin));
        assertEquals(Verdict.NO_PERMISSION, PlanetHakaiRules.sidecarVerdict("admin_only", 1, impact));
        assertNull(PlanetHakaiRules.sidecarVerdict("admin_only", 2, hakai),
                "operator-level event use is permitted by admin_only");
        assertEquals(Verdict.NO_PERMISSION, PlanetHakaiRules.sidecarVerdict("admin_only", 1, admin));
        assertNull(PlanetHakaiRules.sidecarVerdict("admin_only", 2, admin));
        assertNull(PlanetHakaiRules.sidecarVerdict("admin_only", 0, script));
        assertEquals(Verdict.POLICY_PATH_DENIED, PlanetHakaiRules.sidecarVerdict("gated", 0, impact));
        assertNull(PlanetHakaiRules.sidecarVerdict("gated", 0, hakai));
        assertNull(PlanetHakaiRules.sidecarVerdict("gated", 0, admin));
        assertNull(PlanetHakaiRules.sidecarVerdict("free", 0, impact));
        assertNull(PlanetHakaiRules.sidecarVerdict("free", 0, hakai));
        assertEquals(Verdict.PROTECTED, PlanetHakaiRules.sidecarVerdict("sometimes", 4, impact),
                "an invalid policy reaching runtime must fail closed rather than escape into projectile handling");
    }

    @Test void raySphereHitsMissesAndRespectsReach() {
        // Planet r=100 at z=1000 straight ahead.
        assertEquals(900, PlanetHakaiRules.raySphere(0, 0, 0, 0, 0, 1, 0, 0, 1000, 100, 20_000), 1e-9);
        assertEquals(-1, PlanetHakaiRules.raySphere(0, 0, 0, 0, 0, -1, 0, 0, 1000, 100, 20_000), "behind");
        assertEquals(-1, PlanetHakaiRules.raySphere(0, 0, 0, 1, 0, 0, 0, 0, 1000, 100, 20_000), "off to the side");
        assertEquals(-1, PlanetHakaiRules.raySphere(0, 0, 0, 0, 0, 1, 0, 0, 1000, 100, 500), "beyond reach");
        assertEquals(0, PlanetHakaiRules.raySphere(0, 0, 990, 0, 0, 1, 0, 0, 1000, 100, 10), "inside");
        assertEquals(-1, PlanetHakaiRules.raySphere(0, 0, 0, 0, 0, 1, 0, 0, Double.NaN, 100, 20_000));
    }

    @Test void progressAndRestoreTimer() {
        assertEquals(0.5, PlanetHakaiRules.progress(100, 150, 100), 1e-9);
        assertEquals(1, PlanetHakaiRules.progress(100, 999, 100), 1e-9);
        assertFalse(PlanetHakaiRules.dueForRestore(0, Long.MAX_VALUE, 0), "0 = never by itself");
        assertTrue(PlanetHakaiRules.dueForRestore(0, 60_000, 1));
        assertFalse(PlanetHakaiRules.dueForRestore(0, 59_999, 1));
    }
}
