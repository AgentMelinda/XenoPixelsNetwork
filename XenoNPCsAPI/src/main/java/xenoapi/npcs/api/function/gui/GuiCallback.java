package xenoapi.npcs.api.function.gui;

import xenoapi.npcs.api.gui.ICustomGui;

@FunctionalInterface
public interface GuiCallback<T> {
    void onAction(ICustomGui gui, T v);
}
