package net.bullettrain.xenopixelsmod.space.physics;

import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.WeakHashMap;

/** One fair activation-rate token shared by chart-planet, asteroid, and small-planet builders per server. */
public final class SpaceBuildActivationGate {
    private static final Map<MinecraftServer, SpaceActivationBudget> BUDGETS = new WeakHashMap<>();

    private SpaceBuildActivationGate() {}

    public static synchronized boolean tryAcquire(MinecraftServer server, SpaceActivationBudget.Owner owner, long gameTime) {
        return BUDGETS.computeIfAbsent(server, ignored -> new SpaceActivationBudget()).tryAcquire(owner, gameTime);
    }

    /** Command-path contention counters; absent gate state means no activation request has been seen. */
    public static synchronized String report(MinecraftServer server) {
        SpaceActivationBudget budget = BUDGETS.get(server);
        StringBuilder out = new StringBuilder("shared body activation gate denials (since server start):");
        for (SpaceActivationBudget.Owner owner : SpaceActivationBudget.Owner.values()) {
            out.append("\n  ").append(owner.name().toLowerCase(java.util.Locale.ROOT))
                    .append(" denied=").append(budget == null ? 0 : budget.deniedRequests(owner))
                    .append(" rateLimited=").append(budget == null ? 0 : budget.rateLimitDenials(owner))
                    .append(" arbitration=").append(budget == null ? 0 : budget.arbitrationDenials(owner))
                    .append(" lastDeniedTick=").append(budget == null ? "never" : formatTick(budget.lastDeniedTick(owner)));
        }
        return out.toString();
    }

    private static String formatTick(long tick) { return tick == Long.MIN_VALUE ? "never" : Long.toString(tick); }

    public static synchronized void forget(MinecraftServer server) { BUDGETS.remove(server); }
}
