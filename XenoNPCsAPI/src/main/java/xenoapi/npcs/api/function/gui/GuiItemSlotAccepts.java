package xenoapi.npcs.api.function.gui;

import xenoapi.npcs.api.gui.ICustomGui;
import xenoapi.npcs.api.gui.IItemSlot;
import xenoapi.npcs.api.item.IItemStack;

@FunctionalInterface
public interface GuiItemSlotAccepts {
    boolean onAccepts(ICustomGui gui, IItemSlot slot, IItemStack itemstack);
}
