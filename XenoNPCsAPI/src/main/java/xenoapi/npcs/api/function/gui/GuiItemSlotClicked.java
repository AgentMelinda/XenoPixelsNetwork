package xenoapi.npcs.api.function.gui;

import xenoapi.npcs.api.gui.ICustomGui;
import xenoapi.npcs.api.gui.IItemSlot;

@FunctionalInterface
public interface GuiItemSlotClicked {
    boolean onClick(ICustomGui gui, IItemSlot comp, int dragType, String clickType);
}
