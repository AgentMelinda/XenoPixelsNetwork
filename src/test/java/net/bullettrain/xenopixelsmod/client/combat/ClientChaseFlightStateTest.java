package net.bullettrain.xenopixelsmod.client.combat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClientChaseFlightStateTest {
    @AfterEach void reset() { ClientChaseFlightState.reset(); }
    @Test void requestCannotSuppressNativeFlightUntilServerAccepts() {
        ClientChaseFlightState.reset();
        ClientChaseFlightState.request();
        assertTrue(ClientChaseFlightState.isPending());
        assertFalse(ClientChaseFlightState.isActive());
        ClientChaseFlightState.setActive(true);
        assertFalse(ClientChaseFlightState.isPending());
        assertTrue(ClientChaseFlightState.isActive());
        ClientChaseFlightState.setActive(false);
        assertFalse(ClientChaseFlightState.isActive());
    }
    @Test void rejectionAndDisconnectClearPendingAndAcceptedState() {
        ClientChaseFlightState.request();
        ClientChaseFlightState.setActive(false);
        assertFalse(ClientChaseFlightState.isPending());
        assertFalse(ClientChaseFlightState.isActive());
        ClientChaseFlightState.setActive(true);
        ClientChaseFlightState.request();
        ClientChaseFlightState.reset();
        assertFalse(ClientChaseFlightState.isActive());
        assertFalse(ClientChaseFlightState.isPending());
    }
}
