package net.bullettrain.xenopixelsmod.npc.script.api.xeno.event;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.npc.script.api.xeno.NativeNpcApi;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import xenoapi.npcs.api.NpcAPI;
import xenoapi.npcs.api.event.NpcEvent;
import xenoapi.npcs.api.event.PlayerEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared delivery rules for typed XenoAPI events: build gate, Java post, state access, re-entry. */
public final class XenoEventDispatch {
    private static final long LOG_INTERVAL_MS = 60_000;
    private static final Map<String, Long> LAST_LOG = new ConcurrentHashMap<>();
    private static final ThreadLocal<Set<String>> ACTIVE = ThreadLocal.withInitial(HashSet::new);

    private XenoEventDispatch() {}

    public static boolean javaListening() {
        return NpcAPI.Instance() instanceof NativeNpcApi api && api.eventBus().hasListeners();
    }

    /** Build a typed event only when a script defines the hook or a Java listener exists. */
    public static boolean wanted(boolean scriptWants) {
        return scriptWants || javaListening();
    }

    /** Posts to NpcAPI.events() after scripts ran; listener exceptions are logged, never thrown. */
    public static <E extends Event> E post(E event) {
        NpcAPI api = NpcAPI.Instance();
        return api == null ? event : post(api.events(), event);
    }

    /**
     * Any listener failure, including a linkage error from an addon built against an older API,
     * stays out of the game tick. Only VM errors propagate. The bus stops at the failing listener.
     */
    static <E extends Event> E post(IEventBus bus, E event) {
        try {
            bus.post(event);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable ignored) {
            // Logged, rate-limited per listener, by onListenerError (the bus's exception handler).
        }
        return event;
    }

    /** The bus's exception handler: one warning per listener per minute, instead of every throw. */
    public static void onListenerError(IEventBus bus, Event event, net.neoforged.bus.api.EventListener[] listeners,
                                       int index, Throwable error) {
        String listener = index >= 0 && index < listeners.length ? String.valueOf(listeners[index]) : "unknown";
        long now = System.currentTimeMillis();
        Long last = LAST_LOG.get(listener);
        if (last == null || now - last >= LOG_INTERVAL_MS) {
            LAST_LOG.put(listener, now);
            XenoPixelsMod.LOGGER.warn("XenoAPI listener {} threw on {}: {}", listener,
                    event.getClass().getSimpleName(), error.toString());
        }
    }

    public static boolean isCanceled(Event event) {
        return event instanceof ICancellableEvent cancellable && cancellable.isCanceled();
    }

    public static void setCanceled(Event event, boolean canceled) {
        if (event instanceof ICancellableEvent cancellable) cancellable.setCanceled(canceled);
    }

    /** The event's mutable damage, or null for an event without one. */
    public static Float damage(Event event) {
        if (event instanceof NpcEvent.DamagedEvent e) return e.damage;
        if (event instanceof NpcEvent.MeleeAttackEvent e) return e.damage;
        if (event instanceof NpcEvent.RangedLaunchedEvent e) return e.damage;
        if (event instanceof PlayerEvent.DamagedEvent e) return e.damage;
        if (event instanceof PlayerEvent.DamagedEntityEvent e) return e.damage;
        return null;
    }

    public static void setDamage(Event event, float damage) {
        if (!Float.isFinite(damage)) return;
        float value = Math.max(0.0f, damage);
        if (event instanceof NpcEvent.DamagedEvent e) e.damage = value;
        else if (event instanceof NpcEvent.MeleeAttackEvent e) e.damage = value;
        else if (event instanceof NpcEvent.RangedLaunchedEvent e) e.damage = value;
        else if (event instanceof PlayerEvent.DamagedEvent e) e.damage = value;
        else if (event instanceof PlayerEvent.DamagedEntityEvent e) e.damage = value;
    }

    /** False when this owner is already inside this hook on this thread (a hook re-firing itself). */
    public static boolean enter(UUID owner, String hook) {
        return ACTIVE.get().add(owner + "|" + hook);
    }

    public static void exit(UUID owner, String hook) {
        ACTIVE.get().remove(owner + "|" + hook);
    }
}
