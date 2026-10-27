package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityRules;
import net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegritySnapshot;
import net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityUpdate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/** Pure paged-snapshot assembler; clients publish state only after every page has arrived. */
final class PlanetIntegritySnapshotAssembler {
    private long revision = -1;
    private double scarredAt = 50.0, criticalAt = 20.0;
    private Map<String, Double> integrity = Map.of();
    private Assembly pending;
    private final Map<String, PlanetIntegrityUpdate> queuedUpdates = new HashMap<>();

    private static final class Assembly {
        final long revision;
        final int pageCount;
        final double scarredAt, criticalAt;
        final Map<String, Double> entries = new HashMap<>();
        final Set<Integer> pages = new HashSet<>();
        Assembly(PlanetIntegritySnapshot page) {
            revision = page.revision(); pageCount = page.pageCount();
            scarredAt = page.scarredAt(); criticalAt = page.criticalAt();
        }
    }

    boolean accept(PlanetIntegritySnapshot page) {
        if (page.revision() < revision) return false;
        if (pending == null || page.revision() > pending.revision) pending = new Assembly(page);
        Assembly assembly = pending;
        if (page.revision() != assembly.revision || page.pageCount() != assembly.pageCount
                || Double.compare(page.scarredAt(), assembly.scarredAt) != 0
                || Double.compare(page.criticalAt(), assembly.criticalAt) != 0) return false;
        if (!assembly.pages.add(page.pageIndex())) return false;
        for (var entry : page.entries()) {
            if (assembly.entries.putIfAbsent(entry.bodyId(), entry.integrity()) != null
                    || assembly.entries.size() > PlanetIntegritySnapshot.MAX_ENTRIES) {
                pending = null;
                return false;
            }
        }
        if (assembly.pages.size() != assembly.pageCount) return false;
        integrity = Map.copyOf(assembly.entries);
        revision = assembly.revision;
        scarredAt = assembly.scarredAt;
        criticalAt = assembly.criticalAt;
        pending = null;
        var orderedUpdates = new ArrayList<>(queuedUpdates.values());
        orderedUpdates.sort(Comparator.comparingLong(PlanetIntegrityUpdate::revision));
        for (PlanetIntegrityUpdate update : orderedUpdates) applyUpdate(update);
        queuedUpdates.clear();
        return true;
    }

    boolean accept(PlanetIntegrityUpdate update) {
        if (update.revision() <= revision) return false;
        if (pending != null) {
            queuedUpdates.merge(update.bodyId(), update,
                    (oldUpdate, newUpdate) -> oldUpdate.revision() >= newUpdate.revision() ? oldUpdate : newUpdate);
            return false;
        }
        return applyUpdate(update);
    }

    private boolean applyUpdate(PlanetIntegrityUpdate update) {
        if (update.revision() <= revision) return false;
        var next = new HashMap<>(integrity);
        if (update.integrity() == 100.0) next.remove(update.bodyId());
        else next.put(update.bodyId(), update.integrity());
        integrity = Map.copyOf(next);
        revision = update.revision();
        scarredAt = update.scarredAt();
        criticalAt = update.criticalAt();
        return true;
    }

    double integrity(String bodyId) { return integrity.getOrDefault(bodyId, 100.0); }

    PlanetIntegrityRules.Stage stage(String bodyId) {
        double value = integrity(bodyId);
        if (value >= 100.0) return PlanetIntegrityRules.Stage.INTACT;
        return PlanetIntegrityRules.stage(value, new PlanetIntegrityRules.Thresholds(scarredAt, criticalAt));
    }

    long revision() { return revision; }
    Map<String, Double> values() { return integrity; }

    void clear() {
        revision = -1;
        scarredAt = 50.0;
        criticalAt = 20.0;
        integrity = Map.of();
        pending = null;
        queuedUpdates.clear();
    }
}
