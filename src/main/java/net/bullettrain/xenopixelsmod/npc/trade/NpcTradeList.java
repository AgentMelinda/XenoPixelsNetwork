package net.bullettrain.xenopixelsmod.npc.trade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A trader NPC's stock.
 *
 * <p>Capped at {@link #MAX_TRADES}, which is the number of slots the editor's Trader page has
 * always drawn. Those eighteen rows existed as disabled placeholders long before anything backed
 * them; this is what they write into.
 *
 * <p>Order is the order an author typed, and is preserved on every path — vanilla's trade screen
 * lists offers top to bottom, and a stock that reshuffled itself between openings would be unusable
 * to build a shop with.
 */
public final class NpcTradeList {

    /** The Trader page draws eighteen slots, so eighteen is what an operator can fill. */
    public static final int MAX_TRADES = 18;

    private static final String TAG_TRADES = "Trades";

    private final List<NpcTrade> trades = new ArrayList<>();

    /** Every trade, in author order, including rows still being filled in. */
    public List<NpcTrade> all() {
        return Collections.unmodifiableList(trades);
    }

    /**
     * Only the trades that can actually be offered.
     *
     * <p>A half-filled row is one an operator is still typing. Offering it would put an
     * air-for-air exchange in front of a player, which reads as a broken shop rather than an
     * unfinished one.
     */
    public List<NpcTrade> offerable() {
        List<NpcTrade> out = new ArrayList<>();
        for (NpcTrade trade : trades) {
            if (trade.usable()) {
                out.add(trade);
            }
        }
        return List.copyOf(out);
    }

    /** Whether this NPC has anything to sell at all. */
    public boolean isEmpty() {
        return offerable().isEmpty();
    }

    public int size() {
        return trades.size();
    }

    /** One row, or an empty trade when the index is past the end. */
    public NpcTrade get(int index) {
        return index >= 0 && index < trades.size() ? trades.get(index) : NpcTrade.empty();
    }

    /** Adds a row, refusing past the cap rather than growing without bound. */
    public boolean add(NpcTrade trade) {
        if (trade == null || trades.size() >= MAX_TRADES) {
            return false;
        }
        trades.add(trade);
        return true;
    }

    /** Replaces one row, padding with empties when the index is past the end. */
    public void set(int index, NpcTrade trade) {
        if (index < 0 || index >= MAX_TRADES || trade == null) {
            return;
        }
        while (trades.size() <= index) {
            trades.add(NpcTrade.empty());
        }
        trades.set(index, trade);
    }

    /** Drops one row. Later rows move up, as the editor's list shows them. */
    public void remove(int index) {
        if (index >= 0 && index < trades.size()) {
            trades.remove(index);
        }
    }

    public void clear() {
        trades.clear();
    }

    public void copyFrom(NpcTradeList other) {
        trades.clear();
        if (other != null) {
            trades.addAll(other.trades);
        }
    }

    /** Written only when there is something to write, so an ordinary NPC's tag is unchanged. */
    public void saveTo(CompoundTag tag) {
        if (trades.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (NpcTrade trade : trades) {
            list.add(trade.save());
        }
        tag.put(TAG_TRADES, list);
    }

    public void loadFrom(CompoundTag tag) {
        trades.clear();
        if (tag == null || !tag.contains(TAG_TRADES)) {
            return;
        }
        ListTag list = tag.getList(TAG_TRADES, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_TRADES, list.size()); i++) {
            trades.add(NpcTrade.load(list.getCompound(i)));
        }
    }
}
