package net.bullettrain.xenopixelsmod.block.custom;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WingPanelPoseTest {

    @Test
    void forkFacingMatchesBlockstateJsonRotations() {
        assertArrayEquals(new int[] {180, 0, 0}, WingPanelPose.facingOrient(Direction.UP));
        assertArrayEquals(new int[] {0, 0, 0}, WingPanelPose.facingOrient(Direction.DOWN));
        assertArrayEquals(new int[] {270, 0, 0}, WingPanelPose.facingOrient(Direction.NORTH));
        assertArrayEquals(new int[] {270, 180, 0}, WingPanelPose.facingOrient(Direction.SOUTH));
        assertArrayEquals(new int[] {270, 270, 0}, WingPanelPose.facingOrient(Direction.WEST));
        assertArrayEquals(new int[] {270, 90, 0}, WingPanelPose.facingOrient(Direction.EAST));
    }

    @Test
    void forkXenowingExtraStacksOnFacing() {
        int[] untouched = WingPanelPose.composeForkOrient(Direction.NORTH, new int[] {0, 0, 0});
        assertArrayEquals(WingPanelPose.facingOrient(Direction.NORTH), untouched);
        assertArrayEquals(new int[] {360, 0, 0},
                WingPanelPose.composeForkOrient(Direction.NORTH, new int[] {90, 0, 0}));
        assertArrayEquals(new int[] {180, 45, -15},
                WingPanelPose.composeForkOrient(Direction.UP, new int[] {0, 45, -15}));
    }

    @Test
    void horizontalAndVerticalFacingsAreNotTheSameMount() {
        int[] up = WingPanelPose.facingOrient(Direction.UP);
        int[] north = WingPanelPose.facingOrient(Direction.NORTH);
        int[] east = WingPanelPose.facingOrient(Direction.EAST);
        assertFalse(java.util.Arrays.equals(up, north));
        assertFalse(java.util.Arrays.equals(north, east));
    }

    @Test
    void defaultStubFollowsModelPlusZ() {
        assertEquals(Direction.SOUTH, WingPanelPose.stubWorldDir(Direction.DOWN, 0));
        assertEquals(Direction.NORTH, WingPanelPose.stubWorldDir(Direction.UP, 0));
        assertEquals(Direction.UP, WingPanelPose.stubWorldDir(Direction.NORTH, 0));
    }

    @Test
    void hingeTowardAimsStubAtPlayerEdge() {
        assertEquals(0, WingPanelPose.hingeToward(Direction.DOWN, Direction.SOUTH, Direction.SOUTH));
        assertEquals(180, WingPanelPose.hingeToward(Direction.UP, Direction.SOUTH, Direction.SOUTH));
        assertEquals(180, WingPanelPose.hingeToward(Direction.NORTH, Direction.SOUTH, Direction.NORTH));
        assertEquals(Direction.SOUTH, WingPanelPose.stubWorldDir(Direction.UP,
                WingPanelPose.hingeToward(Direction.UP, Direction.SOUTH, Direction.SOUTH)));
        assertEquals(Direction.DOWN, WingPanelPose.stubWorldDir(Direction.SOUTH,
                WingPanelPose.hingeToward(Direction.SOUTH, Direction.SOUTH, Direction.NORTH)));
    }
}
