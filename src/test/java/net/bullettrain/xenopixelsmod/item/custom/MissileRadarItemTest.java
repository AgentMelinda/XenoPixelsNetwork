package net.bullettrain.xenopixelsmod.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MissileRadarItemTest {

    @Test
    void storedCoordsRoundTripAndClear() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertNull(MissileRadarItem.getStoredTarget(stack));
        MissileRadarItem.setStoredTarget(stack, new BlockPos(12, 80, -4));
        assertEquals(new BlockPos(12, 80, -4), MissileRadarItem.getStoredTarget(stack));
        MissileRadarItem.clearStoredTarget(stack);
        assertNull(MissileRadarItem.getStoredTarget(stack));
    }
}
