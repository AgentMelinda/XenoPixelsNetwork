package net.bullettrain.xenopixelsmod.npc.bank;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bank's data model: what is shared, what is per-player, and where the line is.
 *
 * <p>The split is the whole design. A bank's tabs and costs are shared by every teller that serves
 * it; a player's unlocked tabs and their contents follow the player. Getting either side wrong is
 * visible immediately in game — shared contents would show every player the same vault — so it is
 * worth pinning here.
 */
class BankStoreTest {

    // ------------------------------------------------------------ the definition

    @Test
    void theIdComesFromTheFilenameNotTheContents() {
        // One fact, one place - the rule the faction and transport stores already follow.
        CompoundTag tag = BankDefinition.createDefault("capital", "Capital Bank").save();
        assertFalse(tag.contains("Id"));
        assertEquals("elsewhere", BankDefinition.load("elsewhere", tag).id());
    }

    @Test
    void aBankSurvivesASaveAndLoadWithItsTabsInOrder() {
        BankDefinition bank = new BankDefinition("cities", "Cities");
        bank.setWithdrawFeePercent(5);
        bank.addTab(new BankTab("Free", "", 1, 9, true));
        bank.addTab(new BankTab("Paid", "minecraft:diamond", 3, 18, false));

        BankDefinition read = BankDefinition.load("cities", bank.save());
        assertEquals("Cities", read.name());
        assertEquals(5, read.withdrawFeePercent());
        assertEquals(List.of("Free", "Paid"), read.tabs().stream().map(BankTab::name).toList());
        assertEquals("minecraft:diamond", read.tab(1).costItem());
        assertEquals(3, read.tab(1).costCount());
        assertFalse(read.tab(1).upgradable());
    }

    @Test
    void aFreeTabIsTheOneWithNoCostItem() {
        assertTrue(new BankTab("v", "", 1, 9, true).free());
        assertFalse(new BankTab("v", "minecraft:emerald", 1, 9, true).free());
    }

    @Test
    void theTabCapHoldsOnAddAndOnRead() {
        BankDefinition bank = new BankDefinition("n", "N");
        for (int i = 0; i < BankDefinition.MAX_TABS + 5; i++) {
            bank.addTab(BankTab.empty());
        }
        assertEquals(BankDefinition.MAX_TABS, bank.tabCount());

        // And a hand-edited file claiming more is truncated rather than trusted.
        CompoundTag tag = new CompoundTag();
        ListTag oversized = new ListTag();
        for (int i = 0; i < 50; i++) {
            oversized.add(BankTab.empty().save());
        }
        tag.put("Tabs", oversized);
        assertEquals(BankDefinition.MAX_TABS, BankDefinition.load("n", tag).tabCount());
    }

    @Test
    void slotCountsAreRoundedOntoAWholeRowInsideTheGrid() {
        // A grid of 13 slots has no shape; the screen would have to draw a ragged row.
        assertEquals(9, BankTab.clampSlots(13));
        assertEquals(18, BankTab.clampSlots(14));
        assertEquals(BankTab.MIN_SLOTS, BankTab.clampSlots(0));
        assertEquals(BankTab.MIN_SLOTS, BankTab.clampSlots(-100));
        assertEquals(BankTab.MAX_SLOTS, BankTab.clampSlots(9999));
    }

    @Test
    void theFeeIsBounded() {
        BankDefinition bank = new BankDefinition("n", "N");
        bank.setWithdrawFeePercent(5000);
        assertEquals(BankDefinition.MAX_WITHDRAW_FEE_PERCENT, bank.withdrawFeePercent());
        bank.setWithdrawFeePercent(-20);
        assertEquals(0, bank.withdrawFeePercent());
    }

    @Test
    void anEmptyBankIsNotOpenable() {
        // It talks instead. An empty vault screen reads as broken.
        assertTrue(new BankDefinition("n", "N").isEmpty());
        assertFalse(BankDefinition.createDefault("n", "N").isEmpty());
    }

    @Test
    void aNewBankWorksWithoutBeingConfigured() {
        // An Add button that made something no player could open would be a second step nobody
        // was told about.
        BankDefinition bank = BankDefinition.createDefault("n", "N");
        assertEquals(1, bank.tabCount());
        assertTrue(bank.tab(0).free());
    }

    // ------------------------------------------------------------ the account

    @Test
    void anAccountSurvivesASaveAndLoadWithItsTabsAndMoney() {
        BankAccount account = new BankAccount("capital");
        account.setMoney(250L);
        account.tab(1).unlock();
        account.tab(1).setSlots(27);

        BankAccount read = BankAccount.load("capital", account.save());
        assertEquals(250L, read.money());
        assertTrue(read.tab(1).unlocked());
        assertEquals(27, read.tab(1).slots());
        assertFalse(read.tab(0).unlocked());
    }

    @Test
    void aBankCanNeverOweAPlayer() {
        BankAccount account = new BankAccount("n");
        account.setMoney(-5L);
        assertEquals(0L, account.money());
    }

    @Test
    void anUntouchedAccountIsEmptyAndNotWorthSaving() {
        assertTrue(new BankAccount("n").isEmpty());
        BankAccount used = new BankAccount("n");
        used.tab(0).unlock();
        assertFalse(used.isEmpty());
    }

    @Test
    void everyAccountCarriesAllTabsWhetherTheBankOffersThemOrNot() {
        // So an operator adding a seventh tab later does not have to migrate anybody's account.
        BankAccount account = new BankAccount("n");
        assertEquals(BankAccount.MAX_TABS, BankDefinition.MAX_TABS);
        // Out of range is clamped rather than thrown - a stale tab index must not crash a menu.
        assertNotNull(account.tab(-4));
        assertNotNull(account.tab(9999));
    }

    // ------------------------------------------------------------ unlocking

    @Test
    void aFreeTabIsImplicitlyUnlocked() {
        // Charging nothing and still demanding a click would be a step that means nothing, and it
        // is how a bank's first tab normally reads.
        BankDefinition bank = new BankDefinition("n", "N");
        bank.addTab(new BankTab("Free", "", 1, 18, true));
        BankAccount account = new BankAccount("n");
        assertEquals(18, NpcBankService.unlockedSlots(bank, account, 0));
    }

    @Test
    void aPaidTabOffersNothingUntilItIsBought() {
        BankDefinition bank = new BankDefinition("n", "N");
        bank.addTab(new BankTab("Paid", "minecraft:diamond", 1, 18, true));
        BankAccount account = new BankAccount("n");
        assertEquals(0, NpcBankService.unlockedSlots(bank, account, 0));

        account.tab(0).unlock();
        account.tab(0).setSlots(18);
        assertEquals(18, NpcBankService.unlockedSlots(bank, account, 0));
    }

    @Test
    void aTabIndexTheBankDoesNotOfferUnlocksNothing() {
        BankDefinition bank = BankDefinition.createDefault("n", "N");
        assertEquals(0, NpcBankService.unlockedSlots(bank, new BankAccount("n"), 3));
        assertEquals(0, NpcBankService.unlockedSlots(bank, new BankAccount("n"), -1));
        assertEquals(0, NpcBankService.unlockedSlots(null, new BankAccount("n"), 0));
    }

    @Test
    void upgradingIsRefusedOnATabThatCannotGrow() {
        BankDefinition bank = new BankDefinition("n", "N");
        bank.addTab(new BankTab("Fixed", "", 1, 9, false));
        String refusal = NpcBankService.canUpgrade(bank, new BankAccount("n"), 0);
        assertNotNull(refusal);
        assertTrue(refusal.contains("cannot be made bigger"));
    }

    @Test
    void upgradingIsRefusedOnATabAlreadyAtItsLimit() {
        BankDefinition bank = new BankDefinition("n", "N");
        bank.addTab(new BankTab("Big", "", 1, BankTab.MAX_SLOTS, true));
        String refusal = NpcBankService.canUpgrade(bank, new BankAccount("n"), 0);
        assertNotNull(refusal);
        assertTrue(refusal.contains("as big as it goes"));
    }

    @Test
    void upgradingIsRefusedOnATabTheyHaveNotUnlocked() {
        BankDefinition bank = new BankDefinition("n", "N");
        bank.addTab(new BankTab("Paid", "minecraft:diamond", 1, 9, true));
        String refusal = NpcBankService.canUpgrade(bank, new BankAccount("n"), 0);
        assertNotNull(refusal);
        assertTrue(refusal.contains("Unlock that tab first"));
    }

    @Test
    void anUpgradableTabWithRoomIsAllowed() {
        BankDefinition bank = new BankDefinition("n", "N");
        bank.addTab(new BankTab("Free", "", 1, 9, true));
        assertNull(NpcBankService.canUpgrade(bank, new BankAccount("n"), 0));
    }

    // ------------------------------------------------------------ the profile reference

    @Test
    void anNpcHoldsAReferenceAndNotABank() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.bankId = "capital";
        assertEquals("capital", NpcCombatProfile.fromTag(profile.toTag()).bankId);
    }

    @Test
    void anNpcThatTellsForNothingWritesNothing() {
        assertFalse(new NpcCombatProfile().toTag().contains("BankId"));
    }

    @Test
    void theStoreCategoryIsTheOneThatWasSittingEmpty() {
        assertEquals("banks", XenoNpcStoreCategory.BANKS.folder());
        assertFalse(XenoNpcStoreCategory.BANKS.grouped(),
                "ungrouped, so the store takes an empty group");
    }
}
