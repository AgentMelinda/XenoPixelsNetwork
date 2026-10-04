package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XenoItemAttributesTest {
    private static final String ATTACK = "minecraft:generic.attack_damage";

    @Test
    void attackDamageIsTheMainHandAddValueSum() {
        // SwordItem.createAttributes(IRON, 3, -2.4f): 3 + iron bonus 2 = 5.
        assertEquals(5.0, XenoApiAdapters.wrap(new ItemStack(Items.IRON_SWORD)).getAttackDamage(), 1e-9);
        assertEquals(0.0, XenoApiAdapters.wrap(new ItemStack(Items.STONE)).getAttackDamage(), 1e-9);
    }

    @Test
    void setAttributeAddsAReplaceableModifierPerSlot() {
        var sword = XenoApiAdapters.wrap(new ItemStack(Items.IRON_SWORD));
        sword.setAttribute(ATTACK, 2.0, 0);
        assertEquals(7.0, sword.getAttackDamage(), 1e-9);
        sword.setAttribute(ATTACK, 4.0, 0);
        assertEquals(9.0, sword.getAttackDamage(), 1e-9, "setting again replaces, not stacks");
        assertTrue(sword.hasAttribute(ATTACK));
        sword.setAttribute(ATTACK, 0.0, 0);
        assertEquals(5.0, sword.getAttackDamage(), 1e-9, "zero removes the scripted modifier");
    }

    @Test
    void attributeInputsAreValidated() {
        var stone = XenoApiAdapters.wrap(new ItemStack(Items.STONE));
        assertThrows(IllegalArgumentException.class, () -> stone.setAttribute("minecraft:not_real", 1.0, 0));
        assertThrows(IllegalArgumentException.class, () -> stone.setAttribute(ATTACK, 1.0, 6));
        assertThrows(IllegalArgumentException.class, () -> stone.setAttribute(ATTACK, Double.NaN));
        assertFalse(stone.hasAttribute(ATTACK));
        assertEquals(0.0, stone.getAttribute(ATTACK), 1e-9);
    }
}
