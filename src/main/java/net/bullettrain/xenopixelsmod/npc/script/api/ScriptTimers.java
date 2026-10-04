package net.bullettrain.xenopixelsmod.npc.script.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/** Bounded MyNPCs-style timers, advanced once per server tick. */
public final class ScriptTimers {
    private static final int MAX_TIMERS = 64;
    private static final int MAX_INTERVAL = 1_200_000;
    private static final String STORE_KEY = "XenoScriptTimers";
    private final Map<Integer, Timer> timers = new HashMap<>();
    private final CompoundTag owner;

    public ScriptTimers() { this(null); }

    public ScriptTimers(CompoundTag owner) {
        this.owner = owner;
        if (owner == null) return;
        ListTag saved = owner.getList(STORE_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < saved.size() && timers.size() < MAX_TIMERS; i++) {
            CompoundTag one = saved.getCompound(i);
            int interval = one.getInt("Interval");
            if (interval < 1 || interval > MAX_INTERVAL) continue;
            timers.put(one.getInt("Id"), new Timer(interval, one.getBoolean("Repeat"), one.getLong("Due")));
        }
    }

    private void save() {
        if (owner == null) return;
        ListTag saved = new ListTag();
        for (var entry : timers.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putInt("Id", entry.getKey());
            one.putInt("Interval", entry.getValue().interval);
            one.putBoolean("Repeat", entry.getValue().repeating);
            one.putLong("Due", entry.getValue().due);
            saved.add(one);
        }
        owner.put(STORE_KEY, saved);
    }

    private record Timer(int interval, boolean repeating, long due) {}

    public boolean start(int id, int ticks, boolean repeating, long now) {
        if (timers.containsKey(id)) return false;
        return forceStart(id, ticks, repeating, now);
    }

    public boolean forceStart(int id, int ticks, boolean repeating, long now) {
        if (ticks < 1 || ticks > MAX_INTERVAL || (!timers.containsKey(id) && timers.size() >= MAX_TIMERS)) {
            return false;
        }
        timers.put(id, new Timer(ticks, repeating, now + ticks));
        save();
        return true;
    }

    public boolean has(int id) { return timers.containsKey(id); }
    public boolean stop(int id) { boolean removed = timers.remove(id) != null; if (removed) save(); return removed; }
    public boolean reset(int id, long now) {
        Timer timer = timers.get(id);
        if (timer == null) return false;
        timers.put(id, new Timer(timer.interval, timer.repeating, now + timer.interval));
        save();
        return true;
    }
    public void clear() { timers.clear(); save(); }

    public void tick(long now, IntConsumer fire) {
        for (Integer id : java.util.List.copyOf(timers.keySet())) {
            Timer timer = timers.get(id);
            if (timer == null || timer.due > now) continue;
            if (timer.repeating) timers.put(id, new Timer(timer.interval, true, now + timer.interval));
            else timers.remove(id);
            save();
            fire.accept(id);
        }
    }
}
