package net.bullettrain.xenopixelsmod.space.chart;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ManualBodyPositionTest {
    private static CelestialBody body(String id,String json) {
        return ChartDefinition.parse(id,JsonParser.parseString(json).getAsJsonObject());
    }
    private static ChartRegistry chart() {
        var chart=new ChartRegistry();
        chart.rebuild(List.of(body("demo:planet","""
                {"dimension":"minecraft:overworld","position":{"x":100},"erasable":false}
                """),body("demo:moon","""
                {"parent":"demo:planet","position":{"x":10}}
                """)));
        chart.evaluate(0);return chart;
    }
    @Test void manualPositionIsAbsoluteAndChildrenFollowWithoutChangingBodyIdentity() {
        var c=chart();var planet=c.byId("demo:planet");
        c.moveBody("demo:planet",500,20,30);c.evaluate(0);
        assertEquals(500,c.x(c.slotOf("demo:planet")));
        assertEquals(510,c.x(c.slotOf("demo:moon")));
        assertEquals(20,c.y(c.slotOf("demo:moon")));
        assertSame(planet,c.byId("demo:planet"));assertFalse(planet.erasable());
        assertEquals("minecraft:overworld",planet.dimensionId());assertEquals(2,c.size());
        assertTrue(c.manuallyPositioned(c.slotOf("demo:planet")));
    }
    @Test void movingAChildDoesNotAddItsParentPositionAndResumeRestoresAuthoredHierarchy() {
        var c=chart();c.moveBody("demo:moon",50,0,0);c.evaluate(0);
        assertEquals(50,c.x(c.slotOf("demo:moon")));
        c.resumeBody("demo:moon");c.evaluate(0);
        assertEquals(110,c.x(c.slotOf("demo:moon")));
        assertFalse(c.manuallyPositioned(c.slotOf("demo:moon")));
    }
    @Test void manualOrbitFreezesAndResumeUsesCurrentEphemerisWithVelocity() {
        var c=new ChartRegistry();c.rebuild(List.of(body("demo:orbit","""
                {"orbit":{"semi_major":100,"period_seconds":40}}
                """)));
        c.moveBody("demo:orbit",7,8,9);c.evaluate(10);int s=c.slotOf("demo:orbit");
        assertEquals(7,c.x(s));assertEquals(0,c.velocityX(s));assertEquals(0,c.velocityZ(s));
        c.resumeBody("demo:orbit");c.evaluate(10);
        assertEquals(100,Math.sqrt(c.x(s)*c.x(s)+c.y(s)*c.y(s)+c.z(s)*c.z(s)),1e-8);
        assertTrue(Math.sqrt(c.velocityX(s)*c.velocityX(s)+c.velocityY(s)*c.velocityY(s)
                +c.velocityZ(s)*c.velocityZ(s))>0);
    }
    @Test void invalidOrUnknownRequestsCannotPartiallyChangeTheChart() {
        var c=chart();
        assertThrows(IllegalArgumentException.class,()->c.moveBody("demo:missing",1,2,3));
        assertThrows(IllegalArgumentException.class,()->c.resumeBody("demo:missing"));
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,30_000_001})
            assertThrows(IllegalArgumentException.class,()->c.moveBody("demo:planet",1,bad,3));
        c.evaluate(0);assertEquals(100,c.x(c.slotOf("demo:planet")));
        assertFalse(c.manuallyPositioned(c.slotOf("demo:planet")));assertEquals(2,c.size());
    }
    @Test void reloadCarriesOverridesByIdentityAndDropsMissingBodies() {
        var old=chart();old.moveBody("demo:planet",700,0,0);old.moveBody("demo:moon",900,0,0);
        var replacement=new ChartRegistry();replacement.rebuild(List.of(old.byId("demo:planet")));
        replacement.copyManualPositionsFrom(old);replacement.evaluate(5);
        assertEquals(700,replacement.x(0));assertEquals(1,replacement.size());
        assertTrue(replacement.manuallyPositioned(0));assertEquals(-1,replacement.slotOf("demo:moon"));
    }
    @Test void gravityInspectionUsesMovedPositionRatherThanAuthoredPosition() {
        var c=new ChartRegistry();c.rebuild(List.of(body("demo:gravity","""
                {"position":{"x":100},"radius_blocks":10,"gravity":{"surface_ms2":1}}
                """)));c.evaluate(0);
        assertEquals(.01,c.inspectAt(0,0,0).ax(),1e-12);
        c.moveBody("demo:gravity",200,0,0);c.evaluate(0);
        assertEquals(.0025,c.inspectAt(0,0,0).ax(),1e-12);
    }
}
