package net.bullettrain.xenopixelsmod.combat.v3.technique;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class V3RushGeometryTest {
    @Test void ultimateOpeningUsesExactTenBlockGapAndFiniteFallback(){
        Vec3 origin=new Vec3(4,20,-8),victim=new Vec3(7,24,-8);
        Vec3 landing=V3RushGeometry.ultimateOpening(origin,victim,Vec3.ZERO);
        assertEquals(10,origin.distanceTo(landing),1e-6);
        assertEquals(new Vec3(10,28,-8),landing);
        assertEquals(origin.add(0,0,10),V3RushGeometry.ultimateOpening(origin,origin,Vec3.ZERO));
    }
    @Test void raisedVictimSetsTheContactBodyLevelAndStandOff() {
        Vec3 point = V3RushGeometry.contactPosition(Vec3.ZERO, 2, new Vec3(0, 8, 10), 4, new Vec3(0, 0, 1));
        assertEquals(9, point.y, 1e-6);
        assertEquals(8, point.z, 1e-6);
        assertEquals(2, point.distanceTo(new Vec3(0, 9, 10)), 1e-6);
    }

    @Test void overlappingAndVerticalTargetsHaveAFiniteStandOff() {
        Vec3 point = V3RushGeometry.contactPosition(Vec3.ZERO, 2, new Vec3(0, 8, 0), 2, new Vec3(1, 0, 0));
        assertEquals(new Vec3(-2, 8, 0), point);
        assertEquals(new Vec3(0, 0, -2), V3RushGeometry.contactPosition(Vec3.ZERO, 2, Vec3.ZERO, 2, Vec3.ZERO));
    }

    @Test void projectileAimUsesTheBodyInsteadOfTargetFeet() {
        Vec3 eye = new Vec3(0, 1.6, 0), targetCenter = new Vec3(0, 4, 15);
        Vec3 direction = V3RushGeometry.aim(eye, targetCenter, new Vec3(0, -1, 0));
        assertTrue(direction.y > 0);
        assertEquals(targetCenter, eye.add(direction.scale(eye.distanceTo(targetCenter))));
        assertEquals(new Vec3(0, 0, 1), V3RushGeometry.aim(eye, eye, Vec3.ZERO));
    }
}
