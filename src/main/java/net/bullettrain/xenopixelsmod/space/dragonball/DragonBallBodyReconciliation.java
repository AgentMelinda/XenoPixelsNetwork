package net.bullettrain.xenopixelsmod.space.dragonball;

import java.util.List;
import java.util.UUID;

/** Pure startup decision for reconciling one saved SDB slot with Sable-restored tagged bodies. */
public final class DragonBallBodyReconciliation {
    public enum Action { EXISTING, ADOPT, WAIT_FOR_SABLE, CREATE, DEFERRED_NO_ASSIGNMENT, CONFLICT }

    public record Decision(Action action, UUID bodyId, String reason) {}

    private DragonBallBodyReconciliation() {}

    /**
     * Never authorize a replacement while a saved Sable incarnation is expected but not loaded, and never pick
     * arbitrarily when either the slot ledger or loaded body tags disagree.
     */
    public static Decision decide(UUID savedBodyId, List<UUID> loadedTaggedBodies) {
        if (loadedTaggedBodies == null)
            return new Decision(Action.CONFLICT, savedBodyId, "loaded body scan is unavailable");
        if (loadedTaggedBodies.stream().anyMatch(java.util.Objects::isNull))
            return new Decision(Action.CONFLICT, savedBodyId, "loaded body scan contains a null identity");
        if (loadedTaggedBodies.size() > 1)
            return new Decision(Action.CONFLICT, savedBodyId, "multiple tagged Sable bodies claim this slot");
        if (loadedTaggedBodies.size() == 1) {
            UUID loaded = loadedTaggedBodies.get(0);
            if (savedBodyId != null && !savedBodyId.equals(loaded))
                return new Decision(Action.CONFLICT, loaded, "saved slot UUID disagrees with tagged Sable body");
            if (savedBodyId == null)
                return new Decision(Action.ADOPT, loaded, "adopt the unique tagged Sable body");
            return new Decision(Action.EXISTING, loaded, "saved Sable body is loaded");
        }
        if (savedBodyId != null)
            return new Decision(Action.WAIT_FOR_SABLE, savedBodyId, "Sable may still restore the saved body");
        return new Decision(Action.CREATE, null, "no saved or loaded body exists");
    }
}
