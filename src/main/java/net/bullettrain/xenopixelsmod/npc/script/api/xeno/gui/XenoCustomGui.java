package net.bullettrain.xenopixelsmod.npc.script.api.xeno.gui;

import net.minecraft.resources.ResourceLocation;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.function.EventWrapper;
import xenoapi.npcs.api.function.gui.GuiAction;
import xenoapi.npcs.api.function.gui.GuiClosed;
import xenoapi.npcs.api.gui.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Minimal native {@link ICustomGui} core used by {@link XenoGuiComponents}.
 *
 * <p>Enough to compile and host button/label/text/rect components. Show/open/session
 * broadcasting still needs the unfinished {@link XenoGuiSessions} owner map; methods that
 * require a live player session refuse explicitly.
 */
public final class XenoCustomGui extends XenoGuiUnsupported {
    private static final int MAX_COORD = 8192;
    private static final int MAX_DIM = 4096;

    private final UUID uniqueId = UUID.randomUUID();
    private final IPlayer player;
    private final String name;
    private final boolean pauseGame;
    private final Map<Integer, XenoGuiComponents.Component> byId = new HashMap<>();
    private final List<XenoGuiComponents.Component> components = new ArrayList<>();
    private final EventWrapper<GuiClosed> onClosed = new EventWrapper<>();
    private final EventWrapper<GuiAction> customActions = new EventWrapper<>();
    private final EventWrapper<GuiAction> onResized = new EventWrapper<>();
    private int width;
    private int height;
    private boolean closeOnEsc = true;
    private String background = "";
    private boolean closed;
    private int scopeDepth;

    public XenoCustomGui(String name, int width, int height, boolean pauseGame, IPlayer player) {
        this.name = text(Objects.requireNonNull(name, "name"));
        dimension(width);
        dimension(height);
        this.width = width;
        this.height = height;
        this.pauseGame = pauseGame;
        this.player = Objects.requireNonNull(player, "player");
    }

    void check() {
        if (closed) throw new IllegalStateException("Custom GUI is closed");
    }

    void checkId(int id, XenoGuiComponents.Component owner) {
        XenoGuiComponents.Component existing = byId.get(id);
        if (existing != null && existing != owner) {
            throw new IllegalArgumentException("Duplicate custom GUI component id " + id);
        }
    }

    <T> T inScope(Supplier<T> work) {
        scopeDepth++;
        try {
            return work.get();
        } finally {
            scopeDepth--;
        }
    }

    public static void coordinate(int value) {
        if (value < -MAX_COORD || value > MAX_COORD) {
            throw new IllegalArgumentException("Coordinate out of range");
        }
    }

    public static void dimension(int value) {
        if (value < 0 || value > MAX_DIM) {
            throw new IllegalArgumentException("Dimension out of range");
        }
    }

    public static void scale(float value) {
        if (!Float.isFinite(value) || value <= 0f || value > 64f) {
            throw new IllegalArgumentException("Scale out of range");
        }
    }

    public static String text(String value) {
        if (value == null) throw new IllegalArgumentException("Text cannot be null");
        if (value.length() > XenoGuiWire.MAX_TEXT) {
            throw new IllegalArgumentException("Text exceeds " + XenoGuiWire.MAX_TEXT + " characters");
        }
        return value;
    }

    public static String texture(String value) {
        String cleaned = text(value == null ? "" : value);
        if (!cleaned.isEmpty() && ResourceLocation.tryParse(cleaned) == null) {
            throw new IllegalArgumentException("Invalid texture id");
        }
        return cleaned;
    }

    private void register(XenoGuiComponents.Component component) {
        check();
        if (components.size() >= XenoGuiWire.MAX_COMPONENTS) {
            throw new IllegalStateException("Custom GUI component limit reached");
        }
        checkId(component.id, component);
        byId.put(component.id, component);
        components.add(component);
    }

    @Override public int getID() { return uniqueId.hashCode(); }
    @Override public UUID getUniqueID() { return uniqueId; }
    @Override public String getName() { return name; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public void setSize(int width, int height) {
        check();
        dimension(width);
        dimension(height);
        this.width = width;
        this.height = height;
    }
    @Override public String getBackgroundTexture() { return background; }
    @Override public void setBackgroundTexture(String resourceLocation) {
        check();
        background = texture(resourceLocation == null ? "" : resourceLocation);
    }
    @Override public boolean getDoesPauseGame() { return pauseGame; }
    @Override public void setDoesPauseGame(boolean bo) { throw unsupported("setDoesPauseGame after construction"); }
    @Override public boolean getCloseOnEsc() { return closeOnEsc; }
    @Override public void setCloseOnEsc(boolean bo) { check(); closeOnEsc = bo; }
    @Override public void update() { throw unsupported("update (session broadcast not wired)"); }
    @Override public void update(ICustomGuiComponent component) { throw unsupported("update(component)"); }
    @Override public void open() { throw unsupported("open (session map not wired)"); }
    @Override public void close() { closed = true; }
    @Override public IPlayer getPlayer() { return player; }
    @Override public ICustomGui setOnClosed(String id, GuiClosed onClosed) {
        check();
        this.onClosed.add(text(id), onClosed);
        return this;
    }
    @Override public EventWrapper<GuiClosed> getOnClosedEvents() { return onClosed; }
    @Override public ICustomGui setCustomAction(String id, GuiAction action) {
        check();
        customActions.add(text(id), action);
        return this;
    }
    @Override public EventWrapper<GuiAction> getCustomActionEvents() { return customActions; }
    @Override public ICustomGui setOnResized(String id, GuiAction action) {
        check();
        onResized.add(text(id), action);
        return this;
    }
    @Override public EventWrapper<GuiAction> getOnResizedEvents() { return onResized; }

    @Override public IButton addButton(int id, String label, int x, int y) {
        return addButton(id, label, x, y, 100, 20);
    }
    @Override public IButton addButton(int id, String label, int x, int y, int width, int height) {
        var button = new XenoGuiComponents.Button(this, id, label, x, y, width, height);
        register(button);
        return button;
    }
    @Override public ILabel addLabel(int id, String label, int x, int y, int width, int height) {
        return addLabel(id, label, x, y, width, height, 0xffffff);
    }
    @Override public ILabel addLabel(int id, String label, int x, int y, int width, int height, int color) {
        var component = new XenoGuiComponents.Label(this, id, label, x, y, width, height, color);
        register(component);
        return component;
    }
    @Override public ITextField addTextField(int id, int x, int y, int width, int height) {
        var field = new XenoGuiComponents.TextField(this, id, x, y, width, height);
        register(field);
        return field;
    }
    @Override public ITexturedRect addTexturedRect(int id, String texture, int x, int y, int width, int height) {
        return addTexturedRect(id, texture, x, y, width, height, 0, 0);
    }
    @Override public ITexturedRect addTexturedRect(int id, String texture, int x, int y, int width, int height,
                                                   int textureX, int textureY) {
        var rect = new XenoGuiComponents.Rect(this, id, texture, x, y, width, height);
        rect.setTextureOffset(textureX, textureY);
        register(rect);
        return rect;
    }
    @Override public List<ICustomGuiComponent> getComponents() {
        return List.copyOf(components);
    }
    @Override public ICustomGuiComponent getComponent(int id) {
        return byId.get(id);
    }
    @Override public void addComponent(ICustomGuiComponent button) {
        if (!(button instanceof XenoGuiComponents.Component component) || component.gui != this) {
            throw new IllegalArgumentException("Foreign custom GUI component");
        }
        if (!components.contains(component)) register(component);
    }
    @Override public void removeComponent(int id) {
        check();
        XenoGuiComponents.Component removed = byId.remove(id);
        if (removed != null) components.remove(removed);
    }
    @Override public void clear() {
        check();
        byId.clear();
        components.clear();
    }
}
