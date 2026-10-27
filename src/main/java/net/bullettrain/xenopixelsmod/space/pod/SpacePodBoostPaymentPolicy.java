package net.bullettrain.xenopixelsmod.space.pod;

/** Tick scheduling rules for the server-authoritative, per-second Ctrl-boost KI charge. */
public final class SpacePodBoostPaymentPolicy {
    public static final long TICKS_PER_SECOND = 20L;

    private SpacePodBoostPaymentPolicy() {}

    public static boolean due(long currentTick, long nextPaymentTick) {
        return currentTick >= nextPaymentTick;
    }

    public static long nextPaymentTick(long paidAtTick) {
        return paidAtTick > Long.MAX_VALUE - TICKS_PER_SECOND
                ? Long.MAX_VALUE : paidAtTick + TICKS_PER_SECOND;
    }
}
