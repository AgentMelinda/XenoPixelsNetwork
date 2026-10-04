package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MissileLookTargetTest {

    @Test
    void rangeMatchesComputerLookRay() {
        assertEquals(200.0, MissileLookTarget.RANGE);
    }

    @Test
    void worldPosWithoutLevelIsContainingBlock() {
        assertEquals(new BlockPos(10, 64, -4),
                MissileLookTarget.worldPos(null, new Vec3(10.2, 64.9, -3.1)));
    }

    @Test
    void fromPlayerNullIsMiss() {
        assertNull(MissileLookTarget.fromPlayer(null));
    }
}
