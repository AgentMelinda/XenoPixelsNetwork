package net.bullettrain.xenopixelsmod.combat.v3.ki;

import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueCatalog;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V3AuthoredKiAnimationTest {
    @Test void sourceCastFirePairCanBeBoundToNativeController() {
        V3TechniqueCatalog.load();
        var attack = V3TechniqueCatalog.all().stream().filter(t -> t.sourceStartsMs().contains(505000L)).findFirst().orElseThrow();
        assertEquals("combat.xeno_bt3_v3_ref40_505", V3NativeKi.authoredAnimationPrefix(attack));
        assertEquals("kamehameha", attack.kiTechnique());
        assertEquals("kamehameha", V3NativeKi.originalTechniqueId(attack));
        assertTrue(V3NativeKi.pureKi(attack));
        var rush = V3TechniqueCatalog.all().stream().filter(t -> t.sourceStartsMs().contains(707000L)).findFirst().orElseThrow();
        assertNull(V3NativeKi.authoredAnimationPrefix(rush));
        assertFalse(V3NativeKi.pureKi(rush));
    }
}
