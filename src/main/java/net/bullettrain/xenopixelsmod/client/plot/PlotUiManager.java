package net.bullettrain.xenopixelsmod.client.plot;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Modular registry and dispatcher for plot UI.
 *
 * <p>Each concern of the plot UI — boundary rendering, the selection readout, an ownership
 * tooltip — is a separate {@link PlotUiModule}. The manager only holds the ordered set, tracks
 * which modules are enabled, and fans out tick/render calls. New behaviour is added by
 * registering a module, not by changing this class.</p>
 *
 * <p>Client-only. The manager holds no world state: it never claims, releases, or mutates a plot.
 * All of that stays server-authoritative in {@code PlotManager}.</p>
 */
public final class PlotUiManager {

    private static final PlotUiManager INSTANCE = new PlotUiManager();

    private final Map<String, PlotUiModule> modules = new LinkedHashMap<>();
    private final Map<String, Boolean> enabled = new LinkedHashMap<>();

    private PlotUiManager() {
    }

    public static PlotUiManager get() {
        return INSTANCE;
    }

    /**
     * Registers a module. Re-registering the same id replaces the previous module and keeps its
     * enablement, so a hot reload does not silently disable an overlay.
     */
    public void register(PlotUiModule module) {
        Objects.requireNonNull(module, "module");
        String id = Objects.requireNonNull(module.id(), "module id");
        modules.put(id, module);
        enabled.putIfAbsent(id, true);
    }

    /** Removes a module. Returns true when one was present. */
    public boolean unregister(String id) {
        enabled.remove(id);
        return modules.remove(id) != null;
    }

    /** Turns a registered module on or off. Returns false when the id is unknown. */
    public boolean setEnabled(String id, boolean value) {
        if (!modules.containsKey(id)) {
            return false;
        }
        enabled.put(id, value);
        return true;
    }

    public boolean isEnabled(String id) {
        return enabled.getOrDefault(id, false);
    }

    /** Registered module ids in registration order. */
    public List<String> ids() {
        return List.copyOf(modules.keySet());
    }

    /** Ticks every enabled module. */
    public void tick() {
        for (PlotUiModule module : active()) {
            module.tick();
        }
    }

    /** Renders every enabled module. */
    public void render(GuiGraphics graphics, float partialTick) {
        if (graphics == null) {
            return;
        }
        for (PlotUiModule module : active()) {
            module.render(graphics, partialTick);
        }
    }

    private List<PlotUiModule> active() {
        List<PlotUiModule> result = new ArrayList<>();
        for (PlotUiModule module : modules.values()) {
            if (isEnabled(module.id()) && module.isEnabled()) {
                result.add(module);
            }
        }
        result.sort(Comparator.comparing(PlotUiModule::id));
        return result;
    }
}