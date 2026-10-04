package xenoapi.npcs.api.function.gui;

import xenoapi.npcs.api.gui.ICustomGui;

@FunctionalInterface
public interface GuiAction {
    void onAction(ICustomGui gui);
}
