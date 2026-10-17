package net.bullettrain.xenopixelsmod.space.universe;

/** Pure coalescing policy for page requests arriving while a player's bounded page job is in flight. */
public final class InfiniteUniversePageQueuePolicy {
    private InfiniteUniversePageQueuePolicy() {}

    /** Keeps the active request stable and stores at most the latest different viewport as its follow-up. */
    public static InfiniteUniversePageRequest followUp(InfiniteUniversePageRequest active,
                                                        InfiniteUniversePageRequest existingFollowUp,
                                                        InfiniteUniversePageRequest incoming) {
        if (active.equals(incoming)) return null;
        return incoming.equals(existingFollowUp) ? existingFollowUp : incoming;
    }
}
