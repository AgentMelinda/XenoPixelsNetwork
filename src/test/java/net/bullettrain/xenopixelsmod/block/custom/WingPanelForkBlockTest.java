package net.bullettrain.xenopixelsmod.block.custom;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WingPanelForkBlockTest {

    @Test
    void liftNormalFollowsConfiguredFacing() {
        assertEquals(Direction.NORTH, WingPanelForkBlock.liftNormal(Direction.NORTH, Direction.Axis.Y));
        assertEquals(Direction.UP, WingPanelForkBlock.liftNormal(Direction.UP, Direction.Axis.Z));
        assertEquals(Direction.WEST, WingPanelForkBlock.liftNormal(Direction.WEST, Direction.Axis.X));
        assertEquals(Direction.DOWN, WingPanelForkBlock.liftNormal(Direction.DOWN, null));
    }

    @Test
    void liftNormalFallsBackToAxisWhenFacingMissing() {
        assertEquals(Direction.UP, WingPanelForkBlock.liftNormal(null, Direction.Axis.Y));
        assertEquals(Direction.SOUTH, WingPanelForkBlock.liftNormal(null, Direction.Axis.Z));
        assertEquals(Direction.EAST, WingPanelForkBlock.liftNormal(null, Direction.Axis.X));
        assertEquals(Direction.UP, WingPanelForkBlock.liftNormal(null, null));
    }
}
