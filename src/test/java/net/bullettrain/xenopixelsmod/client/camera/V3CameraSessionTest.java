package net.bullettrain.xenopixelsmod.client.camera;

import java.util.List;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3CameraBeat;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3CameraPacket;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V3CameraSessionTest {
    private final UUID session = UUID.randomUUID(), cast = UUID.randomUUID(), target = UUID.randomUUID();
    private CombatV3CameraPacket start(UUID cast) {
        return new CombatV3CameraPacket(session, cast, target, 42, 40, 0, List.of(
                new V3CameraBeat(0, 40, new Vec3(4, 2, -3), Vec3.ZERO, 0.5f, V3CameraBeat.Easing.CUT)));
    }
    @Test void rejectsUnapprovedSessionTargetAndNonCinematicState() {
        var guard = new V3CameraSession();
        assertFalse(guard.apply(start(cast), UUID.randomUUID(), target, true));
        assertFalse(guard.apply(start(cast), session, UUID.randomUUID(), true));
        assertFalse(guard.apply(start(cast), session, target, false));
        assertNull(guard.active());
        assertTrue(guard.apply(start(cast), session, target, true));
    }
    @Test void stoppedCastCannotRestartAndStaleStopCannotEndNextCast() {
        var guard = new V3CameraSession();
        var stop = CombatV3CameraPacket.stop(session, cast, target, 42);
        assertTrue(guard.apply(start(cast), session, target, true));
        assertTrue(guard.apply(stop, session, target, true));
        assertFalse(guard.apply(start(cast), session, target, true));
        UUID next = UUID.randomUUID();
        assertTrue(guard.apply(start(next), session, target, true));
        assertFalse(guard.apply(stop, session, target, true));
        assertEquals(next, guard.active().cast());
    }
    @Test void updatesOnlyTheActiveCastAndClearRetiresIt() {
        var guard = new V3CameraSession();
        assertTrue(guard.apply(start(cast), session, target, true));
        assertFalse(guard.apply(start(UUID.randomUUID()), session, target, true));
        var pause = new CombatV3CameraPacket(session, cast, target, 42, 40, 10, true, start(cast).beats());
        assertTrue(guard.apply(pause, session, target, true));
        assertTrue(guard.active().paused());
        guard.clear();
        assertFalse(guard.apply(start(cast), session, target, true));
        guard.reset();
        assertTrue(guard.apply(start(cast), session, target, true));
    }
}
