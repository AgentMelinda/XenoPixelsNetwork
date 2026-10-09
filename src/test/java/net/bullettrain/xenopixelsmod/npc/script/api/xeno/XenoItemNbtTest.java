package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.INbt;

import static org.junit.jupiter.api.Assertions.*;

class XenoItemNbtTest {
    @Test void independentViewsReadCurrentComponentAndPreserveUnrelatedComponents() {
        var stack = new ItemStack(Items.IRON_SWORD);
        var item = new XenoItemAdapter(stack);
        item.setCustomName("Training sword");
        item.setDamage(3);
        INbt first = item.getNbt(), second = item.getNbt();
        assertTrue(first.isEmpty());
        assertFalse(item.hasNbt(), "reading is not a mutation");
        first.setInteger("power", 7);
        second.putString("owner", "Goku");
        assertEquals(7, second.getInteger("power"));
        assertEquals("Goku", first.getString("owner"));
        assertEquals(7, stack.get(DataComponents.CUSTOM_DATA).copyTag().getInt("power"));
        assertEquals("Training sword", item.getDisplayName());
        assertEquals(3, item.getDamage());
        assertFalse(first.has("components"), "custom data is not the complete item save");
        first.putString("CustomName", "Legacy raw field");
        assertEquals("Training sword", item.getDisplayName(), "built-in components are distinct");
    }

    @Test void nestedCompoundWritesFollowLatestDataWithoutReplacingSiblingChanges() {
        var item = new XenoItemAdapter(new ItemStack(Items.STONE));
        var source = new XenoNbtAdapter(new CompoundTag());
        source.setInteger("level", 1);
        item.getNbt().setCompound("training", source);
        INbt training = item.getNbt().getCompound("training");
        item.getNbt().getCompound("training").putString("style", "BT3");
        item.getNbt().putString("sibling", "preserve");
        training.setInteger("level", 2);
        assertEquals("BT3", training.getString("style"));
        assertEquals("preserve", item.getNbt().getString("sibling"));
        assertEquals(2, item.getNbt().getCompound("training").getInteger("level"));
        assertEquals(1, source.getInteger("level"), "setCompound copies the source");
        assertTrue(source.isEqual(source));
        var detached = item.getNbt().getCompound("missing");
        detached.setInteger("value", 3);
        assertFalse(item.getNbt().has("missing"), "missing compounds remain detached, as before");
    }

    @Test void compoundListsRemainWritableAndCopyIncomingTags() {
        var item = new XenoItemAdapter(new ItemStack(Items.STONE));
        var entry = new XenoNbtAdapter(new CompoundTag());
        entry.putString("move", "Dash");
        item.getNbt().setList("moves", new Object[]{entry});
        var liveEntry = (INbt) item.getNbt().getList("moves", Tag.TAG_COMPOUND)[0];
        liveEntry.setInteger("damage", 12);
        assertEquals(12, ((INbt) item.getNbt().getList("moves", Tag.TAG_COMPOUND)[0]).getInteger("damage"));
        assertFalse(entry.has("damage"));
        var copy = new XenoNbtAdapter(new CompoundTag());
        copy.setCompound("copy", liveEntry);
        assertEquals(12, copy.getCompound("copy").getInteger("damage"), "views compose with detached tags");
        item.getNbt().merge(copy);
        assertTrue(item.getNbt().getCompound("copy").isEqual(liveEntry));
    }

    @Test void invalidWritesDoNotPartiallyCommitAndArraysAreCopied() {
        var stack = new ItemStack(Items.STONE);
        var view = new XenoItemAdapter(stack).getNbt();
        view.setList("values", new Object[]{1, 2});
        var before = stack.get(DataComponents.CUSTOM_DATA);
        assertThrows(IllegalArgumentException.class, () -> view.setList("values", new Object[]{3, "mixed"}));
        assertThrows(IllegalArgumentException.class, () -> view.putString("bad", "x".repeat(XenoNbtAdapter.MAX_STRING + 1)));
        assertThrows(IllegalArgumentException.class, () -> view.setByteArray("bad", new byte[XenoNbtAdapter.MAX_ARRAY + 1]));
        assertThrows(IllegalArgumentException.class, () -> view.setCompound("bad", null));
        assertThrows(IllegalArgumentException.class, () -> view.setInteger(null, 3));
        assertSame(before, stack.get(DataComponents.CUSTOM_DATA));
        byte[] bytes = {1, 2};
        view.setByteArray("bytes", bytes);
        bytes[0] = 9;
        view.getByteArray("bytes")[1] = 8;
        assertArrayEquals(new byte[]{1, 2}, view.getByteArray("bytes"));
        view.getMCNBT().putInt("detached", 1);
        assertFalse(view.has("detached"), "plain-data handles never expose the component's unsafe tag");
    }

    @Test void itemCopiesAndRemovedDataDoNotShareMutableComponentState() {
        var stack = new ItemStack(Items.STONE);
        var item = new XenoItemAdapter(stack);
        var view = item.getNbt();
        view.setInteger("power", 7);
        var copy = item.copy();
        view.setInteger("power", 8);
        assertEquals(7, copy.getNbt().getInteger("power"));
        item.removeNbt();
        assertFalse(item.hasNbt());
        assertTrue(view.isEmpty(), "a cached view follows removal");
        view.setInteger("fresh", 1);
        assertFalse(view.has("power"));
        assertEquals(7, copy.getNbt().getInteger("power"));
        view.clear();
        assertFalse(stack.has(DataComponents.CUSTOM_DATA));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        assertFalse(item.hasNbt(), "an explicitly empty component is still empty NBT");
    }

    @Test void ordinaryCompoundAdaptersKeepTheirOriginalLiveNestedBehavior() {
        var tag = new CompoundTag();
        var child = new CompoundTag();
        tag.put("child", child);
        var wrapper = new XenoNbtAdapter(tag);
        wrapper.getCompound("child").setInteger("value", 42);
        assertEquals(42, child.getInt("value"));
        assertSame(tag, wrapper.getMCNBT());
        assertEquals(wrapper, new XenoNbtAdapter(tag));
    }
}
