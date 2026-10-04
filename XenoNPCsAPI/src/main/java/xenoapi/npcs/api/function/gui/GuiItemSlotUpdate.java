package xenoapi.npcs.api.function.gui;

import xenoapi.npcs.api.gui.ICustomGui;
import xenoapi.npcs.api.gui.IItemSlot;
import xenoapi.npcs.api.gui.ITextField;

@FunctionalInterface
public interface GuiItemSlotUpdate {
    void onUpdate(ICustomGui gui, IItemSlot slot);
}
