package net.bullettrain.xenopixelsmod.client.space;

/** Pure tick-clock policy for retrying bounded logical Atlas page requests. */
final class InfiniteUniverseRequestThrottle {
    private InfiniteUniverseRequestThrottle() {}

    /**
     * Returns whether the retry interval is still active. A rewound client clock starts a new epoch,
     * and subtraction overflow represents an elapsed interval too large to remain throttled.
     */
    static boolean isActive(long now, long lastRequest, long intervalTicks) {
        if (intervalTicks <= 0 || now < lastRequest) return false;
        long elapsed = now - lastRequest;
        return elapsed >= 0 && elapsed < intervalTicks;
    }
}
