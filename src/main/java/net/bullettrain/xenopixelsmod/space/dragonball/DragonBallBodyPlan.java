package net.bullettrain.xenopixelsmod.space.dragonball;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

/**
 * Whole-set startup plan for the planet-form Super Dragon Ball slots.
 *
 * <p>{@link DragonBallBodyReconciliation} decides one slot in isolation and therefore cannot see the two
 * cross-slot states that must stop an entire cycle: a ledger that binds one Sable incarnation to two slots, and a
 * loaded body whose tag claims two slots. Acting on either would reach
 * {@link DragonBallSavedData#bindPlanetBody(int, UUID)}, which throws once a duplicate exists in the saved ledger,
 * or would adopt one body into two slots and leave that state on disk.
 *
 * <p>Deliberately free of Minecraft and Sable types so the Sable tag scan stays a thin adapter and this ordering is
 * unit-testable without a server. The slot count is the caller's {@code savedBodyIdsBySlot} size; this class does
 * not read the ball count from anywhere else.
 */
public final class DragonBallBodyPlan {

    /** Whether the plan's per-slot actions may be acted on at all. */
    public enum Overall {
        /** Per-slot actions are safe to execute. */
        PROCEED,
        /** One saved ledger UUID is bound to two slots; nothing may be created or adopted until it is repaired. */
        DUPLICATE_LEDGER,
        /** One loaded Sable body claims two slots; adopting either would orphan the other. */
        DUPLICATE_LOADED_BODY,
        /** The loaded-body scan did not run, so absence of a body carries no information. */
        SCAN_UNAVAILABLE
    }

    /** One slot's decision. {@code slot} is 1-based, matching {@link DragonBallSavedData.Ball#identity}. */
    public record Entry(int slot, DragonBallBodyReconciliation.Action action, UUID bodyId, String reason) {}

    /**
     * The plan for one cycle. A blocked cycle ({@link Overall#DUPLICATE_LEDGER} or
     * {@link Overall#DUPLICATE_LOADED_BODY}) carries no entries at all, because it must not act on any slot;
     * {@code reason} then explains the block so the caller can log it. {@link Overall#SCAN_UNAVAILABLE} is the
     * deliberate exception: it does carry per-slot entries, and every one of them is {@code CONFLICT}, so the
     * caller can report which slots it was unable to judge while {@link #createsNothing()} still reads true.
     */
    public record Plan(Overall overall, List<Entry> entries, String reason) {
        public Plan { entries = entries == null ? List.of() : List.copyOf(entries); }

        public Entry entry(int slot) {
            for (Entry entry : entries) if (entry.slot() == slot) return entry;
            throw new IllegalArgumentException("no planned entry for slot " + slot);
        }

        /** True when no slot may cause a body to be created or adopted this cycle. */
        public boolean createsNothing() {
            if (overall != Overall.PROCEED) return true;
            return entries.stream().noneMatch(e -> e.action() == DragonBallBodyReconciliation.Action.CREATE
                    || e.action() == DragonBallBodyReconciliation.Action.ADOPT);
        }
    }

    private DragonBallBodyPlan() {}

    /**
     * Plans one reconciliation cycle.
     *
     * @param savedBodyIdsBySlot the ledger's bound Sable incarnation per slot, index 0 is slot 1; a {@code null}
     *                           element means that slot recorded no body. Its size fixes the slot count.
     * @param loadedTaggedBodiesBySlot slot to the Sable body UUIDs currently loaded and tagged for that slot; an
     *                                 absent key means none, while a present {@code null} value means that slot
     *                                 was not scanned and conflicts rather than creating. A {@code null} map means
     *                                 the scan itself is unavailable.
     * @throws IllegalArgumentException when {@code savedBodyIdsBySlot} is null/empty or a scan key is outside the
     *                                 slot range the ledger declares
     */
    public static Plan plan(List<UUID> savedBodyIdsBySlot, Map<Integer, List<UUID>> loadedTaggedBodiesBySlot) {
        if (savedBodyIdsBySlot == null || savedBodyIdsBySlot.isEmpty())
            throw new IllegalArgumentException("SDB ledger slot list is required");
        int slots = savedBodyIdsBySlot.size();

        Map<Integer, List<UUID>> loaded = loadedTaggedBodiesBySlot == null
                ? Map.of() : loadedTaggedBodiesBySlot;
        for (Integer key : loaded.keySet())
            if (key == null || key < 1 || key > slots)
                throw new IllegalArgumentException("SDB loaded-body scan key " + key + " is outside slots 1.." + slots);

        String ledgerDuplicate = firstDuplicatedLedger(savedBodyIdsBySlot);
        if (ledgerDuplicate != null)
            return new Plan(Overall.DUPLICATE_LEDGER, List.of(),
                    "saved ledger binds one Sable body to two slots: " + ledgerDuplicate);

        if (loadedTaggedBodiesBySlot == null) {
            var entries = new ArrayList<Entry>(slots);
            for (int index = 0; index < slots; index++) {
                var decision = DragonBallBodyReconciliation.decide(savedBodyIdsBySlot.get(index), null);
                entries.add(new Entry(index + 1, decision.action(), decision.bodyId(), decision.reason()));
            }
            return new Plan(Overall.SCAN_UNAVAILABLE, entries,
                    "loaded Sable body scan did not run");
        }

        String scanDuplicate = firstDuplicatedLoadedBody(loaded, slots);
        if (scanDuplicate != null)
            return new Plan(Overall.DUPLICATE_LOADED_BODY, List.of(),
                    "one loaded Sable body claims two slots: " + scanDuplicate);

        var entries = new ArrayList<Entry>(slots);
        for (int index = 0; index < slots; index++) {
            int slot = index + 1;
            // get() cannot tell the two cases apart, so presence is read separately: an absent key is a
            // confirmed "nothing tagged here", while a present null means this slot was never scanned.
            // Collapsing the second into the first would authorize a duplicate body beside a saved incarnation.
            var decision = DragonBallBodyReconciliation.decide(savedBodyIdsBySlot.get(index),
                    loaded.containsKey(slot) ? loaded.get(slot) : List.of());
            entries.add(new Entry(slot, decision.action(), decision.bodyId(), decision.reason()));
        }
        return new Plan(Overall.PROCEED, entries, null);
    }

    /**
     * Defers only fresh creation for slots without a currently published, validated physical assignment.
     * Existing, adopted, waiting, and conflict entries are preserved so a reload cannot orphan an already-saved
     * Sable body merely because its chart assignment was temporarily removed.
     */
    public static Plan deferUnassignedCreates(Plan plan, Set<Integer> physicallySupportedSlots) {
        if (plan == null) throw new IllegalArgumentException("SDB plan is required");
        if (physicallySupportedSlots == null)
            throw new IllegalArgumentException("supported SDB slots are required");
        if (plan.overall() != Overall.PROCEED) return plan;
        var entries = new ArrayList<Entry>(plan.entries().size());
        for (Entry entry : plan.entries()) {
            if (entry.action() == DragonBallBodyReconciliation.Action.CREATE
                    && !physicallySupportedSlots.contains(entry.slot())) {
                entries.add(new Entry(entry.slot(), DragonBallBodyReconciliation.Action.DEFERRED_NO_ASSIGNMENT,
                        null, "no physically supported chart assignment is published"));
            } else {
                entries.add(entry);
            }
        }
        return new Plan(plan.overall(), entries, plan.reason());
    }

    private static String firstDuplicatedLedger(List<UUID> saved) {
        var seen = new LinkedHashMap<UUID, Integer>();
        for (int index = 0; index < saved.size(); index++) {
            UUID bodyId = saved.get(index);
            if (bodyId == null) continue;
            Integer earlier = seen.putIfAbsent(bodyId, index + 1);
            if (earlier != null) return bodyId + " (slots " + earlier + " and " + (index + 1) + ")";
        }
        return null;
    }

    private static String firstDuplicatedLoadedBody(Map<Integer, List<UUID>> loaded, int slots) {
        var owner = new LinkedHashMap<UUID, Integer>();
        for (int slot = 1; slot <= slots; slot++) {
            List<UUID> tagged = loaded.get(slot);
            if (tagged == null) continue;
            for (UUID bodyId : tagged) {
                if (bodyId == null) continue;
                Integer earlier = owner.putIfAbsent(bodyId, slot);
                if (earlier != null) return bodyId + " (slots " + earlier + " and " + slot + ")";
            }
        }
        return null;
    }

}
