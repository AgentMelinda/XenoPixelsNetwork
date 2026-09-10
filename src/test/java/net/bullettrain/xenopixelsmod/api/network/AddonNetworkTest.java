package net.bullettrain.xenopixelsmod.api.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddonNetworkTest {

    @BeforeEach
    @AfterEach
    void reset() {
        AddonNetwork.resetForTests();
    }

    @Test
    void protocolIsStableRegardlessOfRegistrationOrder() {
        register("example:b", SecondPacket.class, AddonPacketDirection.CLIENTBOUND);
        register("example:a", FirstPacket.class, AddonPacketDirection.SERVERBOUND);
        String firstOrder = AddonNetwork.protocolVersion();

        AddonNetwork.resetForTests();
        register("example:a", FirstPacket.class, AddonPacketDirection.SERVERBOUND);
        register("example:b", SecondPacket.class, AddonPacketDirection.CLIENTBOUND);
        assertEquals(firstOrder, AddonNetwork.protocolVersion());
    }

    @Test
    void duplicateIdsAndClassesAreRejected() {
        register("example:first", FirstPacket.class, AddonPacketDirection.SERVERBOUND);
        assertThrows(IllegalArgumentException.class,
                () -> register("example:first", SecondPacket.class, AddonPacketDirection.CLIENTBOUND));
        assertThrows(IllegalArgumentException.class,
                () -> register("example:second", FirstPacket.class, AddonPacketDirection.SERVERBOUND));
    }

    @Test
    void lateRegistrationIsRejected() {
        AddonNetwork.freezeForTests();
        assertThrows(IllegalStateException.class,
                () -> register("example:late", FirstPacket.class, AddonPacketDirection.SERVERBOUND));
    }

    @Test
    void sendHelpersRejectTheWrongDirectionBeforeNetworkUse() {
        register("example:first", FirstPacket.class, AddonPacketDirection.SERVERBOUND);
        register("example:second", SecondPacket.class, AddonPacketDirection.CLIENTBOUND);
        assertThrows(IllegalArgumentException.class,
                () -> AddonNetwork.sendToServer(new SecondPacket(2)));
        assertThrows(IllegalArgumentException.class,
                () -> AddonNetwork.sendToAll(new FirstPacket(1)));
    }

    private static <MSG> void register(String id, Class<MSG> type, AddonPacketDirection direction) {
        AddonNetwork.register(ResourceLocation.parse(id), type, direction,
                (message, buffer) -> {}, buffer -> null, (message, context) -> {});
    }

    private record FirstPacket(int value) {}
    private record SecondPacket(int value) {}
}
