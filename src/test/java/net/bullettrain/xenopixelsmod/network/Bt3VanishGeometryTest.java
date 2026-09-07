package net.bullettrain.xenopixelsmod.network;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link Bt3CombatPacket#vanishPoint}.
 *
 * <p>Vanish was landing somewhere other than beside the opponent, but only sometimes and only when
 * already close. The landing direction was the horizontal line from the fighter to the target,
 * trusted down to a hundredth of a block; at point-blank that line is short enough to be noise, so
 * it flipped between frames, and the attacking client — which computes this same point to predict
 * its own landing, from positions about a hundred milliseconds behind the server's — could pick a
 * direction opposite to the server's. Whether the two sides agree at close range is the property
 * under test here, and it is not something in-game testing shows reliably, because it depends on
 * network timing.
 */
class Bt3VanishGeometryTest {

    private static final double GAP = 1.35;
    private static final double SIDE = 1.05;
    private static final double NEAR = 1.5;
    private static final double EPS = 1.0e-9;

    /** The formula this replaced, so "unchanged at range" is asserted against the real thing. */
    private static Vec3 legacy(double px, double pz, double tx, double ty, double tz,
                               int side, double gap, double sideOff) {
        double ax = tx - px;
        double az = tz - pz;
        double len = Math.sqrt(ax * ax + az * az);
        double dirX = ax / len;
        double dirZ = az / len;
        double s = side < 0 ? -sideOff : (side > 0 ? sideOff : 0.0);
        return new Vec3(tx + dirX * gap + -dirZ * s, ty, tz + dirZ * gap + dirX * s);
    }

    private static Vec3 point(double px, double pz, float targetYaw, int side, double nearField) {
        return Bt3CombatPacket.vanishPoint(px, pz, 0, 64, 0, targetYaw, side, GAP, SIDE, nearField);
    }

    @Test
    void atOrBeyondTheNearFieldNothingChangedFromTheOldFormula() {
        for (double sep : new double[]{NEAR, 2.0, 4.0, 7.0}) {
            for (int side : new int[]{-1, 0, 1}) {
                Vec3 expected = legacy(-sep, 0.7 * sep, 0, 64, 0, side, GAP, SIDE);
                Vec3 actual = Bt3CombatPacket.vanishPoint(
                        -sep, 0.7 * sep, 0, 64, 0, 37.0f, side, GAP, SIDE, NEAR);
                // The approach vector is (sep, -0.7*sep) normalised either way; only its length
                // differs between the two call shapes, so the landings must match exactly.
                assertEquals(expected.x, actual.x, 1.0e-9, "sep " + sep + " side " + side);
                assertEquals(expected.z, actual.z, 1.0e-9, "sep " + sep + " side " + side);
            }
        }
    }

    /**
     * What actually ships. The near-field rework played worse than the plain approach line, so the
     * default turns it off and this asserts that "off" really is the original geometry rather than
     * an approximation of it. The rework itself stays in {@link Bt3CombatPacket#vanishPoint},
     * reachable by setting {@code vanishNearField} above zero, so the two can be compared again.
     */
    @Test
    void theShippedDefaultIsTheOriginalGeometry() {
        assertEquals(0.0, net.bullettrain.xenopixelsmod.config.XenoServerConfig.vanishNearField,
                "vanishNearField must ship off");
        assertFalse(net.bullettrain.xenopixelsmod.config.XenoServerConfig.vanishOpenSpotSearch,
                "vanishOpenSpotSearch must ship off");
    }

    @Test
    void aZeroNearFieldIsAnOffSwitchAtEveryRange() {
        for (double sep : new double[]{0.05, 0.3, 1.0, 5.0}) {
            Vec3 expected = legacy(-sep, 0, 0, 64, 0, 0, GAP, SIDE);
            Vec3 actual = point(-sep, 0, 123.0f, 0, 0.0);
            assertEquals(expected.x, actual.x, EPS, "sep " + sep);
            assertEquals(expected.z, actual.z, EPS, "sep " + sep);
        }
    }

    /** Yaw 0 faces +Z, so the target's back is -Z and the landing is gap blocks that way. */
    @Test
    void onTopOfTheTargetTheLandingIsTheirOwnBack() {
        Vec3 landing = point(0, 0, 0.0f, 0, NEAR);
        assertEquals(0.0, landing.x, 1.0e-6);
        assertEquals(-GAP, landing.z, 1.0e-6);

        // Yaw 90 faces -X, so their back is +X.
        Vec3 turned = point(0, 0, 90.0f, 0, NEAR);
        assertEquals(GAP, turned.x, 1.0e-6);
        assertEquals(0.0, turned.z, 1.0e-6);
    }

    @Test
    void atPointBlankTheLandingDoesNotDependOnWhereTheFighterStands() {
        Vec3 first = point(0.01, 0.0, 40.0f, 0, NEAR);
        Vec3 second = point(-0.01, 0.008, 40.0f, 0, NEAR);
        assertTrue(first.distanceTo(second) < 0.05,
                "two near-identical positions gave " + first + " and " + second);
    }

    /**
     * The bug itself. Client and server can disagree by a couple of tenths of a block on where two
     * moving bodies are; under the old formula, at a tenth of a block apart, that disagreement
     * could put the two landings on opposite sides of the target.
     */
    @Test
    void aSmallPositionDisagreementNoLongerFlipsTheLandingAcrossTheTarget() {
        double serverSep = 0.10;
        Vec3 server = point(-serverSep, 0, 25.0f, 1, NEAR);
        Vec3 client = point(serverSep, 0.05, 25.0f, 1, NEAR);
        assertTrue(server.distanceTo(client) < 0.6,
                "landings " + server.distanceTo(client) + " blocks apart");

        Vec3 legacyServer = legacy(-serverSep, 0, 0, 64, 0, 1, GAP, SIDE);
        Vec3 legacyClient = legacy(serverSep, 0.05, 0, 64, 0, 1, GAP, SIDE);
        assertTrue(legacyServer.distanceTo(legacyClient) > 2.0,
                "the old formula should be the one that flips; it moved only "
                        + legacyServer.distanceTo(legacyClient));
    }

    @Test
    void theBlendIsContinuousSoThereIsNoSnapAtTheThreshold() {
        Vec3 previous = null;
        for (double sep = 0.0; sep <= 3.0; sep += 0.05) {
            Vec3 landing = point(-sep, 0, 130.0f, 0, NEAR);
            if (previous != null) {
                assertTrue(landing.distanceTo(previous) < 0.15,
                        "jump of " + landing.distanceTo(previous) + " at sep " + sep);
            }
            previous = landing;
        }
    }

    @Test
    void theSideOffsetIsPerpendicularAndMirrors() {
        for (double sep : new double[]{0.0, 0.5, 1.5, 5.0}) {
            Vec3 centre = point(-sep, 0.3, 210.0f, 0, NEAR);
            Vec3 left = point(-sep, 0.3, 210.0f, -1, NEAR);
            Vec3 right = point(-sep, 0.3, 210.0f, 1, NEAR);

            assertEquals(SIDE, centre.distanceTo(left), 1.0e-6, "sep " + sep);
            assertEquals(SIDE, centre.distanceTo(right), 1.0e-6, "sep " + sep);
            // Mirrored: the centre is exactly halfway between the two.
            assertEquals(centre.x, (left.x + right.x) * 0.5, 1.0e-6, "sep " + sep);
            assertEquals(centre.z, (left.z + right.z) * 0.5, 1.0e-6, "sep " + sep);

            double alongX = centre.x;
            double alongZ = centre.z;
            double offX = right.x - centre.x;
            double offZ = right.z - centre.z;
            assertEquals(0.0, alongX * offX + alongZ * offZ, 1.0e-6,
                    "side offset must be perpendicular to the landing direction, sep " + sep);
        }
    }
}
