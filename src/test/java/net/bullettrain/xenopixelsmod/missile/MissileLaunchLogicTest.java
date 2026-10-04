package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileLaunchLogicTest {

    @Test
    void emptyTubeDoesNotLaunch() {
        assertFalse(net.bullettrain.xenopixelsmod.block.entity.MissileTubeBlockEntity.canLaunch(
                true, 0, ItemStack.EMPTY));
        assertFalse(net.bullettrain.xenopixelsmod.block.entity.MissileTubeBlockEntity.canLaunch(
                true, 0, new ItemStack(Items.TNT)));
        assertFalse(net.bullettrain.xenopixelsmod.block.entity.MissileTubeBlockEntity.canLaunch(
                false, 0, new ItemStack(Items.IRON_INGOT)));
        assertFalse(net.bullettrain.xenopixelsmod.block.entity.MissileTubeBlockEntity.canLaunch(
                true, 10, new ItemStack(Items.IRON_INGOT)));
    }

    @Test
    void ejectWaitsForClearanceAndOptionalWorldY() {
        Vec3 origin = new Vec3(0, -50, 0);
        Vec3 loft = new Vec3(0, 1, 0);
        assertFalse(MissileEject.finished(4, origin, origin.add(0, 30, 0), loft, 24, 0));
        assertFalse(MissileEject.finished(10, origin, origin.add(0, 10, 0), loft, 24, 0));
        assertTrue(MissileEject.finished(10, origin, origin.add(0, 24, 0), loft, 24, 0));
        assertFalse(MissileEject.finished(10, origin, origin.add(0, 24, 0), loft, 24, 80));
        assertTrue(MissileEject.finished(10, origin, new Vec3(0, 80, 0), loft, 24, 80));
        assertTrue(MissileEject.finished(MissileEject.MAX_EJECT_TICKS, origin, origin, loft, 24, 80));
    }

    @Test
    void speedOneIsSlowerThanSpeedTwenty() {
        assertTrue(MissileSpeed.accelFor(1) < MissileSpeed.accelFor(20));
        assertTrue(MissileSpeed.ticksFor(1) < MissileSpeed.ticksFor(20));
        assertEquals(MissileSpeed.accelFor(20), MissileSpeed.accelFor(99));
    }

    @Test
    void allSizesStackToSixtyFour() {
        for (MissileSize size : MissileSize.values()) {
            assertEquals(64, size.stackSize(), size.name());
        }
        assertEquals(64, MissileSize.maxStackSize());
    }

    @Test
    void visualLengthGrowsWithSizeTier() {
        assertTrue(MissileSize.SMALL.visualLength() < MissileSize.MEDIUM.visualLength());
        assertTrue(MissileSize.MEDIUM.visualLength() < MissileSize.LARGE.visualLength());
        assertTrue(MissileSize.LARGE.visualLength() < MissileSize.MEGA.visualLength());
        assertTrue(MissileSize.MEGA.visualRadius() > MissileSize.SMALL.visualRadius());
        assertEquals(3.0f, MissileSize.SMALL.visualLength());
        assertEquals(6.0f, MissileSize.MEDIUM.visualLength());
        assertEquals(10.0f, MissileSize.LARGE.visualLength());
        assertEquals(16.0f, MissileSize.MEGA.visualLength());
    }

    @Test
    void emptyWarheadIsInertAndTntScalesWithSize() {
        assertEquals(0f, MissileWarhead.explosionPower(ItemStack.EMPTY, MissileSize.MEGA));
        float small = MissileWarhead.explosionPower(new ItemStack(Items.TNT), MissileSize.SMALL);
        float mega = MissileWarhead.explosionPower(new ItemStack(Items.TNT), MissileSize.MEGA);
        assertTrue(small > 0f);
        assertTrue(mega > small);
        assertTrue(mega <= MissileWarhead.MAX_YIELD);
    }
}
