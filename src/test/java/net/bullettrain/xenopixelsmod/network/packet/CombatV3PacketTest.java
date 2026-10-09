package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.network.ProtocolVersion;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.bullettrain.xenopixelsmod.combat.v3.*;
import net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState;
import static org.junit.jupiter.api.Assertions.*;

class CombatV3PacketTest {
    @Test void inputRoundTripsSessionTargetAndSequenceWithoutChargeAuthority() {
        var packet = new CombatV3InputPacket(V3Input.HEAVY_CHARGE_START, UUID.randomUUID(), UUID.randomUUID(), V3Direction.BACK, 123);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buf);
            assertEquals(packet, new CombatV3InputPacket(buf));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }
    @Test void stateAndNormalizedConfigRoundTripAndClientDoesNotMutateServerConfig() {
        var target = new V3TargetSnapshot(UUID.randomUUID(), 42, new Vec3(1, 2, 3), new Vec3(0.5, 0, -1), 7);
        var packet = new CombatV3StatePacket(UUID.randomUUID(), V3State.CHARGING_KICK, target, 13, 20, 8, 24);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        var original = V3Config.get();
        try {
            packet.encode(buf);
            assertEquals(packet, new CombatV3StatePacket(buf));
            assertEquals(0, buf.readableBytes());
            var config = new CombatV3ConfigPacket(new V3Config.Values(9999, -1, 100001, 7.5, 33, 4.25,
                    17, 90, 21, 48, false, true, false, 29, 0.4, true, 55, 18, 96));
            config.encode(buf);
            var decoded = new CombatV3ConfigPacket(buf);
            assertEquals(999, decoded.values().dragonDashRange());
            assertEquals(0, decoded.values().heavyAttackerStaminaCost());
            assertEquals(100000, decoded.values().heavyVictimStaminaDrain());
            // 2026-10-08 tuning tail round-trips in order and already normalized.
            assertEquals(config.values(), decoded.values());
            assertEquals(7.5, decoded.values().dragonDashSpeed());
            assertEquals(17, decoded.values().dragonDashFollowCooldownTicks());
            assertFalse(decoded.values().strikeCinematicCamera());
            assertTrue(decoded.values().dashCamera());
            assertFalse(decoded.values().attackSounds());
            assertEquals(29, decoded.values().heavyChargeLaunchDistance());
            assertEquals(0.4, decoded.values().attackSoundVolume());
            assertTrue(decoded.values().strikeKiRequireLock());
            assertEquals(55, decoded.values().strikeKiRange());
            assertEquals(18, decoded.values().grabThrowDistance());
            assertEquals(96, decoded.values().strikeCameraHoldTicks());
            assertEquals(0, decoded.values().strikeCinematicCameraHoldTicks(),
                    "legacy packet without opening hold defaults to 0");
            // Append-only: a second encode/decode with the opening field round-trips.
            var withOpening = new CombatV3ConfigPacket(new V3Config.Values(9999, -1, 100001, 7.5, 33, 4.25,
                    17, 90, 21, 48, false, true, false, 29, 0.4, true, 55, 18, 96, 40));
            withOpening.encode(buf);
            var decodedOpening = new CombatV3ConfigPacket(buf);
            assertEquals(96, decodedOpening.values().strikeCameraHoldTicks());
            assertEquals(40, decodedOpening.values().strikeCinematicCameraHoldTicks());
            assertTrue(decodedOpening.values().strikeRequireLock(), "legacy packet defaults melee lock on");
            var lockless = new CombatV3ConfigPacket(new V3Config.Values(9999, -1, 100001, 7.5, 33, 4.25,
                    17, 90, 21, 48, false, true, false, 29, 0.4, true, 55, 18, 96, 40, false));
            lockless.encode(buf);
            var decodedLockless = new CombatV3ConfigPacket(buf);
            assertFalse(decodedLockless.values().strikeRequireLock());
            V3ClientState.apply(decoded);
            V3ClientState.apply(packet);
            assertEquals(original, V3Config.get());
            assertEquals(packet.session(), V3ClientState.session());
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); V3ClientState.reset(); }
    }
    @Test void invalidEnumsSequencesAndNonFiniteVectorsAreRefused() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(999);
            assertThrows(IllegalArgumentException.class, () -> new CombatV3InputPacket(buf));
            buf.clear();
            buf.writeVarInt(V3Input.LIGHT_TAP.ordinal()); buf.writeUUID(UUID.randomUUID());
            buf.writeBoolean(false); buf.writeVarInt(999); buf.writeVarInt(0);
            assertThrows(IllegalArgumentException.class, () -> new CombatV3InputPacket(buf));
            assertThrows(IllegalArgumentException.class, () -> new CombatV3InputPacket(V3Input.LIGHT_TAP, UUID.randomUUID(), null, V3Direction.NONE, -1));
            assertThrows(IllegalArgumentException.class, () -> new V3TargetSnapshot(UUID.randomUUID(), 1, new Vec3(Double.NaN, 0, 0), Vec3.ZERO, 1));
            buf.clear();
            buf.writeUUID(UUID.randomUUID()); buf.writeVarInt(V3State.IDLE.ordinal()); buf.writeBoolean(true);
            buf.writeUUID(UUID.randomUUID()); buf.writeVarInt(1); buf.writeDouble(Double.POSITIVE_INFINITY);
            buf.writeDouble(0); buf.writeDouble(0); buf.writeDouble(0); buf.writeDouble(0); buf.writeDouble(0); buf.writeVarLong(0);
            buf.writeVarInt(0); buf.writeVarInt(0); buf.writeVarInt(0); buf.writeVarInt(0);
            assertThrows(IllegalArgumentException.class, () -> new CombatV3StatePacket(buf));
            assertThrows(IllegalArgumentException.class, () -> new CombatV3StatePacket(UUID.randomUUID(), V3State.IDLE, null, -1, 201, 0, 0));
            buf.clear(); buf.writeDouble(Double.NaN); buf.writeDouble(10); buf.writeDouble(10);
            buf.writeDouble(3); buf.writeDouble(20); buf.writeDouble(2.5); buf.writeVarInt(8); buf.writeVarInt(60);
            buf.writeDouble(15); buf.writeDouble(32); buf.writeBoolean(true); buf.writeBoolean(false); buf.writeBoolean(true);
            buf.writeDouble(15); buf.writeDouble(1);
            assertThrows(IllegalArgumentException.class, () -> new CombatV3ConfigPacket(buf));
            // A truncated tail is refused the same way, never as a raw buffer index error.
            buf.clear(); buf.writeDouble(24); buf.writeDouble(10); buf.writeDouble(10);
            assertThrows(IllegalArgumentException.class, () -> new CombatV3ConfigPacket(buf));
        } finally { buf.release(); }
    }
    @Test void v3PacketsAppendAfterTheExistingTailAndBumpProtocol() throws Exception {
        assertTrue(ProtocolVersion.current() >= 107);
        String source = Files.readString(RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/network", "ModNetwork.java"));
        int tail = source.indexOf("UltimateFinisherCameraPacket.class, id++");
        int input = source.indexOf("CombatV3InputPacket.class, id++");
        int state = source.indexOf("CombatV3StatePacket.class, id++");
        int config = source.indexOf("CombatV3ConfigPacket.class, id++");
        assertTrue(tail > 0 && input > tail && state > input && config > state);
        int camera = source.indexOf("CombatV3CameraPacket.class, id++");
        assertTrue(camera > config);
        assertTrue(ProtocolVersion.current() >= 108);
    }
}
