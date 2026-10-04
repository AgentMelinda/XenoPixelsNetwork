package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.block.custom.PanelRole;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ShipToolModeTest {

    @Test
    void airShiftCyclesEveryModeThenWraps() {
        ShipToolMode mode = ShipToolMode.LINKER;
        for (int i = 0; i < ShipToolMode.values().length; i++) {
            ShipToolMode next = mode.next();
            assertNotEquals(mode, next);
            mode = next;
        }
        assertEquals(ShipToolMode.LINKER, mode);
    }

    @Test
    void byNameParsesAndDefaultsToLinker() {
        assertEquals(ShipToolMode.FACING, ShipToolMode.byName("facing"));
        assertEquals(ShipToolMode.FACING, ShipToolMode.byName("facing_y"));
        assertEquals(ShipToolMode.FACING, ShipToolMode.byName("FACING_Z"));
        assertEquals(ShipToolMode.ORIENT, ShipToolMode.byName("orient"));
        assertEquals(ShipToolMode.LIT, ShipToolMode.byName("LIT"));
        assertEquals(ShipToolMode.LINKER, ShipToolMode.byName(null));
        assertEquals(ShipToolMode.LINKER, ShipToolMode.byName("nope"));
    }

    @Test
    void roleFacingAndAxisAdvanceInStableOrder() {
        assertEquals(PanelRole.FLAP, ShipToolActions.nextRole(PanelRole.NONE));
        assertEquals(PanelRole.NONE, ShipToolActions.nextRole(PanelRole.BRAKE));
        assertEquals(Direction.EAST, ShipToolActions.nextFacing(Direction.NORTH));
        assertEquals(Direction.NORTH, ShipToolActions.nextFacing(Direction.DOWN));
        assertEquals(Direction.WEST, ShipToolActions.nextFacingOnAxis(Direction.EAST, Direction.Axis.X));
        assertEquals(Direction.EAST, ShipToolActions.nextFacingOnAxis(Direction.WEST, Direction.Axis.X));
        assertEquals(Direction.UP, ShipToolActions.nextFacingOnAxis(Direction.NORTH, Direction.Axis.Y));
        assertEquals(Direction.DOWN, ShipToolActions.nextFacingOnAxis(Direction.UP, Direction.Axis.Y));
        assertEquals(Direction.SOUTH, ShipToolActions.nextFacingOnAxis(Direction.NORTH, Direction.Axis.Z));
        assertEquals(Direction.NORTH, ShipToolActions.nextFacingOnAxis(Direction.SOUTH, Direction.Axis.Z));
        assertEquals(Direction.Axis.Y, ShipToolActions.nextAxis(Direction.Axis.X));
        assertEquals(Direction.Axis.X, ShipToolActions.nextAxis(Direction.Axis.Z));
        assertEquals(ShipToolMode.ORIENT, ShipToolMode.FACING.next());
        assertEquals(ShipToolMode.AXIS, ShipToolMode.ORIENT.next());
    }

    @Test
    void litToggleIsSymmetricForCopycatIds() {
        // Mapping is verified without placing blocks: each counterpart must invert.
        assertSame(ShipToolMode.LIT, ShipToolMode.byName("lit"));
    }
}
