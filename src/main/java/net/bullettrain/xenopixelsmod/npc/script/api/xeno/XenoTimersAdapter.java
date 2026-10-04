package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpc;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.ITimers;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * XenoAPI's {@link ITimers} over native timers: an NPC's (the ones its script host saves and
 * ticks, so a timer started here fires the NPC script's {@code timer} hook) or a player's. Where
 * the native call refuses, this throws as CustomNPCs does rather than returning silently.
 */
public final class XenoTimersAdapter implements ITimers {
    /** The six native timer operations, over NPC TimerView or a raw ScriptTimers. */
    interface Backing {
        boolean start(int id, int ticks, boolean repeat);
        boolean forceStart(int id, int ticks, boolean repeat);
        boolean has(int id);
        boolean stop(int id);
        boolean reset(int id);
        void clear();
    }

    private final Supplier<Backing> timers;

    public XenoTimersAdapter(Supplier<ScriptNpc.TimerView> npcTimers) {
        Objects.requireNonNull(npcTimers);
        this.timers = () -> {
            XenoApiAdapters.requireServerThreadNow();
            ScriptNpc.TimerView view = npcTimers.get();
            return new Backing() {
                public boolean start(int id, int ticks, boolean repeat) { return view.start(id, ticks, repeat); }
                public boolean forceStart(int id, int ticks, boolean repeat) { return view.forceStart(id, ticks, repeat); }
                public boolean has(int id) { return view.has(id); }
                public boolean stop(int id) { return view.stop(id); }
                public boolean reset(int id) { return view.reset(id); }
                public void clear() { view.clear(); }
            };
        };
    }

    private XenoTimersAdapter(Backing backing, Runnable guard) {
        this.timers = () -> {
            guard.run();
            return backing;
        };
    }

    /** A XenoAPI view over native timers read with {@code now} (player timers). */
    public static XenoTimersAdapter forTimers(ScriptTimers timers, LongSupplier now) {
        return forTimers(timers, now, XenoApiAdapters::requireServerThreadNow);
    }

    /** {@code guard} runs before every operation (the server-thread check; replaceable in tests). */
    static XenoTimersAdapter forTimers(ScriptTimers timers, LongSupplier now, Runnable guard) {
        Objects.requireNonNull(timers);
        Objects.requireNonNull(now);
        return new XenoTimersAdapter(new Backing() {
            public boolean start(int id, int ticks, boolean repeat) { return timers.start(id, ticks, repeat, now.getAsLong()); }
            public boolean forceStart(int id, int ticks, boolean repeat) { return timers.forceStart(id, ticks, repeat, now.getAsLong()); }
            public boolean has(int id) { return timers.has(id); }
            public boolean stop(int id) { return timers.stop(id); }
            public boolean reset(int id) { return timers.reset(id, now.getAsLong()); }
            public void clear() { timers.clear(); }
        }, Objects.requireNonNull(guard));
    }

    @Override
    public void start(int id, int ticks, boolean repeat) {
        Backing view = timers.get();
        if (view.has(id)) throw new CustomNPCsException("There is already a timer with id: %d", id);
        if (!view.start(id, ticks, repeat)) throw refused(id, ticks);
    }

    @Override
    public void forceStart(int id, int ticks, boolean repeat) {
        if (!timers.get().forceStart(id, ticks, repeat)) throw refused(id, ticks);
    }

    private static CustomNPCsException refused(int id, int ticks) {
        return new CustomNPCsException("Timer %d refused: ticks must be 1-1200000 (got %d) and at most 64 timers may run", id, ticks);
    }

    @Override public boolean has(int id) { return timers.get().has(id); }
    @Override public boolean stop(int id) { return timers.get().stop(id); }

    @Override
    public void reset(int id) {
        if (!timers.get().reset(id)) throw new CustomNPCsException("There is no timer with id: %d", id);
    }

    @Override public void clear() { timers.get().clear(); }
}
