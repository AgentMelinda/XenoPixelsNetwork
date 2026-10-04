package xenoapi.npcs.api.function.gui;

import xenoapi.npcs.api.gui.ICustomGui;
import xenoapi.npcs.api.gui.ICustomGuiComponent;

@FunctionalInterface
public interface GuiComponentAction<T extends ICustomGuiComponent> {
    void onAction(ICustomGui gui, T comp);
}
