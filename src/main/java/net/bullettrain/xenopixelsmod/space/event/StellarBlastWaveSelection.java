package net.bullettrain.xenopixelsmod.space.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Shared deterministic cap for server wave effects and client-visible wave snapshots. */
public final class StellarBlastWaveSelection {
    public record Selection<T>(List<T> values, int omitted) {
        public Selection { values = List.copyOf(values); }
        public boolean truncated() { return omitted > 0; }
    }

    private StellarBlastWaveSelection() {}

    /** Keeps the fast in-order path under the cap; overflow is resolved by a stable caller-provided priority. */
    public static <T> Selection<T> select(Collection<T> candidates, int maximum,
            Comparator<? super T> overflowPriority) {
        if (candidates == null || maximum < 0 || overflowPriority == null)
            throw new IllegalArgumentException("invalid wave selection");
        if (candidates.size() <= maximum) return new Selection<>(new ArrayList<>(candidates), 0);
        var ordered = new ArrayList<>(candidates);
        ordered.sort(overflowPriority);
        int count = maximum;
        List<T> values = new ArrayList<>(count);
        values.addAll(ordered.subList(0, count));
        return new Selection<>(values, candidates.size() - count);
    }
}
