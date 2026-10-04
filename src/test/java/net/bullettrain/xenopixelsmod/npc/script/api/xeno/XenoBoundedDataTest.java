package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class XenoBoundedDataTest {
    @Test
    void storedDataKeepsNativeBoundsAndTypes() {
        CompoundTag[] owner = {new CompoundTag()};
        var data = XenoDataAdapter.ofView(() -> XenoBoundedData.stored(() -> owner[0], t -> owner[0] = t));
        data.put("n", 3);
        data.put("s", "text");
        data.put("bad", new Object());
        data.put("x".repeat(65), 1);
        data.put("long", "y".repeat(1025));
        data.put("nan", Double.NaN);
        assertEquals(3.0, data.get("n"));
        assertEquals("text", data.get("s"));
        assertFalse(data.has("bad"));
        assertFalse(data.has("x".repeat(65)));
        assertFalse(data.has("long"));
        assertFalse(data.has("nan"));
        for (int i = 0; i < 100; i++) data.put("k" + i, i);
        assertEquals(64, data.getKeys().length, "at most 64 stored keys");
        data.clear();
        assertEquals(0, data.getKeys().length);
    }

    @Test
    void tempDataHoldsAnyValueUpTo64Keys() {
        var map = new HashMap<String, Object>();
        var data = XenoDataAdapter.ofView(() -> XenoBoundedData.temp(map));
        Object value = new Object();
        data.put("o", value);
        assertSame(value, data.get("o"));
        for (int i = 0; i < 100; i++) data.put("k" + i, i);
        assertEquals(64, map.size());
        data.remove("o");
        assertFalse(data.has("o"));
    }

    @Test
    void itemStoredDataLivesInTheCustomDataComponent() {
        ItemStack stack = new ItemStack(Items.STONE);
        var item = XenoApiAdapters.wrap(stack);
        item.getStoreddata().put("owner", "goku");
        assertEquals("goku", XenoApiAdapters.wrap(stack).getStoreddata().get("owner"));
        assertTrue(item.hasNbt(), "stored item data is custom data");
        item.getTempdata().put("t", 1);
        assertEquals(1, item.getTempdata().get("t"));
        assertNull(XenoApiAdapters.wrap(stack.copy()).getTempdata().get("t"), "temp data is per stack instance");
    }
}
