package net.bullettrain.xenopixelsmod.client.flight;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlightInputChangesTest {
    private static FlightInputChanges.Frame frame(double pitch, double roll, double yaw, boolean mouse, boolean auto) {
        return new FlightInputChanges.Frame(1, .5, 0, 179.9, 0, 0, pitch, roll, yaw, mouse, false, auto);
    }
    @Test void stickYawSendsWithoutAttitudeChangeAndReleaseCannotWaitForKeepalive() {
        var idle = frame(0, 0, 0, false, false);
        assertTrue(frame(0, 0, .25, false, false).differs(idle));
        assertTrue(idle.differs(frame(0, 0, .005, false, false)));
        assertTrue(frame(.25, 0, 0, false, false).differs(idle));
        assertTrue(frame(0, .25, 0, false, false).differs(idle));
        assertFalse(idle.differs(idle));
    }
    @Test void modeAndSeatTransitionsSendEvenWhenStill() {
        var idle = frame(0, 0, 0, false, false);
        assertTrue(frame(0, 0, 0, true, false).differs(idle));
        assertTrue(frame(0, 0, 0, false, true).differs(idle));
        assertTrue(idle.differs(null));
        assertTrue(new FlightInputChanges.Frame(2, .5, 0, 179.9, 0, 0, 0, 0, 0, false, false, false).differs(idle));
        assertFalse(new FlightInputChanges.Frame(1, .5, 0, -179.9, 0, 0, 0, 0, 0, false, false, false).differs(idle));
    }
}
