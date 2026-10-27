package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallIdentity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only check that each physical planet binding the server sends clients names the body that carries that
 * identity. A client draws a planet's map on whichever UUID the binding gives it, so a wrong binding paints one
 * body's art on another body without any client-side check noticing. This class only compares; it never repairs.
 */
public final class PlanetBindingAudit {
    private PlanetBindingAudit() {}

    /** Which owner's persisted identity tag the body is judged by, derived from the binding id. */
    public enum Kind { CHART, SMALL_PLANET, SUPER_DRAGON_BALL }

    public enum Verdict {
        /** The body's own identity tag names this binding id. */
        MATCH,
        /** The body's tag names a different id: the client is drawing this id's art on another body. */
        MISMATCH,
        /** The body exists but carries no readable identity tag for this kind. */
        UNTAGGED,
        /** No loaded, non-removed body has this UUID. */
        NOT_LOADED,
        /** The same UUID is bound to more than one id. */
        DUPLICATE_UUID
    }

    /** What the server knows about the bound body: whether it is loaded, and the id its own tag claims. */
    public record Observed(boolean loaded, String taggedId) {}

    public record Finding(String bindingId, UUID uuid, Kind kind, Verdict verdict, String taggedId) {
        public boolean ok() { return verdict == Verdict.MATCH; }
    }

    public static Kind kindOf(String bindingId) {
        if (DragonBallIdentity.slotFromPlanetKey(bindingId) > 0) return Kind.SUPER_DRAGON_BALL;
        if (bindingId != null && bindingId.startsWith("xenopixelsmod:smallplanet/")) return Kind.SMALL_PLANET;
        return Kind.CHART;
    }

    /** Compares every binding with what its body's own tag says. Bounded by the binding list it is given. */
    public static List<Finding> audit(List<PhysicalPlanetBindings.Entry> bindings,
                                      java.util.function.BiFunction<Kind, UUID, Observed> observe) {
        Map<UUID, Integer> uuidCounts = new HashMap<>();
        for (var entry : bindings) uuidCounts.merge(entry.uuid(), 1, Integer::sum);
        List<Finding> findings = new ArrayList<>(bindings.size());
        for (var entry : bindings) {
            Kind kind = kindOf(entry.bodyId());
            Observed observed = observe.apply(kind, entry.uuid());
            String tagged = observed == null ? null : observed.taggedId();
            Verdict verdict;
            if (uuidCounts.get(entry.uuid()) > 1) verdict = Verdict.DUPLICATE_UUID;
            else if (observed == null || !observed.loaded()) verdict = Verdict.NOT_LOADED;
            else if (tagged == null || tagged.isEmpty()) verdict = Verdict.UNTAGGED;
            else verdict = tagged.equals(entry.bodyId()) ? Verdict.MATCH : Verdict.MISMATCH;
            findings.add(new Finding(entry.bodyId(), entry.uuid(), kind, verdict, tagged));
        }
        return findings;
    }

    public static String describe(Finding finding) {
        return (finding.ok() ? "OK " : "WRONG ") + finding.kind().name().toLowerCase(java.util.Locale.ROOT) + " "
                + finding.bindingId() + " -> body " + finding.uuid() + " : " + finding.verdict()
                + (finding.taggedId() == null ? "" : " (body tag says " + finding.taggedId() + ")");
    }
}
