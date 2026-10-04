package net.bullettrain.xenopixelsmod.npc.script.api.xeno.event;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * NpcAPI.events(): forwards every call to a real bus and counts registrations, because
 * IEventBus has no listener query (bus 8.0.5). Typed events are built only when someone listens.
 * {@link #unregister} does not decrement: a stale "listening" answer only costs an unread event.
 */
public final class XenoEventBus implements IEventBus {
    private final IEventBus delegate;
    private final AtomicInteger registrations = new AtomicInteger();

    public XenoEventBus(IEventBus delegate) { this.delegate = Objects.requireNonNull(delegate); }

    public boolean hasListeners() { return registrations.get() > 0; }

    @Override public void register(Object target) { registrations.incrementAndGet(); delegate.register(target); }
    @Override public <T extends Event> void addListener(Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(consumer); }
    @Override public <T extends Event> void addListener(Class<T> type, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(type, consumer); }
    @Override public <T extends Event> void addListener(EventPriority priority, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(priority, consumer); }
    @Override public <T extends Event> void addListener(EventPriority priority, Class<T> type, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(priority, type, consumer); }
    @Override public <T extends Event> void addListener(EventPriority priority, boolean receiveCanceled, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(priority, receiveCanceled, consumer); }
    @Override public <T extends Event> void addListener(EventPriority priority, boolean receiveCanceled, Class<T> type, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(priority, receiveCanceled, type, consumer); }
    @Override public <T extends Event> void addListener(boolean receiveCanceled, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(receiveCanceled, consumer); }
    @Override public <T extends Event> void addListener(boolean receiveCanceled, Class<T> type, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(receiveCanceled, type, consumer); }
    @Override public void unregister(Object target) { delegate.unregister(target); }
    @Override public <T extends Event> T post(T event) { return delegate.post(event); }
    @Override public <T extends Event> T post(EventPriority phase, T event) { return delegate.post(phase, event); }
    @Override public void start() { delegate.start(); }
}
