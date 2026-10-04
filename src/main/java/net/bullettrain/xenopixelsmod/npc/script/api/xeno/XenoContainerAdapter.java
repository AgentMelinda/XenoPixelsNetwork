package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import xenoapi.npcs.api.IContainer;
import xenoapi.npcs.api.item.IItemStack;

import java.util.function.BiConsumer;
import java.util.function.IntFunction;

/** A container or menu as XenoAPI's IContainer; slot reads are live stacks, writes are bounded. */
public final class XenoContainerAdapter implements IContainer {
    private final int size;
    private final IntFunction<ItemStack> get;
    private final BiConsumer<Integer, ItemStack> set;
    private final Runnable guard;

    private XenoContainerAdapter(int size, IntFunction<ItemStack> get, BiConsumer<Integer, ItemStack> set, Runnable guard) {
        this.size = size;
        this.get = get;
        this.set = set;
        this.guard = guard;
    }

    public static IContainer of(Container container) {
        return of(container, XenoApiAdapters::requireServerThreadNow);
    }

    /** {@code guard} runs before every write (the server-thread check; replaceable in tests). */
    static IContainer of(Container container, Runnable guard) {
        return container == null ? null : new XenoContainerAdapter(container.getContainerSize(),
                container::getItem, (slot, stack) -> {
                    container.setItem(slot, stack);
                    container.setChanged();
                }, guard);
    }

    public static IContainer of(AbstractContainerMenu menu) {
        return menu == null ? null : new XenoContainerAdapter(menu.slots.size(),
                slot -> menu.getSlot(slot).getItem(), (slot, stack) -> {
                    menu.getSlot(slot).set(stack);
                    menu.broadcastChanges();
                }, XenoApiAdapters::requireServerThreadNow);
    }

    private int slot(int slot) {
        if (slot < 0 || slot >= size) throw new IllegalArgumentException("Slot must be 0-" + (size - 1) + ", got " + slot);
        return slot;
    }

    @Override public int getSize() { return size; }
    @Override public IItemStack getSlot(int slot) { return XenoApiAdapters.wrap(get.apply(slot(slot))); }

    @Override
    public void setSlot(int slot, IItemStack item) {
        int index = slot(slot);
        ItemStack stack = XenoApiAdapters.unwrap(item).copy();
        guard.run();
        set.accept(index, stack);
    }

    @Override
    public int count(IItemStack item, boolean ignoreDamage, boolean ignoreNBT) {
        if (item == null) throw new IllegalArgumentException("IContainer.count: item cannot be null");
        int total = 0;
        for (int i = 0; i < size; i++) {
            ItemStack stack = get.apply(i);
            if (!stack.isEmpty() && item.compare(stack, ignoreNBT, ignoreDamage)) total += stack.getCount();
        }
        return total;
    }

    @Override
    public IItemStack[] getItems() {
        IItemStack[] items = new IItemStack[size];
        for (int i = 0; i < size; i++) items[i] = XenoApiAdapters.wrap(get.apply(i));
        return items;
    }

    @Override
    public Container getMCInventory() {
        throw XenoApiAdapters.unsupported("IContainer.getMCInventory (raw handles are not exposed)");
    }

    @Override
    public AbstractContainerMenu getMCContainer() {
        throw XenoApiAdapters.unsupported("IContainer.getMCContainer (raw handles are not exposed)");
    }
}
