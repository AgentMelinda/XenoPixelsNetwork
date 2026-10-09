package net.bullettrain.xenopixelsmod.npc.script.api.xeno.gui;

import xenoapi.npcs.api.gui.*;
import xenoapi.npcs.api.entity.*;
import xenoapi.npcs.api.item.*;
import xenoapi.npcs.api.function.*;
import xenoapi.npcs.api.function.gui.*;
import java.util.*;

/** Explicit refusals for widgets and behaviors that require a later native implementation. */
abstract class XenoGuiUnsupported implements ICustomGui {
    protected static UnsupportedOperationException unsupported(String name) {
        return new UnsupportedOperationException(name + " is not implemented by the native custom GUI core");
    }
    @Override public int getID() { throw unsupported("getID"); }
    @Override public UUID getUniqueID() { throw unsupported("getUniqueID"); }
    @Override public String getName() { throw unsupported("getName"); }
    @Override public int getWidth() { throw unsupported("getWidth"); }
    @Override public int getHeight() { throw unsupported("getHeight"); }
    @Override public void setSize(int width, int height) { throw unsupported("setSize"); }
    @Override public String getBackgroundTexture() { throw unsupported("getBackgroundTexture"); }
    @Override public void setBackgroundTexture(String resourceLocation) { throw unsupported("setBackgroundTexture"); }
    @Override public boolean getDoesPauseGame() { throw unsupported("getDoesPauseGame"); }
    @Override public void setDoesPauseGame(boolean bo) { throw unsupported("setDoesPauseGame"); }
    @Override public boolean getCloseOnEsc() { throw unsupported("getCloseOnEsc"); }
    @Override public void setCloseOnEsc(boolean bo) { throw unsupported("setCloseOnEsc"); }
    @Override public void update() { throw unsupported("update"); }
    @Override public IItemStack getCarriedItem() { throw unsupported("getCarriedItem"); }
    @Override public void setCarriedItem(IItemStack stack) { throw unsupported("setCarriedItem"); }
    @Override public void update(ICustomGuiComponent component) { throw unsupported("update"); }
    @Override public IComponentsScrollableWrapper getScrollingPanel() { throw unsupported("getScrollingPanel"); }
    @Override public void openSubGui(ICustomGui gui) { throw unsupported("openSubGui"); }
    @Override public void open() { throw unsupported("open"); }
    @Override public ICustomGui getSubGui() { throw unsupported("getSubGui"); }
    @Override public boolean hasSubGui() { throw unsupported("hasSubGui"); }
    @Override public boolean isSubGui() { throw unsupported("isSubGui"); }
    @Override public ICustomGui closeSubGui() { throw unsupported("closeSubGui"); }
    @Override public void close() { throw unsupported("close"); }
    @Override public ICustomGui getParentGui() { throw unsupported("getParentGui"); }
    @Override public ICustomGui getRootGui() { throw unsupported("getRootGui"); }
    @Override public ICustomGui getActiveGui() { throw unsupported("getActiveGui"); }
    @Override public IPlayer getPlayer() { throw unsupported("getPlayer"); }
    @Override public ICustomGui showMessage(String message) { throw unsupported("showMessage"); }
    @Override public ICustomGui showYesNo(String message, GuiCallback<Boolean> callback) { throw unsupported("showYesNo"); }
    @Override public ICustomGui showList(String title, String[] list, String selected, GuiCallback<String> callback) { throw unsupported("showList"); }
    @Override public ICustomGui setOnClosed(String id, GuiClosed onClosed) { throw unsupported("setOnClosed"); }
    @Override public EventWrapper<GuiClosed> getOnClosedEvents() { throw unsupported("getOnClosedEvents"); }
    @Override public ICustomGui setCustomAction(String id, GuiAction action) { throw unsupported("setCustomAction"); }
    @Override public EventWrapper<GuiAction> getCustomActionEvents() { throw unsupported("getCustomActionEvents"); }
    @Override public ICustomGui setOnResized(String id, GuiAction action) { throw unsupported("setOnResized"); }
    @Override public EventWrapper<GuiAction> getOnResizedEvents() { throw unsupported("getOnResizedEvents"); }
    @Override public IButton addButton(int id, String label, int x, int y) { throw unsupported("addButton"); }
    @Override public IButton addButton(int id, String label, int x, int y, int width, int height) { throw unsupported("addButton"); }
    @Override public IButtonList addButtonList(int id, int x, int y, int width, int height) { throw unsupported("addButtonList"); }
    @Override public IButton addTexturedButton(int id, String label, int x, int y, int width, int height, String texture) { throw unsupported("addTexturedButton"); }
    @Override public IButton addTexturedButton(int id, String label, int x, int y, int width, int height, String texture, int textureX, int textureY) { throw unsupported("addTexturedButton"); }
    @Override public ILabel addLabel(int id, String label, int x, int y, int width, int height) { throw unsupported("addLabel"); }
    @Override public ILabel addLabel(int id, String label, int x, int y, int width, int height, int color) { throw unsupported("addLabel"); }
    @Override public ITextField addTextField(int id, int x, int y, int width, int height) { throw unsupported("addTextField"); }
    @Override public ITextArea addTextArea(int id, int x, int y, int width, int height) { throw unsupported("addTextArea"); }
    @Override public IScroll addScroll(int id, int x, int y, int width, int height, String[] list) { throw unsupported("addScroll"); }
    @Override public ISlider addSlider(int id, int x, int y, int width, int height, String format) { throw unsupported("addSlider"); }
    @Override public IEntityDisplay addEntityDisplay(int id, int x, int y, int width, int height, IEntity entity) { throw unsupported("addEntityDisplay"); }
    @Override public IAssetsSelector addAssetsSelector(int id, int x, int y, int width, int height) { throw unsupported("addAssetsSelector"); }
    @Override public ITexturedRect addTexturedRect(int id, String texture, int x, int y, int width, int height) { throw unsupported("addTexturedRect"); }
    @Override public ITexturedRect addTexturedRect(int id, String texture, int x, int y, int width, int height, int textureX, int textureY) { throw unsupported("addTexturedRect"); }
    @Override public List<ICustomGuiComponent> getComponents() { throw unsupported("getComponents"); }
    @Override public ICustomGuiComponent getComponent(int id) { throw unsupported("getComponent"); }
    @Override public void addComponent(ICustomGuiComponent button) { throw unsupported("addComponent"); }
    @Override public void removeComponent(int id) { throw unsupported("removeComponent"); }
    @Override public List<IItemSlot> getSlots() { throw unsupported("getSlots"); }
    @Override public List<IItemSlot> getPlayerSlots() { throw unsupported("getPlayerSlots"); }
    @Override public IItemSlot addItemSlot(int x, int y) { throw unsupported("addItemSlot"); }
    @Override public IItemSlot addItemSlot(int x, int y, IItemStack stack) { throw unsupported("addItemSlot"); }
    @Override public IItemSlot addItemSlot(int id, int x, int y, IItemStack stack) { throw unsupported("addItemSlot"); }
    @Override public IItemSlot getItemSlot(int id) { throw unsupported("getItemSlot"); }
    @Override public void removeItemSlot(IItemSlot slot) { throw unsupported("removeItemSlot"); }
    @Override public void showPlayerInventory(int x, int y) { throw unsupported("showPlayerInventory"); }
    @Override public IItemSlot[] showPlayerInventory(int x, int y, boolean full) { throw unsupported("showPlayerInventory"); }
    @Override public void clear() { throw unsupported("clear"); }
}

