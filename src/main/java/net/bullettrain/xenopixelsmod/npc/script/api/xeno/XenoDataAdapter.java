package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpc;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptPlayer;
import xenoapi.npcs.api.entity.data.IData;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * XenoAPI's {@link IData} over a native data view. The view is fetched on every call, so the
 * adapter always reaches the current owner (a script-host rebuild replaces temporary data) and
 * keeps that owner's key, value and size bounds.
 */
public final class XenoDataAdapter implements IData {
    /** The six operations both native data views share. */
    interface View {
        void put(String key, Object value);
        Object get(String key);
        void remove(String key);
        boolean has(String key);
        String[] getKeys();
        void clear();
    }

    private final Supplier<View> view;
    private final Runnable guard;

    private XenoDataAdapter(Supplier<View> view, Runnable guard) {
        this.view = Objects.requireNonNull(view);
        this.guard = Objects.requireNonNull(guard);
    }

    private XenoDataAdapter(Supplier<View> view) {
        this(view, XenoApiAdapters::requireServerThreadNow);
    }

    /** {@code guard} runs before every operation (the server-thread check; replaceable in tests). */
    static XenoDataAdapter ofView(Supplier<View> view, Runnable guard) {
        return new XenoDataAdapter(view, guard);
    }

    /** An adapter over any native view; the view is fetched on every call. */
    static XenoDataAdapter ofView(Supplier<View> view) {
        return new XenoDataAdapter(view);
    }

    static XenoDataAdapter of(Supplier<ScriptNpc.Data> npcData) {
        return new XenoDataAdapter(() -> {
            ScriptNpc.Data data = npcData.get();
            return new View() {
                public void put(String key, Object value) { data.put(key, value); }
                public Object get(String key) { return data.get(key); }
                public void remove(String key) { data.remove(key); }
                public boolean has(String key) { return data.has(key); }
                public String[] getKeys() { return data.getKeys(); }
                public void clear() { data.clear(); }
            };
        });
    }

    static XenoDataAdapter ofPlayer(Supplier<ScriptPlayer.Data> playerData) {
        return new XenoDataAdapter(() -> {
            ScriptPlayer.Data data = playerData.get();
            return new View() {
                public void put(String key, Object value) { data.put(key, value); }
                public Object get(String key) { return data.get(key); }
                public void remove(String key) { data.remove(key); }
                public boolean has(String key) { return data.has(key); }
                public String[] getKeys() { return data.getKeys(); }
                public void clear() { data.clear(); }
            };
        });
    }

    private View current() {
        guard.run();
        return view.get();
    }

    @Override public void put(String key, Object value) { current().put(key, value); }
    @Override public Object get(String key) { return current().get(key); }
    @Override public void remove(String key) { current().remove(key); }
    @Override public boolean has(String key) { return current().has(key); }
    @Override public String[] getKeys() { return current().getKeys(); }
    @Override public void clear() { current().clear(); }
}
