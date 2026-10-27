package net.bullettrain.xenopixelsmod.space.smallplanet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread cache for repeated per-voxel DMZ permission checks during one game tick. */
final class SmallPlanetKiAdmissionCache {
    private static final int MAX_ENTRIES = 256;
    record Entry(SmallPlanetEditedBodyPolicy.Admission admission, boolean alreadyPinned) {}

    private final Map<UUID, Entry> byBody = new HashMap<>();
    private long tick = Long.MIN_VALUE;

    Entry get(long currentTick, UUID body) {
        beginTick(currentTick);
        return byBody.get(body);
    }

    void put(long currentTick, UUID body, SmallPlanetEditedBodyPolicy.Admission admission, boolean alreadyPinned) {
        if (body == null || admission == null) return;
        beginTick(currentTick);
        if (byBody.size() < MAX_ENTRIES || byBody.containsKey(body)) {
            byBody.put(body, new Entry(admission, alreadyPinned));
            return;
        }
        // A Ki blast may touch many ordinary bodies before reaching a managed small planet. Keep the
        // cheap negative cache bounded, but let a managed admission evict an UNMANAGED entry.
        if (admission != SmallPlanetEditedBodyPolicy.Admission.UNMANAGED) {
            var iterator = byBody.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue().admission() == SmallPlanetEditedBodyPolicy.Admission.UNMANAGED) {
                    iterator.remove();
                    byBody.put(body, new Entry(admission, alreadyPinned));
                    return;
                }
            }
        }
    }

    void markPinned(long currentTick, UUID body) {
        if (body == null) return;
        beginTick(currentTick);
        Entry cached = byBody.get(body);
        if (cached != null && cached.admission() == SmallPlanetEditedBodyPolicy.Admission.ALLOWED)
            byBody.put(body, new Entry(cached.admission(), true));
    }

    private void beginTick(long currentTick) {
        if (tick == currentTick) return;
        tick = currentTick;
        byBody.clear();
    }
}
