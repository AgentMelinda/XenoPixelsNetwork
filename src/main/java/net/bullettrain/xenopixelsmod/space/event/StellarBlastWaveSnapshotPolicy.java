package net.bullettrain.xenopixelsmod.space.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure candidate builder for the bounded client packet; it cannot access a server, chart evaluator, or SavedData. */
final class StellarBlastWaveSnapshotPolicy {
    record ChartStar(String id, String displayName) {}
    record Result(StellarBlastWaveSnapshot snapshot, int omitted) {}

    private StellarBlastWaveSnapshotPolicy() {}

    public static Result build(Map<String, StellarLifecycleSavedData.Entry> entries,
            Collection<ChartStar> chartStars, long nowTick, int durationTicks, double speedBlocksPerTick,
            boolean enabled) {
        if (entries == null || chartStars == null)
            throw new IllegalArgumentException("stellar snapshot inputs are required");
        if (!enabled)
            return new Result(StellarBlastWaveSnapshot.empty(false, durationTicks, speedBlocksPerTick), 0);

        Map<String, ChartStar> starsById = new HashMap<>();
        for (ChartStar star : chartStars) if (star != null && star.id() != null) starsById.put(star.id(), star);
        List<StellarBlastWaveSnapshot.Wave> candidates = new ArrayList<>();
        for (var saved : entries.entrySet()) {
            StellarLifecycleSavedData.Entry entry = saved.getValue();
            if (entry == null || !StellarStage.SUPERNOVA.name().equals(entry.stage()) || entry.origin() == null
                    || !StellarBlastWaveMath.activeAt(entry.startedAtTick(), nowTick, durationTicks)) continue;
            ChartStar star = starsById.get(saved.getKey());
            if (star == null) continue;
            StellarLifecycleSavedData.Origin origin = entry.origin();
            try {
                candidates.add(new StellarBlastWaveSnapshot.Wave(saved.getKey(), star.displayName(),
                        origin.x(), origin.y(), origin.z(), nowTick - entry.startedAtTick()));
            } catch (IllegalArgumentException malformed) {
                // Invalid persisted packet rows are omitted instead of breaking login or fanout.
            }
        }
        StellarBlastWaveSelection.Selection<StellarBlastWaveSnapshot.Wave> selected =
                StellarBlastWaveSelection.select(candidates, StellarBlastWaveSnapshot.MAX_WAVES,
                        Comparator.comparing(StellarBlastWaveSnapshot.Wave::id));
        return new Result(new StellarBlastWaveSnapshot(true, durationTicks, speedBlocksPerTick,
                selected.values()), selected.omitted());
    }
}
