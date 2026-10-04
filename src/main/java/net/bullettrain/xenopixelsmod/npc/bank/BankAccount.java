package net.bullettrain.xenopixelsmod.npc.bank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One player's holdings at one bank.
 *
 * <p>Per-player state, so it lives on {@code XenoPlayerData} rather than in the world store.
 * {@code XenoNpcStoreCategory.PLAYERDATA} is documented permanently empty for exactly this reason:
 * two writers for one fact, with no rule about which wins, is worse than one.
 *
 * <p>The split against {@link BankDefinition} is the ownership rule applied twice over. What the
 * bank <em>offers</em> — tabs, costs, the fee — is shared and belongs to the definition. What this
 * player <em>has</em> — which tabs they opened, how far they upgraded them, what is in them — is
 * theirs and belongs here. Renaming a bank must not touch anybody's items, and depositing must not
 * touch anybody else's vault.
 *
 * <p>Contents are held as a {@code ListTag} in vanilla's own container format
 * ({@code SimpleContainer.createTag}), not as a parsed item list. Nothing here needs to understand
 * an item to store it, and a stack from a mod that is currently absent survives a round trip
 * instead of being silently dropped.
 */
public final class BankAccount {

    /** Tabs beyond what any definition can offer cannot be reached, so they are not kept. */
    public static final int MAX_TABS = BankDefinition.MAX_TABS;

    private static final String TAG_MONEY = "Money";
    private static final String TAG_TABS = "Tabs";
    private static final String TAG_UNLOCKED = "Unlocked";
    private static final String TAG_SLOTS = "Slots";
    private static final String TAG_ITEMS = "Items";

    /** One tab as this player holds it. */
    public static final class Tab {
        private boolean unlocked;
        private int slots = BankTab.SLOTS_PER_ROW;
        private ListTag items = new ListTag();

        public boolean unlocked() {
            return unlocked;
        }

        public void unlock() {
            unlocked = true;
        }

        public int slots() {
            return slots;
        }

        public void setSlots(int value) {
            slots = BankTab.clampSlots(value);
        }

        /** The stored contents, in vanilla's container format. Never null. */
        public ListTag items() {
            return items;
        }

        public void setItems(ListTag replacement) {
            items = replacement == null ? new ListTag() : replacement;
        }
    }

    private final String bankId;
    private final List<Tab> tabs = new ArrayList<>();
    private long money;

    public BankAccount(String bankId) {
        this.bankId = bankId == null ? "" : bankId.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < MAX_TABS; i++) {
            tabs.add(new Tab());
        }
    }

    public String bankId() {
        return bankId;
    }

    /**
     * One tab, always in range.
     *
     * <p>Every account carries all {@value #MAX_TABS} whether the definition offers them or not —
     * an operator adding a seventh tab later must not have to migrate anybody's account, and a
     * locked tab costs one boolean and an empty list.
     */
    public Tab tab(int index) {
        return tabs.get(Math.max(0, Math.min(MAX_TABS - 1, index)));
    }

    /** Money deposited <em>at this bank</em>, as opposed to carried in the player's wallet. */
    public long money() {
        return money;
    }

    /** Never negative: a bank that can owe a player is a bug nobody wants to find later. */
    public void setMoney(long value) {
        money = Math.max(0L, value);
    }

    public boolean isEmpty() {
        if (money > 0L) {
            return false;
        }
        for (Tab tab : tabs) {
            if (tab.unlocked || !tab.items.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TAG_MONEY, money);
        ListTag list = new ListTag();
        for (Tab tab : tabs) {
            CompoundTag entry = new CompoundTag();
            entry.putBoolean(TAG_UNLOCKED, tab.unlocked);
            entry.putInt(TAG_SLOTS, tab.slots);
            entry.put(TAG_ITEMS, tab.items.copy());
            list.add(entry);
        }
        tag.put(TAG_TABS, list);
        return tag;
    }

    public static BankAccount load(String bankId, CompoundTag tag) {
        BankAccount account = new BankAccount(bankId);
        if (tag == null) {
            return account;
        }
        account.setMoney(tag.getLong(TAG_MONEY));
        ListTag list = tag.getList(TAG_TABS, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_TABS, list.size()); i++) {
            CompoundTag entry = list.getCompound(i);
            Tab tab = account.tabs.get(i);
            tab.unlocked = entry.getBoolean(TAG_UNLOCKED);
            tab.setSlots(entry.contains(TAG_SLOTS) ? entry.getInt(TAG_SLOTS)
                    : BankTab.SLOTS_PER_ROW);
            tab.setItems(entry.getList(TAG_ITEMS, Tag.TAG_COMPOUND).copy());
        }
        return account;
    }
}
