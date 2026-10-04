package net.bullettrain.xenopixelsmod.network;

import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The controller mode rides on the end of {@code SyncServerConfigPacket}. This codec is positional,
 * so the test is a full round trip: the failure worth catching is an older field shifting, not
 * the new one alone.
 */
class SyncServerConfigPacketControllerModeTest {

    private static XenoServerConfig.Data roundTrip(XenoServerConfig.Data sent) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SyncServerConfigPacket.encode(new SyncServerConfigPacket(sent), buf);
        XenoServerConfig.Data received = SyncServerConfigPacket.decode(buf).data();
        assertEquals(0, buf.readableBytes(),
                "the decoder left bytes on the buffer, so encode and decode disagree");
        return received;
    }

    @Test
    void manualModeSurvivesTheWire() {
        XenoServerConfig.Data sent = new XenoServerConfig.Data();
        sent.combatControllerMode = "bt3_manual";
        sent.npcNumericDamage = 42.0f;
        XenoServerConfig.Data received = roundTrip(sent);
        assertEquals("bt3_manual", received.combatControllerMode);
        assertEquals(42.0f, received.npcNumericDamage, 1.0e-6f);
    }

    @Test
    void defaultDataSyncsAsLegacy() {
        XenoServerConfig.Data received = roundTrip(new XenoServerConfig.Data());
        assertEquals("legacy", received.combatControllerMode);
    }

    @Test
    void garbageModeIsNormalizedBeforeItLeavesTheServer() {
        XenoServerConfig.Data sent = new XenoServerConfig.Data();
        sent.combatControllerMode = "definitely_not_a_mode";
        assertEquals("legacy", roundTrip(sent).combatControllerMode);
        sent.combatControllerMode = null;
        assertEquals("legacy", roundTrip(sent).combatControllerMode);
    }

    /** The client decides whether to draw the vanilla thruster plume from these two flags. */
    @Test
    void effectSwitchesTheClientNeedsSurviveTheWire() {
        XenoServerConfig.Data sent = new XenoServerConfig.Data();
        sent.effekseerEnabled = true;
        sent.effekseerShipThrusters = false;
        XenoServerConfig.Data received = roundTrip(sent);
        assertEquals(true, received.effekseerEnabled);
        assertEquals(false, received.effekseerShipThrusters);
        sent.effekseerEnabled = false;
        sent.effekseerShipThrusters = true;
        received = roundTrip(sent);
        assertEquals(false, received.effekseerEnabled);
        assertEquals(true, received.effekseerShipThrusters);
    }
}
