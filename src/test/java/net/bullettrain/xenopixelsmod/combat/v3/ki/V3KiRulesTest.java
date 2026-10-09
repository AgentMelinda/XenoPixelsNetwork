package net.bullettrain.xenopixelsmod.combat.v3.ki;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueCatalog;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueDefinition;
import net.bullettrain.xenopixelsmod.fx.aura.AuraPalette;
import net.bullettrain.xenopixelsmod.fx.ki.KiLook;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class V3KiRulesTest {
    private static final int KAMEHAMEHA_BLUE = 5240831;

    @Test void everyKiArchetypeGetsItsOwnKind() {
        assertEquals(KiLook.Kind.WAVE, V3KiStyle.of("beam", "kamehameha").kind());
        assertEquals(KiLook.Kind.LASER, V3KiStyle.of("laser", "death_beam").kind());
        assertEquals(KiLook.Kind.BEAM, V3KiStyle.of("laser", "makkanko").kind(), "the spiralled beam");
        assertEquals(KiLook.Kind.DISK, V3KiStyle.of("disc", "kienzan").kind());
        assertEquals(KiLook.Kind.BARRAGE, V3KiStyle.of("volley", "ki_barrage").kind());
        assertEquals(KiLook.Kind.MEDIUM_BALL, V3KiStyle.of("ball", "sokidan").kind());
        assertEquals(KiLook.Kind.GIANT_BALL, V3KiStyle.of("giant_ball", "supernova").kind());
        assertEquals(KiLook.Kind.MEDIUM_BALL, V3KiStyle.of("melee_energy", "sokidan").kind());
        assertEquals(KiLook.Kind.MEDIUM_BALL, V3KiStyle.of("something new", null).kind());
        assertEquals(KiLook.Kind.MEDIUM_BALL, V3KiStyle.of(null, null).kind());
    }

    @Test void aVolleyIsManySmallShotsAndAGiantBallIsOneBigSlowOne() {
        V3KiStyle volley = V3KiStyle.of("volley", "ki_barrage");
        V3KiStyle ball = V3KiStyle.of("ball", "sokidan");
        V3KiStyle giant = V3KiStyle.of("giant_ball", "supernova");
        assertTrue(volley.shots() >= 8);
        assertEquals(1, ball.shots());
        assertEquals(1, giant.shots());
        assertTrue(volley.radius() < ball.radius() && ball.radius() < giant.radius());
        assertTrue(giant.speed() < ball.speed());
        assertTrue(volley.damage() / volley.shots() < ball.damage(), "one volley shot is weaker than a ball");
    }

    @Test void familiarAttacksKeepDragonMineZsColours() {
        assertEquals(KAMEHAMEHA_BLUE, V3KiStyle.of("beam", "kamehameha").core());
        assertEquals(13504739, V3KiStyle.of("beam", "galick_gun").core());
        assertEquals(16750848, V3KiStyle.of("beam", "final_flash").core());
        V3KiStyle makkanko = V3KiStyle.of("laser", "makkanko");
        assertTrue(KiLook.twoTone(makkanko.core(), makkanko.edge()), "yellow beam, purple spiral");
        assertFalse(KiLook.twoTone(KAMEHAMEHA_BLUE, KAMEHAMEHA_BLUE));
    }

    @Test void everyKindOfKiHasALookAndBeamsAreTheOnesLaidInLengths() {
        for (KiLook.Kind kind : KiLook.Kind.values()) {
            assertNotNull(KiLook.flight(kind), kind.name());
            assertNotNull(KiLook.landing(kind), kind.name());
            assertTrue(KiLook.nativeScale(kind, 1f) > 0f, kind.name());
            assertTrue(KiLook.nativeScale(kind, Float.NaN) > 0f, kind.name());
            assertEquals(kind == KiLook.Kind.WAVE || kind == KiLook.Kind.LASER || kind == KiLook.Kind.BEAM,
                    KiLook.beam(kind), kind.name());
        }
        assertEquals(KiLook.Part.EXPLOSION, KiLook.landing(KiLook.Kind.GIANT_BALL));
        assertEquals(KiLook.Part.IMPACT, KiLook.landing(KiLook.Kind.WAVE));
    }

    @Test void theGeneratorBuiltEveryPartInEveryPaletteColour() throws IOException {
        Set<String> parts;
        try (var in = V3KiRulesTest.class.getResourceAsStream("/assets/xenopixelsmod/effeks/ki/parts.txt")) {
            assertNotNull(in, "run: python tools/effekseer/gen_effects.py ki");
            parts = new HashSet<>(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank()).toList());
        }
        Set<String> wanted = new HashSet<>();
        for (KiLook.Part part : KiLook.Part.values()) wanted.add(part.id());
        assertEquals(wanted, parts);
        assertTrue(AuraPalette.colours().size() > 1, "the aura palette is on the classpath");
        for (int colour : AuraPalette.colours()) {
            for (KiLook.Part part : KiLook.Part.values()) {
                String path = "/assets/xenopixelsmod/effeks/ki/" + KiLook.asset(part, colour) + ".efkefc";
                assertNotNull(V3KiRulesTest.class.getResource(path), path);
            }
        }
    }

    @Test void anyColourResolvesToTheNearestBuiltOne() {
        assertEquals("ki_ball_" + AuraPalette.hex(AuraPalette.nearest(KAMEHAMEHA_BLUE)),
                KiLook.asset(KiLook.Part.BALL, KAMEHAMEHA_BLUE));
        assertEquals(KiLook.asset(KiLook.Part.WAVE_BODY, 0x123456), KiLook.asset(KiLook.Part.WAVE_BODY, 0xFF123456),
                "alpha bits are ignored");
        assertTrue(KiLook.asset(KiLook.Part.WAVE_BODY, 0x123456).matches("ki_wave_body_[0-9a-f]{6}"));
    }

    @Test void everyShippedKiTechniqueResolvesToEffectsThatExist() {
        int checked = 0;
        for (V3TechniqueDefinition technique : V3TechniqueCatalog.loadBundled()) {
            if (technique.kiTechnique() == null) continue;
            V3KiStyle style = V3KiStyle.of(technique.type(), technique.kiTechnique());
            for (KiLook.Part part : List.of(KiLook.Part.CHARGE, KiLook.flight(style.kind()), KiLook.landing(style.kind()))) {
                String path = "/assets/xenopixelsmod/effeks/ki/" + style.asset(part) + ".efkefc";
                assertNotNull(V3KiRulesTest.class.getResource(path), technique.id() + " " + path);
            }
            checked++;
        }
        assertTrue(checked > 50, "the shipped catalog has ki techniques");
    }

    @Test void aBeamShowsNewLengthsAtOnceAndRefreshesAllOfThemTogether() {
        assertEquals(0, KiLook.firstLength(0, 0, 3), "first tick: everything");
        assertEquals(3, KiLook.firstLength(1, 3, 5), "between refreshes: only the new ones");
        assertEquals(0, KiLook.firstLength(KiLook.BODY_RESEND_TICKS, 5, 5), "refresh: all of them");
        assertEquals(4, KiLook.firstLength(1, 9, 4), "a beam that got shorter draws nothing new");
        assertEquals(0, KiLook.firstLength(1, -3, 4));
    }

    @Test void aShotAdvancesTowardsItsTargetAndNeverPastIt() {
        Vec3 from = new Vec3(0, 64, 0);
        Vec3 to = new Vec3(10, 64, 0);
        assertEquals(new Vec3(1.5, 64, 0), V3KiPath.advance(from, to, 1.5));
        assertEquals(to, V3KiPath.advance(new Vec3(9.5, 64, 0), to, 1.5));
        assertEquals(to, V3KiPath.advance(to, to, 1.5));
    }

    @Test void aShotLandsWhenThisTicksStepReachesTheTarget() {
        Vec3 to = new Vec3(10, 64, 0);
        assertFalse(V3KiPath.reaches(new Vec3(0, 64, 0), to, 1.5, 0.6));
        assertTrue(V3KiPath.reaches(new Vec3(8.0, 64, 0), to, 1.5, 0.6));
        assertTrue(V3KiPath.reaches(to, to, 1.5, 0.6));
        assertFalse(V3KiPath.reaches(new Vec3(Double.NaN, 64, 0), to, 1.5, 0.6));
    }

    @Test void aBeamIsLaidInLengthsFromTheHandsToItsHead() {
        assertEquals(0, V3KiPath.lengths(0.0, 4.0));
        assertEquals(1, V3KiPath.lengths(0.1, 4.0));
        assertEquals(1, V3KiPath.lengths(4.0, 4.0));
        assertEquals(2, V3KiPath.lengths(4.1, 4.0));
        assertEquals(24, V3KiPath.lengths(96.0, 4.0));
        assertEquals(V3KiPath.MAX_LENGTHS, V3KiPath.lengths(1.0e9, 0.5), "never an unbounded burst of packets");
        assertEquals(0, V3KiPath.lengths(Double.NaN, 4.0));
        assertEquals(new Vec3(0, 64, 8), V3KiPath.lengthStart(new Vec3(0, 64, 0), new Vec3(0, 0, 1), 2, 4.0));
    }

    @Test void aBeamsHeadStopsAtTheTargetOrAtItsRange() {
        assertEquals(4.0, V3KiPath.beamReach(0.0, 30.0));
        assertEquals(30.0, V3KiPath.beamReach(28.0, 30.0));
        assertEquals(V3KiPath.BEAM_RANGE, V3KiPath.beamReach(V3KiPath.BEAM_RANGE - 1, 500.0));
    }

    @Test void aSustainedBeamCannotDamageBeyondItsExistingHead() {
        assertTrue(V3KiPath.beamContact(20.0, 20.4, 0.5));
        assertFalse(V3KiPath.beamContact(20.0, 21.0, 0.5));
        assertFalse(V3KiPath.beamContact(20.0, Double.NaN, 0.5));
        assertFalse(V3KiPath.beamContact(-1.0, 0.0, 0.5));
    }

    @Test void volleyShotsLeaveOneAfterAnotherFromAlternatingHands() {
        assertEquals(0, V3KiPath.volleyDelay(0));
        assertTrue(V3KiPath.volleyDelay(1) > 0);
        assertTrue(V3KiPath.volleyDelay(5) > V3KiPath.volleyDelay(4));
        assertTrue(V3KiPath.volleySide(0) * V3KiPath.volleySide(1) < 0, "left then right");
        assertEquals(V3KiPath.volleySide(0), V3KiPath.volleySide(2));
    }

    @Test void damageIsSharedOutSoTheWholeAttackDealsItsTotal() {
        assertEquals(3.0f, V3KiPath.share(3.0f, 1));
        assertEquals(0.6f, V3KiPath.share(3.0f, 5), 1.0e-6f);
        assertEquals(3.0f, V3KiPath.share(3.0f, 0), "a zero count still deals the attack's damage once");
    }

    @Test void delayedShotsAreOwnedByOneExactCasterAndCast() {
        var owner = java.util.UUID.randomUUID();
        var cast = java.util.UUID.randomUUID();
        assertTrue(V3KiShots.ownedBy(owner, cast, owner, cast));
        assertFalse(V3KiShots.ownedBy(owner, cast, java.util.UUID.randomUUID(), cast));
        assertFalse(V3KiShots.ownedBy(owner, cast, owner, java.util.UUID.randomUUID()));
        assertFalse(V3KiShots.ownedBy(owner, cast, null, cast));
    }

    @Test void catalogKiAttacksGetPlainIdsDragonMineZsCommandAccepts() {
        assertEquals("bt3_kamehameha_x10", V3NativeKi.nativeId("xenopixelsmod:bt3_kamehameha_x10"));
        assertEquals("bt3_kamehameha_x10", V3NativeKi.nativeId("XenoPixelsMod:BT3_Kamehameha_X10"));
        assertNull(V3NativeKi.nativeId(null));
        var plan = V3NativeKi.plan(V3TechniqueCatalog.loadBundled());
        assertTrue(plan.size() > 50, "every ki technique of the catalog is offered");
        for (var entry : plan.entrySet()) {
            assertTrue(entry.getKey().matches("bt3_[a-z0-9_]+"), entry.getKey());
            assertNotNull(entry.getValue().kiTechnique(), entry.getKey());
        }
    }
}
