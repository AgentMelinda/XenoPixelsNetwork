package net.bullettrain.xenopixelsmod.npc.brain.v6;

import net.bullettrain.xenopixelsmod.compat.npc.brain.v4.NpcBrainBudget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class XenoNpcScheduler {
    private final NpcBrainBudget budget;
    private final XenoNpcCooldowns cooldowns = new XenoNpcCooldowns();
    private final List<Entry> entries = new ArrayList<>();
    private long lastSelectedTick = Long.MIN_VALUE;
    private XenoNpcActionKind lastSelected;
    private int lastSkipped;

    public XenoNpcScheduler(int maximumPerTick) {
        this.budget = new NpcBrainBudget(maximumPerTick);
    }

    public void register(XenoNpcDecision decision, Predicate<Long> condition) {
        entries.add(new Entry(decision, condition));
    }

    public XenoNpcActionKind select(long serverTick) {
        if (!budget.tryAcquire(serverTick)) return null;
        entries.sort(Comparator.comparingInt(e -> e.decision.priority()));
        int skipped = 0;
        for (Entry entry : entries) {
            if (!entry.condition.test(serverTick) || !cooldowns.isReady(entry.decision.kind(), serverTick)) {
                skipped++;
                continue;
            }
            cooldowns.arm(entry.decision.kind(), serverTick, entry.decision.cooldownTicks());
            lastSelectedTick = serverTick;
            lastSelected = entry.decision.kind();
            lastSkipped = skipped;
            return entry.decision.kind();
        }
        lastSelectedTick = serverTick;
        lastSelected = null;
        lastSkipped = skipped;
        return null;
    }

    public XenoNpcActionKind lastSelected() {
        return lastSelected;
    }

    public long lastSelectedTick() {
        return lastSelectedTick;
    }

    public int lastSkipped() {
        return lastSkipped;
    }

    public String diagnostics() {
        return "v6 tick=" + lastSelectedTick + " selected=" + lastSelected + " skipped=" + lastSkipped;
    }

    private record Entry(XenoNpcDecision decision, Predicate<Long> condition) {}
}