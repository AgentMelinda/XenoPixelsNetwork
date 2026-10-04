package net.bullettrain.xenopixelsmod.npc.bank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A named bank that teller NPCs share.
 *
 * <p>Library content, by the rule in {@code docs/xeno-npc-schema.md} §0: <em>would two NPCs ever
 * want to share this, and would an author expect editing it once to change both?</em> Two tellers
 * in one city obviously offer one bank — a player who deposited with the first expects the second
 * to know about it. So the definition lives in the world store under {@code banks/} and an NPC
 * holds only its id.
 *
 * <p>This is the third worked example of that rule, after {@code NpcDialogSlots} and
 * {@code TransportNetwork}, and it is deliberately the same shape as the latter: id from the
 * filename, a display name, a bounded list, and a {@code load}/{@code save} pair with no schema
 * version of its own.
 *
 * <p><b>What is not here:</b> a player's actual vault contents and unlocked tabs. Those are
 * per-player and live on {@code XenoPlayerData}, which already owns per-player state. Putting them
 * here would give one fact two writers — the thing {@code XenoNpcStoreCategory.PLAYERDATA} is
 * documented permanently empty to avoid.
 */
public final class BankDefinition {

    /**
     * Six tabs, matching the reference editor's fixed "Tab 1..6" rows.
     *
     * <p>Fixed rather than open-ended because the reference's own page is fixed, and because the
     * tab strip is drawn across the top of a 176-wide panel — a seventh would not fit.
     */
    public static final int MAX_TABS = 6;

    /** Nobody should be charged more than everything they have. */
    public static final int MAX_WITHDRAW_FEE_PERCENT = 100;

    private static final String TAG_NAME = "Name";
    private static final String TAG_TABS = "Tabs";
    private static final String TAG_FEE = "WithdrawFee";

    private final String id;
    private String name;
    private final List<BankTab> tabs = new ArrayList<>();
    private int withdrawFeePercent;

    public BankDefinition(String id, String name) {
        this.id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        this.name = name == null || name.isBlank() ? this.id : name.trim();
    }

    /** A usable bank out of the box: one free row, nothing to configure before it works. */
    public static BankDefinition createDefault(String id, String name) {
        BankDefinition bank = new BankDefinition(id, name);
        bank.tabs.add(BankTab.firstTab());
        return bank;
    }

    public String id() {
        return id;
    }

    /** What an operator sees in the picker, and a player sees on the screen. The id is the file. */
    public String name() {
        return name;
    }

    public void setName(String replacement) {
        name = replacement == null || replacement.isBlank() ? id : replacement.trim();
    }

    /**
     * The percentage skimmed off a money withdrawal.
     *
     * <p>Only ever consulted when an economy is present; with none, no withdrawal happens and the
     * number is inert. It is stored regardless so that installing an economy later does not lose
     * an operator's setting.
     */
    public int withdrawFeePercent() {
        return withdrawFeePercent;
    }

    public void setWithdrawFeePercent(int percent) {
        withdrawFeePercent = Math.max(0, Math.min(MAX_WITHDRAW_FEE_PERCENT, percent));
    }

    public List<BankTab> tabs() {
        return Collections.unmodifiableList(tabs);
    }

    public int tabCount() {
        return tabs.size();
    }

    /** One tab by index, or an empty one — never null, and never an exception for a stale index. */
    public BankTab tab(int index) {
        return index >= 0 && index < tabs.size() ? tabs.get(index) : BankTab.empty();
    }

    public boolean addTab(BankTab tab) {
        if (tab == null || tabs.size() >= MAX_TABS) {
            return false;
        }
        tabs.add(tab);
        return true;
    }

    public void setTab(int index, BankTab tab) {
        if (index < 0 || index >= MAX_TABS || tab == null) {
            return;
        }
        while (tabs.size() <= index) {
            tabs.add(BankTab.empty());
        }
        tabs.set(index, tab);
    }

    public void removeTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            tabs.remove(index);
        }
    }

    /**
     * Whether this bank can be opened at all.
     *
     * <p>A bank with no tabs is one an operator is still building. Opening an empty vault screen
     * reads as broken, so the NPC talks instead — the same rule an empty transporter follows.
     */
    public boolean isEmpty() {
        return tabs.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_NAME, name);
        tag.putInt(TAG_FEE, withdrawFeePercent);
        ListTag list = new ListTag();
        for (BankTab tab : tabs) {
            list.add(tab.save());
        }
        tag.put(TAG_TABS, list);
        return tag;
    }

    /**
     * Reads one bank.
     *
     * <p>The id comes from the filename rather than the contents — one fact, one place, the same
     * rule the faction and transport stores follow.
     */
    public static BankDefinition load(String id, CompoundTag tag) {
        BankDefinition bank = new BankDefinition(id, tag == null ? id : tag.getString(TAG_NAME));
        if (tag == null) {
            return bank;
        }
        bank.setWithdrawFeePercent(tag.getInt(TAG_FEE));
        if (!tag.contains(TAG_TABS)) {
            return bank;
        }
        ListTag list = tag.getList(TAG_TABS, Tag.TAG_COMPOUND);
        // Truncated rather than trusted: a hand-edited or corrupted file claiming fifty tabs must
        // not become fifty tabs in memory.
        for (int i = 0; i < Math.min(MAX_TABS, list.size()); i++) {
            bank.tabs.add(BankTab.load(list.getCompound(i)));
        }
        return bank;
    }
}
