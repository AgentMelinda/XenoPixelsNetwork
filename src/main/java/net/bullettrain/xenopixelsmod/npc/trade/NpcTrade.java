package net.bullettrain.xenopixelsmod.npc.trade;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/**
 * One thing a trader NPC will swap.
 *
 * <p>Items are held as registry id strings rather than as {@code ItemStack}s. A trader authored on
 * a server with a mod installed, opened on one without it, must degrade to a trade that cannot be
 * offered — not to a crash, and not to a silently different item. The id survives the mod being
 * absent; a resolved {@code Item} does not.
 *
 * <p>Two costs because vanilla's {@code MerchantOffer} takes two, and a "two emeralds and a stick
 * for a sword" trade is ordinary. The second is optional and blank when unused.
 */
public record NpcTrade(String costA, int countA, String costB, int countB,
                       String result, int resultCount, int maxUses) {

    /** Vanilla's own default for a villager trade, and a sane one for an NPC. */
    public static final int DEFAULT_MAX_USES = 16;

    /** A stack is 64; a trade asking for more could never be satisfied from one slot. */
    public static final int MAX_COUNT = 64;

    /** Past this an operator wants an infinite trade, which is what 0 means. */
    public static final int MAX_USES_CAP = 100_000;

    public NpcTrade {
        costA = clean(costA);
        costB = clean(costB);
        result = clean(result);
        countA = clamp(countA);
        countB = clamp(countB);
        resultCount = clamp(resultCount);
        // Zero is "unlimited", which vanilla expresses as a very large maxUses. Negative is not a
        // meaning anybody intended, so it folds into the default rather than disabling the trade
        // in a way the editor would not show.
        maxUses = maxUses < 0 ? DEFAULT_MAX_USES : Math.min(MAX_USES_CAP, maxUses);
    }

    private static String clean(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static int clamp(int count) {
        return Math.max(1, Math.min(MAX_COUNT, count));
    }

    /** An empty row in the editor: nothing to buy, nothing to sell. */
    public static NpcTrade empty() {
        return new NpcTrade("", 1, "", 1, "", 1, DEFAULT_MAX_USES);
    }

    /**
     * Whether this trade can actually be offered.
     *
     * <p>A trade needs something to give and something to ask for. A half-filled row in the editor
     * is a row an operator is still typing, not a trade — offering it would put an air-for-air
     * exchange in front of a player.
     */
    public boolean usable() {
        return !costA.isEmpty() && !result.isEmpty();
    }

    /** Whether the second cost is in play. */
    public boolean hasSecondCost() {
        return !costB.isEmpty();
    }

    /** Vanilla treats a huge maxUses as unlimited; zero is how an operator says so. */
    public int effectiveMaxUses() {
        return maxUses == 0 ? MAX_USES_CAP : maxUses;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("A", costA);
        tag.putInt("Ac", countA);
        if (!costB.isEmpty()) {
            tag.putString("B", costB);
            tag.putInt("Bc", countB);
        }
        tag.putString("R", result);
        tag.putInt("Rc", resultCount);
        tag.putInt("Max", maxUses);
        return tag;
    }

    public static NpcTrade load(CompoundTag tag) {
        if (tag == null) {
            return empty();
        }
        return new NpcTrade(
                tag.getString("A"),
                tag.contains("Ac") ? tag.getInt("Ac") : 1,
                tag.getString("B"),
                tag.contains("Bc") ? tag.getInt("Bc") : 1,
                tag.getString("R"),
                tag.contains("Rc") ? tag.getInt("Rc") : 1,
                tag.contains("Max") ? tag.getInt("Max") : DEFAULT_MAX_USES);
    }
}
