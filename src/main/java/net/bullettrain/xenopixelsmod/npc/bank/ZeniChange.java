package net.bullettrain.xenopixelsmod.npc.bank;

/** Pure denomination math; item registry lookup stays in {@link ZeniCash}. */
final class ZeniChange {
    static final long[] DENOMINATIONS = {
            1_000_000, 100_000, 10_000, 1_000, 500, 250, 200, 100, 50, 25, 10, 1
    };

    private ZeniChange() {}

    static long[] counts(long amount) {
        if (amount < 0L || amount > NpcBankMoney.MAX_TRANSFER) return new long[0];
        long[] counts = new long[DENOMINATIONS.length];
        long remaining = amount;
        for (int i = 0; i < counts.length; i++) {
            counts[i] = remaining / DENOMINATIONS[i];
            remaining %= DENOMINATIONS[i];
        }
        return counts;
    }
}
