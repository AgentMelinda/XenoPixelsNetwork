package net.bullettrain.xenopixelsmod.npc.brain.v6;

import java.util.EnumMap;
import java.util.Map;

public final class XenoNpcCooldowns {
    private final Map<XenoNpcActionKind, Long> readyAt = new EnumMap<>(XenoNpcActionKind.class);

    public boolean isReady(XenoNpcActionKind kind, long serverTick) {
        Long until = readyAt.get(kind);
        return until == null || serverTick >= until;
    }

    public void arm(XenoNpcActionKind kind, long serverTick, long durationTicks) {
        if (durationTicks <= 0) return;
        readyAt.put(kind, serverTick + durationTicks);
    }

    public void clear() {
        readyAt.clear();
    }
}