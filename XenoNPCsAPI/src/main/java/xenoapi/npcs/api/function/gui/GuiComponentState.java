package xenoapi.npcs.api.function.gui;

import xenoapi.npcs.api.gui.ICustomGui;
import xenoapi.npcs.api.gui.ICustomGuiComponent;

@FunctionalInterface
public interface GuiComponentState {
    boolean onChange(ICustomGui gui, ICustomGuiComponent comp);
}
