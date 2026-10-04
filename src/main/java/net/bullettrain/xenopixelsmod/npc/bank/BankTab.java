package net.bullettrain.xenopixelsmod.npc.bank;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/**
 * One page of a bank's vault, and what it costs a player to open.
 *
 * <p>The cost is an item held as a <b>registry id string</b> rather than a resolved {@code Item},
 * for the reason {@link net.bullettrain.xenopixelsmod.npc.trade.NpcTrade} already records: a bank
 * authored on a server with a mod installed, opened on one without it, must degrade to a tab that
 * cannot be unlocked — not to a crash, and not to a silently different item.
 *
 * <p>A blank cost means the tab is free, which is how the first tab normally reads.
 */
public record BankTab(String name, String costItem, int costCount, int startSlots,
                      boolean upgradable) {

    /** A vault page is a chest grid, so slots come in rows of nine. */
    public static final int SLOTS_PER_ROW = 9;

    /** Six rows: the tallest grid vanilla's generic container menus offer. */
    public static final int MAX_SLOTS = SLOTS_PER_ROW * 6;

    /** A tab nobody can use is not worth offering, so one row is the floor. */
    public static final int MIN_SLOTS = SLOTS_PER_ROW;

    /** Past a stack the cost could never be paid from one slot. Matches {@code NpcTrade}. */
    public static final int MAX_COST_COUNT = 64;

    public BankTab {
        name = name == null || name.isBlank() ? "Vault" : name.trim();
        costItem = costItem == null ? "" : costItem.trim().toLowerCase(Locale.ROOT);
        costCount = Math.max(1, Math.min(MAX_COST_COUNT, costCount));
        startSlots = clampSlots(startSlots);
    }

    /**
     * Rounds a slot count onto a whole row inside the allowed range.
     *
     * <p>A grid of 13 slots has no shape, and the screen would have to either draw a ragged row or
     * silently pick one — so the rounding happens here, once, where the number is stored.
     */
    public static int clampSlots(int slots) {
        int rows = Math.round(slots / (float) SLOTS_PER_ROW);
        return Math.max(MIN_SLOTS, Math.min(MAX_SLOTS, rows * SLOTS_PER_ROW));
    }

    /** The default first tab: one free row. */
    public static BankTab firstTab() {
        return new BankTab("Vault", "", 1, SLOTS_PER_ROW, true);
    }

    /** A blank row in the editor. */
    public static BankTab empty() {
        return new BankTab("Vault", "", 1, SLOTS_PER_ROW, false);
    }

    /** Whether opening this tab costs anything. A blank item is free. */
    public boolean free() {
        return costItem.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        if (!costItem.isEmpty()) {
            tag.putString("Cost", costItem);
            tag.putInt("CostCount", costCount);
        }
        tag.putInt("Start", startSlots);
        tag.putBoolean("Upgradable", upgradable);
        return tag;
    }

    public static BankTab load(CompoundTag tag) {
        if (tag == null) {
            return empty();
        }
        return new BankTab(
                tag.getString("Name"),
                tag.getString("Cost"),
                tag.contains("CostCount") ? tag.getInt("CostCount") : 1,
                tag.contains("Start") ? tag.getInt("Start") : SLOTS_PER_ROW,
                tag.getBoolean("Upgradable"));
    }
}
