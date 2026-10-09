package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.combat.v2.V2Direction;
import net.bullettrain.xenopixelsmod.combat.v2.V2Input;
import net.bullettrain.xenopixelsmod.combat.v2.V2State;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboInput;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The v2 wire contract. The two v2 packets were appended in protocol 102; the enums they carry
 * travel as ordinals, so their existing order is pinned and new values may only be appended.
 */
class CombatV2ProtocolTest {

    @Test
    void protocolMovedForTheV2Packets() {
        assertTrue(ProtocolVersion.current() >= 102,
                "CombatV2InputPacket and CombatV2StatePacket need protocol 102 or later");
    }

    @Test void dashWindowAppendsToStateAndMovesExactProtocol() throws IOException {
        assertTrue(ProtocolVersion.current() >= 106);
        String packet = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/network/packet", "CombatV2StatePacket.java"));
        int counter = packet.indexOf("buf.writeVarInt(counterAttackerId);");
        int dash = packet.indexOf("buf.writeVarInt(dashTicksLeft);");
        int target = packet.indexOf("buf.writeVarInt(dashTargetId);");
        assertTrue(counter > 0 && dash > counter && target > dash);
    }

    @Test
    void v2PacketsAreRegisteredAfterEveryOlderPacket() throws IOException {
        String source = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/network", "ModNetwork.java"),
                StandardCharsets.UTF_8);
        int input = source.indexOf("CombatV2InputPacket.class, id++");
        int state = source.indexOf("CombatV2StatePacket.class, id++");
        int previousLast = source.indexOf("SecondAuraStatePacket.class, id++");
        assertTrue(previousLast > 0 && input > previousLast && state > input,
                "v2 packets must be appended, never inserted: sequential ids would shift");
    }

    /**
     * The two v2 packets changed after 102 without a packet being added: the state packet grew a
     * field and new values were appended to the enums it and the input packet carry. That is a
     * wire change, so the number moved.
     */
    @Test
    void theV2PacketChangesSince102MovedTheProtocol() throws IOException {
        assertTrue(ProtocolVersion.current() >= 103,
                "CombatV2StatePacket's counter attacker id needs protocol 103 or later");
        String packet = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/network/packet", "CombatV2StatePacket.java"),
                StandardCharsets.UTF_8);
        int grab = packet.indexOf("buf.writeVarInt(grabTicksLeft);");
        int counter = packet.indexOf("buf.writeVarInt(counterAttackerId);");
        assertTrue(grab > 0 && counter > grab, "the new field must be written last");
    }

    @Test
    void wireOrdinalsAreStable() {
        assertEquals(0, V2Input.LIGHT_PRESS.ordinal());
        assertEquals(1, V2Input.LIGHT_HOLD.ordinal());
        assertEquals(2, V2Input.HEAVY_PRESS.ordinal());
        assertEquals(3, V2Input.HEAVY_HOLD.ordinal());
        assertEquals(4, V2Input.GRAB.ordinal());
        assertEquals(5, V2Input.STEP.ordinal());
        assertEquals(6, V2Input.CHASE.ordinal());
        assertEquals(7, V2Input.CHASE_STOP.ordinal());
        assertEquals(8, V2Input.DRAGON_DASH.ordinal());
        assertEquals(9, V2Input.CINEMATIC_RUSH.ordinal());
        assertEquals(10, V2Input.COUNTER.ordinal());
        assertEquals(11, V2Input.VANISH.ordinal());
        assertEquals(12, V2Input.LIGHT_CHARGE_START.ordinal());
        assertEquals(13, V2Input.HEAVY_CHARGE_START.ordinal());
        assertEquals(14, V2Input.CHARGE_CANCEL.ordinal());
        assertTrue(ProtocolVersion.current() >= 105);

        assertEquals(0, V2Direction.NONE.ordinal());
        assertEquals(1, V2Direction.FORWARD.ordinal());
        assertEquals(2, V2Direction.BACK.ordinal());
        assertEquals(3, V2Direction.LEFT.ordinal());
        assertEquals(4, V2Direction.RIGHT.ordinal());
        assertEquals(5, V2Direction.UP.ordinal());
        assertEquals(6, V2Direction.DOWN.ordinal());

        assertEquals(0, V2State.NEUTRAL.ordinal());
        assertEquals(1, V2State.ATTACK.ordinal());
        assertEquals(2, V2State.STEP.ordinal());
        assertEquals(3, V2State.TRAVEL.ordinal());
        assertEquals(4, V2State.RUSH.ordinal());
        assertEquals(5, V2State.GRAB_STARTUP.ordinal());
        assertEquals(6, V2State.GRAB_HOLD.ordinal());
        assertEquals(7, V2State.GRABBED.ordinal());
        assertEquals(8, V2State.STRIKE.ordinal());
        assertEquals(9, V2State.CHARGE.ordinal());

        assertEquals(1, ComboInput.LIGHT.bit());
        assertEquals(2, ComboInput.HEAVY.bit());
        assertEquals(4, ComboInput.GRAB.bit());
        assertEquals(8, ComboInput.RUSH.bit());
    }
}
