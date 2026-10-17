package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.space.PhysicalPlanetBindings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallPlanetGravityBindingsTest {
    @Test void filtersAndReplacesClientBindingsAndClearsOnDisconnect() {
        UUID ignored = UUID.randomUUID();
        UUID retained = UUID.randomUUID();
        SmallPlanetGravity.bindClient(new PhysicalPlanetBindings(List.of(
                new PhysicalPlanetBindings.Entry("xenopixelsmod:non_walkable", ignored, 9),
                new PhysicalPlanetBindings.Entry("xenopixelsmod:walkable", retained, 10)), true));
        assertEquals(Map.of(retained, 10), SmallPlanetGravity.clientBindings());
        assertTrue(SmallPlanetGravity.isClientEnabled());

        UUID replacement = UUID.randomUUID();
        SmallPlanetGravity.bindClient(new PhysicalPlanetBindings(List.of(
                new PhysicalPlanetBindings.Entry("xenopixelsmod:replacement", replacement, 24)), false));
        assertEquals(Map.of(replacement, 24), SmallPlanetGravity.clientBindings());
        assertFalse(SmallPlanetGravity.isClientEnabled());

        SmallPlanetGravity.clearClient();
        assertEquals(Map.of(), SmallPlanetGravity.clientBindings());
        assertFalse(SmallPlanetGravity.isClientEnabled());
    }
}
