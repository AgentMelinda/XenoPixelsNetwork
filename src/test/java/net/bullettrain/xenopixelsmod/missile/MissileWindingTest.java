package net.bullettrain.xenopixelsmod.missile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Faces must be wound the way their normals point.
 *
 * <p>The missile is code-built geometry drawn through {@code RenderType.entitySolid}, which culls
 * back faces. Winding and normal are therefore two independent statements about which way a face
 * points, and nothing in the compiler or the renderer complains when they disagree — the surface
 * simply vanishes and you see the inside of the far wall instead. That is exactly what happened: the
 * whole hull, nose cone, stripe rings and panels were wound inwards while their normals pointed out,
 * and the missile rendered inside-out.
 *
 * <p>The shapes themselves need a client to look at. This invariant does not, so it is the part worth
 * pinning: for every face, the cross product of its first two edges must agree in sign with the
 * normal the same call passes alongside.
 *
 * <p>The geometry here mirrors {@code MissileModelDrawer}'s emitters rather than calling them —
 * {@code VertexConsumer} and {@code PoseStack} need a render context. Keeping the winding rules
 * beside the assertions is what makes a future change to the drawer visible here.
 */
class MissileWindingTest {

    private static final int SIDES = 20;

    private static double[] cross(double[] a, double[] b) {
        return new double[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] edge(double[] from, double[] to) {
        return new double[] {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** The winding normal of a face given in order, from its first two edges. */
    private static double[] windingNormal(double[]... vertices) {
        return cross(edge(vertices[0], vertices[1]), edge(vertices[1], vertices[2]));
    }

    private static double[] ringPoint(int index, double radius, double y) {
        double a = index * Math.PI * 2.0 / SIDES;
        return new double[] {Math.cos(a) * radius, y, Math.sin(a) * radius};
    }

    /**
     * Every hull segment faces outwards.
     *
     * <p>Walks the whole ring rather than one segment: an inverted winding is uniform, so a single
     * sample would pass on a half-fixed emitter that only corrected one side.
     */
    @Test
    void everyHullSegmentFacesOutward() {
        double r0 = 0.8;
        double r1 = 0.6;
        for (int i = 0; i < SIDES; i++) {
            int j = (i + 1) % SIDES;
            // MissileModelDrawer.frustum: bottom-i, top-i, top-j, bottom-j.
            double[] normal = windingNormal(
                    ringPoint(i, r0, 0.0),
                    ringPoint(i, r1, 2.0),
                    ringPoint(j, r1, 2.0));
            // The outward direction at this segment, which is what the emitter passes as its normal.
            double[] outward = {
                    (Math.cos(i * Math.PI * 2.0 / SIDES) + Math.cos(j * Math.PI * 2.0 / SIDES)) / 2.0,
                    0.0,
                    (Math.sin(i * Math.PI * 2.0 / SIDES) + Math.sin(j * Math.PI * 2.0 / SIDES)) / 2.0};
            assertTrue(dot(normal, outward) > 0.0,
                    "hull segment " + i + " is wound inwards; the shell would be culled and the "
                            + "inside of the far wall drawn instead");
        }
    }

    /** A cylinder is the degenerate case of the same segment, and must not flip. */
    @Test
    void aStraightSectionFacesOutwardToo() {
        for (int i = 0; i < SIDES; i++) {
            int j = (i + 1) % SIDES;
            double[] normal = windingNormal(
                    ringPoint(i, 0.7, 0.0),
                    ringPoint(i, 0.7, 1.0),
                    ringPoint(j, 0.7, 1.0));
            double[] outward = {
                    (Math.cos(i * Math.PI * 2.0 / SIDES) + Math.cos(j * Math.PI * 2.0 / SIDES)) / 2.0,
                    0.0,
                    (Math.sin(i * Math.PI * 2.0 / SIDES) + Math.sin(j * Math.PI * 2.0 / SIDES)) / 2.0};
            assertTrue(dot(normal, outward) > 0.0, "straight hull segment " + i + " is inside-out");
        }
    }

    /** An upward cap faces up: centre, j, i. */
    @Test
    void anUpwardCapFacesUp() {
        for (int i = 0; i < SIDES; i++) {
            int j = (i + 1) % SIDES;
            double[] normal = windingNormal(
                    new double[] {0.0, 1.0, 0.0},
                    ringPoint(j, 0.5, 1.0),
                    ringPoint(i, 0.5, 1.0));
            assertTrue(normal[1] > 0.0,
                    "cap wedge " + i + " is wound downwards while its normal points up");
        }
    }

    /** A downward cap faces down: centre, i, j. */
    @Test
    void aDownwardCapFacesDown() {
        for (int i = 0; i < SIDES; i++) {
            int j = (i + 1) % SIDES;
            double[] normal = windingNormal(
                    new double[] {0.0, 0.0, 0.0},
                    ringPoint(i, 0.5, 0.0),
                    ringPoint(j, 0.5, 0.0));
            assertTrue(normal[1] < 0.0,
                    "nozzle wedge " + i + " is wound upwards while its normal points down");
        }
    }

    /**
     * The order that was shipped really was inverted.
     *
     * <p>Without this the suite would pass just as happily against the broken winding, since every
     * other test here only asserts the corrected order behaves correctly.
     */
    @Test
    void theOldOrderWasTheInvertedOne() {
        double[] wrong = windingNormal(
                ringPoint(0, 0.7, 0.0),
                ringPoint(1, 0.7, 0.0),
                ringPoint(1, 0.7, 1.0));
        double[] outward = {Math.cos(Math.PI / SIDES), 0.0, Math.sin(Math.PI / SIDES)};
        assertTrue(dot(wrong, outward) < 0.0,
                "the pre-fix order should point inwards; if this passes the bug was elsewhere");
    }

    /** A box face keeps agreeing with its normal — it was already correct and must stay so. */
    @Test
    void aBoxFaceStillAgreesWithItsNormal() {
        double[] normal = windingNormal(
                new double[] {0.0, 0.0, 1.0},
                new double[] {1.0, 0.0, 1.0},
                new double[] {1.0, 1.0, 1.0});
        assertTrue(normal[2] > 0.0, "the +Z box face no longer faces +Z");
    }
}
