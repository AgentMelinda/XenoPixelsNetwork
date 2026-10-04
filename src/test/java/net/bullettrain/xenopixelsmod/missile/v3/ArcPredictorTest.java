package net.bullettrain.xenopixelsmod.missile.v3;

import net.bullettrain.xenopixelsmod.missile.BallisticCalculator;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Guidance V3 predicts where the current arc lands; it has to agree with a plain tick-by-tick flight. */
class ArcPredictorTest {
    private static final double G = BallisticCalculator.toTickGravity(BallisticCalculator.EARTH_GRAVITY);

    /** Reference: one-tick steps with the same gravity and drag, until the arc drops through targetY. */
    private static Vec3 reference(Vec3 pos, Vec3 vel, double g, double drag, double targetY) {
        for (int t = 0; t < 200_000; t++) {
            Vec3 prevPos = pos;
            vel = ArcPredictor.dragged(vel.add(0, -g, 0), drag, pos.y, 1.0);
            pos = pos.add(vel);
            if (vel.y < 0 && prevPos.y >= targetY && pos.y < targetY) {
                double f = (prevPos.y - targetY) / (prevPos.y - pos.y);
                return prevPos.add(pos.subtract(prevPos).scale(f));
            }
        }
        return null;
    }

    private static void agrees(Vec3 pos, Vec3 vel, double g, double drag, double targetY) {
        Vec3 ref = reference(pos, vel, g, drag, targetY);
        ArcPredictor.Landing landing = ArcPredictor.predict(pos, vel, g, drag, targetY, 200_000);
        assertNotNull(ref);
        assertNotNull(landing);
        double range = Math.hypot(ref.x - pos.x, ref.z - pos.z);
        double allowed = Math.max(1.0, range * 0.002);
        assertTrue(landing.point().distanceTo(ref) <= allowed,
                "predicted " + landing.point() + " vs flown " + ref + " (allowed " + allowed + ")");
    }

    @Test
    void agreesWithAFlownArcShortLoftedLongAndLowGravity() {
        agrees(new Vec3(0, 64, 0), new Vec3(1.5, 1.2, 0.4), G, BallisticCalculator.DEFAULT_DRAG, 64);
        agrees(new Vec3(0, 64, 0), new Vec3(0.3, 4.0, 0.2), G, BallisticCalculator.DEFAULT_DRAG, 70);
        agrees(new Vec3(0, 80, 0), new Vec3(9.0, 9.0, 5.0), G, BallisticCalculator.DEFAULT_DRAG, 60);
        agrees(new Vec3(0, 64, 0), new Vec3(2.0, 2.0, 0), BallisticCalculator.toTickGravity(5.0), 0.0, 64);
    }

    @Test
    void anArcThatNeverComesDownToTheTargetHeightHasNoLanding() {
        assertNull(ArcPredictor.predict(new Vec3(0, 64, 0), new Vec3(1, 0.5, 0), G, 0.0, 200, 10_000));
    }

    @Test
    void aRoundAlreadyFallingBelowTheTargetHeightLandsWhereItIs() {
        ArcPredictor.Landing landing = ArcPredictor.predict(new Vec3(5, 40, 5), new Vec3(1, -1, 0), G, 0.0, 64, 1000);
        assertNotNull(landing);
        assertEquals(0, landing.ticks());
        assertEquals(5.0, landing.point().x, 1e-9);
    }
}
