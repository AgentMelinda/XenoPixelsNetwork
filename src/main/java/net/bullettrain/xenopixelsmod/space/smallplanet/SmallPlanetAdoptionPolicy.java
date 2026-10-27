package net.bullettrain.xenopixelsmod.space.smallplanet;

import java.util.UUID;

/** Fail-closed recovery policy for persisted generated bodies during startup reconciliation. */
public final class SmallPlanetAdoptionPolicy {
    private SmallPlanetAdoptionPolicy() {}

    public enum Disposition { ADOPT, REMOVE, QUARANTINE }

    /**
     * Only a valid, unedited generated body may be discarded as incomplete or duplicate.
     * An invalid identity cannot establish that the plot is disposable; edited duplicates
     * likewise must be kept even though they cannot own the canonical generated identity.
     */
    public static Disposition classify(boolean identityValid, boolean building, boolean edited, boolean duplicate) {
        if (!identityValid) return Disposition.QUARANTINE;
        if (duplicate) return edited ? Disposition.QUARANTINE : Disposition.REMOVE;
        if (building && !edited) return Disposition.REMOVE;
        return Disposition.ADOPT;
    }

    /** Deterministic winner: edited copy first, then complete unedited copy, then stable UUID order. */
    public static boolean preferCandidate(boolean candidateEdited, boolean candidateBuilding, UUID candidateUuid,
                                         boolean incumbentEdited, boolean incumbentBuilding, UUID incumbentUuid) {
        return preferCandidate(false, candidateEdited, candidateBuilding, candidateUuid,
                false, incumbentEdited, incumbentBuilding, incumbentUuid);
    }

    /** Release suppresses quarantine but does not promote a body over the deterministic canonical winner. */
    public static boolean preferCandidate(boolean candidateReleased, boolean candidateEdited,
                                          boolean candidateBuilding, UUID candidateUuid,
                                          boolean incumbentReleased, boolean incumbentEdited,
                                          boolean incumbentBuilding, UUID incumbentUuid) {
        // Keep the release marker out of identity election: the release command removes the safety hold,
        // but should not silently transfer the canonical orbit/body role to a different duplicate.
        if (candidateEdited != incumbentEdited) return candidateEdited;
        if (!candidateEdited && candidateBuilding != incumbentBuilding) return !candidateBuilding;
        return candidateUuid.compareTo(incumbentUuid) < 0;
    }
}
