package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpc;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpcTestAccess;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.INbt;
import xenoapi.npcs.api.IPos;
import xenoapi.npcs.api.entity.IEntity;
import xenoapi.npcs.api.item.IItemStack;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XenoApiAdaptersTest {
    /** A third-party implementation of a contract; every call on it fails the test. */
    @SuppressWarnings("unchecked")
    private static <T> T foreign(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) return "foreign";
                    throw new AssertionError("foreign adapter was called: " + method.getName());
                });
    }

    // ------------------------------------------------------------------ conversion

    @Test
    void nullEntityStaysNullAcrossFactory() {
        assertNull(XenoApiAdapters.wrap((net.minecraft.world.entity.Entity) null));
        assertNull(XenoApiAdapters.unwrap((IEntity<?>) null));
        assertNull(XenoApiAdapters.wrap((ItemStack) null));
        assertNull(XenoApiAdapters.wrap((CompoundTag) null));
    }

    @Test
    void foreignAdaptersAreRefusedBeforeReachingNativeState() {
        assertThrows(IllegalArgumentException.class, () -> XenoApiAdapters.unwrap(foreign(IEntity.class)));
        assertThrows(IllegalArgumentException.class, () -> XenoApiAdapters.unwrap(foreign(IItemStack.class)));
        CompoundTag tag = new CompoundTag();
        INbt nbt = XenoApiAdapters.wrap(tag);
        assertThrows(IllegalArgumentException.class, () -> nbt.setCompound("child", foreign(INbt.class)));
        assertThrows(IllegalArgumentException.class, () -> nbt.merge(foreign(INbt.class)));
        assertTrue(tag.isEmpty(), "a refused foreign adapter must not change the tag");
    }

    @Test
    void unsupportedOperationsNameTheMethodAndTheCapabilityTable() {
        IItemStack item = XenoApiAdapters.wrap(new ItemStack(Items.STONE));
        var error = assertThrows(UnsupportedOperationException.class, item::getNbt);
        assertTrue(error.getMessage().contains("IItemStack.getNbt"));
        assertTrue(error.getMessage().contains(XenoApiAdapters.CAPABILITY_DOC));
        assertThrows(UnsupportedOperationException.class, item::getMCItemStack);
    }

    // ------------------------------------------------------------------ positions

    @Test
    void positionOperationsReturnDistinctComputedPositions() {
        IPos origin = XenoApiAdapters.position(1.9, 2.1, -3.1);
        assertEquals(1, origin.getX());
        assertEquals(2, origin.getY());
        assertEquals(-4, origin.getZ());
        IPos moved = origin.north(2).up().add(3, 0, 0);
        assertEquals(4, moved.getX());
        assertEquals(3, moved.getY());
        assertEquals(-6, moved.getZ());
        assertEquals(-4, origin.getZ());
        assertEquals(origin, XenoApiAdapters.position(1.2, 2.9, -3.9));
        assertEquals(5.0, XenoApiAdapters.position(0, 0, 0).distanceTo(XenoApiAdapters.position(3, 4, 0)), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> origin.offset(6));
        assertThrows(IllegalArgumentException.class, () -> XenoApiAdapters.position(Double.NaN, 0, 0));
    }

    // ------------------------------------------------------------------ NBT

    @Test
    void nbtWritesAndNestedValuesReachOriginalTag() {
        CompoundTag tag = new CompoundTag();
        var adapter = XenoApiAdapters.wrap(tag);
        adapter.setInteger("count", 3);
        assertEquals(3, tag.getInt("count"));
        CompoundTag nested = new CompoundTag();
        nested.putString("name", "native");
        adapter.setCompound("child", XenoApiAdapters.wrap(nested));
        assertEquals("native", adapter.getCompound("child").getString("name"));
        adapter.getCompound("child").setInteger("depth", 2);
        assertEquals(2, tag.getCompound("child").getInt("depth"), "nested compounds are live");
        assertFalse(nested.contains("depth"), "setCompound stores a copy of its argument");
    }

    @Test
    void nbtListsRoundTripAndRefuseMixedTypesWithoutChangingTheTag() {
        CompoundTag tag = new CompoundTag();
        var adapter = XenoApiAdapters.wrap(tag);
        adapter.setList("names", new Object[] {"a", "b"});
        assertArrayEquals(new Object[] {"a", "b"}, adapter.getList("names", net.minecraft.nbt.Tag.TAG_STRING));
        assertEquals(net.minecraft.nbt.Tag.TAG_STRING, adapter.getListType("names"));
        assertThrows(IllegalArgumentException.class, () -> adapter.setList("mixed", new Object[] {"a", 1}));
        assertFalse(tag.contains("mixed"));
    }

    // ------------------------------------------------------------------ items

    @Test
    void itemCopyDoesNotMutateOriginalStack() {
        ItemStack stack = new ItemStack(Items.STONE, 5);
        var adapter = XenoApiAdapters.wrap(stack);
        adapter.copy().setStackSize(2);
        assertEquals(5, stack.getCount());
        adapter.setStackSize(3);
        assertEquals(3, stack.getCount(), "the adapter itself is live");
        assertEquals("minecraft:stone", adapter.getName());
    }

    @Test
    void itemComparisonHonoursIgnoreFlags() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack worn = sword.copy();
        worn.setDamageValue(10);
        var adapter = XenoApiAdapters.wrap(sword);
        assertFalse(adapter.compare(XenoApiAdapters.wrap(worn), false, false));
        assertTrue(adapter.compare(XenoApiAdapters.wrap(worn), false, true));
        assertFalse(adapter.compare(XenoApiAdapters.wrap(new ItemStack(Items.STONE)), true, true));
    }

    @Test
    void stackSizeFollowsTheReferenceRangeOfOneToMax() {
        var adapter = XenoApiAdapters.wrap(new ItemStack(Items.STONE, 5));
        adapter.setStackSize(0);
        assertEquals(1, adapter.getStackSize(), "the reference documents 1..64");
        adapter.setStackSize(999);
        assertEquals(64, adapter.getStackSize());
    }

    @Test
    void unknownItemIdsAreReportedAsAbsentNotAsErrors() {
        assertNull(XenoApiAdapters.knownItem("minecraft:not_an_item"));
        assertNull(XenoApiAdapters.knownItem(""));
        assertNull(XenoApiAdapters.knownItem(null));
        assertNull(XenoApiAdapters.knownItem("minecraft:air"));
        assertSame(Items.STONE, XenoApiAdapters.knownItem("minecraft:stone"));
    }

    // ------------------------------------------------------------------ shared data and timers

    @Test
    void dataWrittenThroughEitherInterfaceIsVisibleThroughTheOther() {
        Map<String, Object> temp = new HashMap<>();
        CompoundTag persistent = new CompoundTag();
        ScriptNpc npc = ScriptNpcTestAccess.npc(persistent, temp, new ScriptTimers());
        var apiTemp = XenoDataAdapter.of(npc::getTempdata);
        var apiStored = XenoDataAdapter.of(npc::getStoreddata);

        apiTemp.put("phase", "charge");
        assertEquals("charge", npc.getTempdata().get("phase"));
        npc.getTempdata().put("hits", 3);
        assertEquals(3, apiTemp.get("hits"));

        apiStored.put("rank", 7);
        assertEquals(7.0, npc.getStoreddata().get("rank"));
        npc.getStoreddata().put("title", "Elite");
        assertEquals("Elite", apiStored.get("title"));
        apiStored.put("x".repeat(65), 1);
        assertFalse(npc.getStoreddata().has("x".repeat(65)), "native key bounds still apply");
    }

    @Test
    void dataAdapterFollowsTheCurrentOwnerAfterARebuild() {
        Map<String, Object> first = new HashMap<>();
        Map<String, Object> second = new HashMap<>();
        CompoundTag persistent = new CompoundTag();
        Map<String, Object>[] current = new Map[] {first};
        var apiTemp = XenoDataAdapter.of(() -> ScriptNpcTestAccess.npc(persistent, current[0], new ScriptTimers()).getTempdata());
        apiTemp.put("k", "v");
        current[0] = second;
        assertFalse(apiTemp.has("k"), "a rebuilt host starts fresh temporary data");
        apiTemp.put("k", "w");
        assertEquals("w", second.get("k"));
        assertEquals("v", first.get("k"));
    }

    @Test
    void timersStartedThroughTheApiFireTheNativeOwner() {
        ScriptTimers timers = new ScriptTimers();
        ScriptNpc npc = ScriptNpcTestAccess.npc(new CompoundTag(), new HashMap<>(), timers);
        var api = new XenoTimersAdapter(npc::getTimers);

        api.start(4, 20, false);
        assertTrue(npc.getTimers().has(4));
        assertThrows(CustomNPCsException.class, () -> api.start(4, 20, false), "start refuses an existing id");
        api.forceStart(4, 10, true);
        assertThrows(CustomNPCsException.class, () -> api.start(5, 0, false));
        assertFalse(api.has(5), "a refused start leaves no timer");
        assertThrows(CustomNPCsException.class, () -> api.reset(99));

        var fired = new java.util.ArrayList<Integer>();
        timers.tick(10, fired::add);
        assertEquals(java.util.List.of(4), fired);
        assertTrue(api.stop(4));
        assertFalse(npc.getTimers().has(4));
    }
}
