package net.bullettrain.xenopixelsmod.client.combat.v3;

import java.util.UUID;
import net.bullettrain.xenopixelsmod.combat.v3.V3State;
import net.bullettrain.xenopixelsmod.combat.v3.V3TargetSnapshot;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3StatePacket;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V3ClientStateTest {
    @AfterEach void cleanup() { V3ClientState.reset(); }
    private CombatV3StatePacket packet(UUID session, UUID target, long revision, int ack) {
        return new CombatV3StatePacket(session, V3State.IDLE,
                target == null ? null : new V3TargetSnapshot(target, 4, Vec3.ZERO, Vec3.ZERO, revision), ack, 0, 0, 0);
    }
    @Test void sameSessionAckCannotRewindOutgoingSequenceAndRetiredSessionIsIgnored() {
        UUID session = UUID.randomUUID();
        V3ClientState.apply(packet(session, null, 0, -1));
        assertEquals(0, V3ClientState.nextSequence());
        assertEquals(1, V3ClientState.nextSequence());
        V3ClientState.apply(packet(session, null, 0, 0));
        assertEquals(2, V3ClientState.nextSequence());
        UUID next = UUID.randomUUID();
        V3ClientState.apply(packet(next, null, 0, -1));
        assertEquals(0, V3ClientState.nextSequence());
        V3ClientState.apply(packet(session, null, 0, 100));
        assertEquals(next, V3ClientState.session());
        assertEquals(1, V3ClientState.nextSequence());
    }
    @Test void targetClearRetainsWatermarkAndNewSessionResetsIt() {
        UUID session = UUID.randomUUID(), target = UUID.randomUUID();
        V3ClientState.apply(packet(session, target, 7, 0));
        V3ClientState.apply(packet(session, target, 6, 0));
        assertEquals(7, V3ClientState.target().revision());
        V3ClientState.apply(packet(session, UUID.randomUUID(), 7, 0));
        assertEquals(target, V3ClientState.target().target(), "reused entity id cannot replace UUID");
        V3ClientState.apply(packet(session, null, 0, 1));
        V3ClientState.apply(packet(session, target, 7, 1));
        assertNull(V3ClientState.target(), "old snapshot cannot resurrect cleared target");
        V3ClientState.apply(packet(session, target, 8, 2));
        assertEquals(8, V3ClientState.target().revision());
        V3ClientState.clear();
        V3ClientState.apply(packet(session, target, 8, 2));
        assertNull(V3ClientState.target());
        V3ClientState.apply(packet(UUID.randomUUID(), target, 0, -1));
        assertEquals(0, V3ClientState.target().revision());
    }
    @Test void staleAcknowledgementCannotClearNewerTarget() {
        UUID session = UUID.randomUUID(), target = UUID.randomUUID();
        V3ClientState.apply(packet(session, target, 4, 10));
        V3ClientState.apply(packet(session, null, 0, 9));
        assertEquals(target, V3ClientState.target().target());
        assertEquals(11, V3ClientState.nextSequence());
    }
    @Test void equalRevisionCannotReplaceMotionButMayUpdateChargeAndSequenceExhaustionFailsClosed() {
        UUID session = UUID.randomUUID(), target = UUID.randomUUID();
        V3ClientState.apply(packet(session, target, 4, 0));
        var motion = new V3TargetSnapshot(target, 4, new Vec3(100, 0, 0), Vec3.ZERO, 4);
        V3ClientState.apply(new CombatV3StatePacket(session, V3State.CHARGING_PUNCH, motion, 0, 12, 0, 0));
        assertEquals(Vec3.ZERO, V3ClientState.target().position());
        assertEquals(12, V3ClientState.state().chargeTicks());
        V3ClientState.apply(packet(session, target, 4, Integer.MAX_VALUE));
        assertEquals(-1, V3ClientState.nextSequence(), "Exhausted sequence cannot wrap to a replayed request");
    }
}
