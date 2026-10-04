package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How many commands one NPC's scripts may issue per game tick. In 1.21 a command issued while
 * another command runs is queued into the same execution and runs after it, so a hook that
 * triggers itself through a command (a ki-attack command re-firing rangedLaunched) is not stopped
 * by thread-local guards and would run to vanilla's 65536-command limit, stalling the server.
 * The budget ends any such loop within one tick.
 */
public final class NpcScriptCommandBudget {
    /** Far above what a hook needs (the ECMA example's init issues 10), far below a runaway loop. */
    public static final int PER_TICK = 32;
    private static final int MAX_TRACKED = 4096;
    private static final long WARN_INTERVAL_MS = 60_000;

    private record Window(long tick, int used) {}

    private static final Map<UUID, Window> USED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_WARN = new ConcurrentHashMap<>();

    private NpcScriptCommandBudget() {}

    /** True when this NPC may issue one more command in {@code tick}; counts it. */
    public static boolean tryConsume(UUID npc, long tick) {
        if (USED.size() > MAX_TRACKED) USED.clear();
        Window window = USED.compute(npc, (id, old) ->
                old == null || old.tick != tick ? new Window(tick, 1) : new Window(tick, old.used + 1));
        return window.used <= PER_TICK;
    }

    /** One warning per NPC per minute when its scripts hit the budget. */
    public static void refused(UUID npc, String name, String command) {
        long now = System.currentTimeMillis();
        Long last = LAST_WARN.get(npc);
        if (last != null && now - last < WARN_INTERVAL_MS) return;
        if (LAST_WARN.size() > MAX_TRACKED) LAST_WARN.clear();
        LAST_WARN.put(npc, now);
        XenoPixelsMod.LOGGER.warn("NPC script {} ({}) issued more than {} commands in one tick; refusing '{}'."
                + " A hook is probably re-triggering itself through a command.", name, npc, PER_TICK, command);
    }
}
