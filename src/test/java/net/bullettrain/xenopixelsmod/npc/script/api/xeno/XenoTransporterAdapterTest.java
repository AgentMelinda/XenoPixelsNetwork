package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.transport.TransportDestination;
import net.bullettrain.xenopixelsmod.npc.transport.TransportNetwork;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.entity.data.role.IRoleTransporter.ITransportLocation;

import static org.junit.jupiter.api.Assertions.*;

class XenoTransporterAdapterTest {
    private static TransportDestination destination(String id, TransportDestination.Unlock unlock) {
        return new TransportDestination(id, "West City", "minecraft:the_nether",
                -1.25, 70.9, 14.5, 90, unlock);
    }

    @Test void missingAndUnfinishedNetworksHaveNoLocation() {
        assertNull(XenoTransporterAdapter.location(null));
        TransportNetwork network = new TransportNetwork("cities", "Cities");
        network.add(TransportDestination.empty());
        assertNull(XenoTransporterAdapter.location(network));
    }

    @Test void singleDestinationUsesActualDimensionBlockCoordinatesAndUnlockRule() {
        TransportNetwork network = new TransportNetwork("cities", "Cities");
        network.add(TransportDestination.empty());
        network.add(destination("42", TransportDestination.Unlock.VISITED));
        ITransportLocation location = XenoTransporterAdapter.location(network);
        assertNotNull(location);
        assertEquals(42, location.getId());
        assertEquals("West City", location.getName());
        assertEquals("minecraft:the_nether", location.getDimension());
        assertEquals(-2, location.getX());
        assertEquals(70, location.getY());
        assertEquals(14, location.getZ());
        assertEquals(0, location.getType());
        network.set(1, destination("84", TransportDestination.Unlock.ALWAYS));
        assertEquals(42, location.getId());
        assertEquals(84, XenoTransporterAdapter.location(network).getId());
        assertEquals(1, XenoTransporterAdapter.location(network).getType());
    }

    @Test void arbitraryNegativeOrOverflowingIdsDoNotAcquireInventedNumbers() {
        for (String id : new String[]{"west_city", "-1", "2147483648"}) {
            TransportNetwork network = new TransportNetwork("cities", "Cities");
            network.add(destination(id, TransportDestination.Unlock.ALWAYS));
            assertEquals(-1, XenoTransporterAdapter.location(network).getId());
        }
    }

    @Test void multipleUsableDestinationsRefuseInsteadOfChoosingFirst() {
        TransportNetwork network = new TransportNetwork("cities", "Cities");
        network.add(destination("1", TransportDestination.Unlock.ALWAYS));
        network.add(destination("2", TransportDestination.Unlock.VISITED));
        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
                () -> XenoTransporterAdapter.location(network));
        assertTrue(error.getMessage().contains("multiple destinations"));
    }
}
