package xenoapi.npcs.api.function.gui;

import xenoapi.npcs.api.gui.ICustomGui;
import xenoapi.npcs.api.gui.ICustomGuiComponent;
import xenoapi.npcs.api.gui.ScrollItem;

@FunctionalInterface
public interface GuiScrollAction<T extends ICustomGuiComponent> {
    void onAction(ICustomGui gui, T comp, ScrollItem item);
}
