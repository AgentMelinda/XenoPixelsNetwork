package net.bullettrain.xenopixelsmod.npc.trade;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A trader NPC's stock.
 *
 * <p>The editor's Trader page has drawn eighteen disabled slots since long before anything backed
 * them. This is what they write into — and building the backing before the rows is the order the
 * repo's no-dead-controls rule asks for, not an accident.
 */
class NpcTradeTest {

    @Test
    void aCompleteStoneTradeBuildsAnOffer() {
        NpcTradeList stock = new NpcTradeList();
        stock.add(new NpcTrade("minecraft:emerald", 1, "", 1,
                "minecraft:stone", 2, 16));
        var offers = NpcTradeOffers.build(stock);
        assertEquals(1, offers.size());
        assertTrue(offers.get(0).getResult().is(net.minecraft.world.item.Items.STONE));
        assertEquals(2, offers.get(0).getResult().getCount());
    }

    private static NpcTrade trade(String cost, String result) {
        return new NpcTrade(cost, 1, "", 1, result, 1, NpcTrade.DEFAULT_MAX_USES);
    }

    // ------------------------------------------------------------ one trade

    @Test
    void itemsAreHeldAsIdsSoAMissingModDoesNotCrash() {
        // A trader authored with a mod installed, opened on a server without it, must degrade to
        // a trade that cannot be offered - not to a crash, and not to a different item.
        NpcTrade t = trade("somemod:ruby", "minecraft:diamond");
        assertEquals("somemod:ruby", t.costA());
        assertEquals("minecraft:diamond", t.result());
    }

    @Test
    void idsAreNormalisedSoTwoSpellingsAreOneTrade() {
        assertEquals("minecraft:diamond", trade("  Minecraft:Diamond  ", "x").costA());
    }

    @Test
    void aHalfFilledRowIsNotOfferable() {
        // It is a row somebody is still typing. Offering it would put an air-for-air exchange in
        // front of a player, which reads as a broken shop rather than an unfinished one.
        assertFalse(trade("minecraft:emerald", "").usable());
        assertFalse(trade("", "minecraft:bread").usable());
        assertTrue(trade("minecraft:emerald", "minecraft:bread").usable());
        assertFalse(NpcTrade.empty().usable());
    }

    @Test
    void countsClampIntoWhatAStackCanHold() {
        // A trade asking for 900 of something could never be satisfied from one slot.
        NpcTrade huge = new NpcTrade("a", 900, "b", -4, "c", 0, 5);
        assertEquals(NpcTrade.MAX_COUNT, huge.countA());
        assertEquals(1, huge.countB(), "a nonsense count is one, not zero");
        assertEquals(1, huge.resultCount());
    }

    @Test
    void zeroUsesMeansUnlimitedRatherThanBroken() {
        // Vanilla has no "infinite" flag; a very large maxUses is how it is expressed. Zero is the
        // readable way for an operator to ask for it.
        assertEquals(NpcTrade.MAX_USES_CAP, new NpcTrade("a", 1, "", 1, "b", 1, 0)
                .effectiveMaxUses());
    }

    @Test
    void aNegativeUseCountFallsBackToTheDefault() {
        // Rather than disabling the trade in a way the editor would not show.
        assertEquals(NpcTrade.DEFAULT_MAX_USES,
                new NpcTrade("a", 1, "", 1, "b", 1, -7).maxUses());
    }

    @Test
    void theSecondCostIsOptional() {
        assertFalse(trade("minecraft:emerald", "minecraft:bread").hasSecondCost());
        assertTrue(new NpcTrade("minecraft:emerald", 2, "minecraft:stick", 1,
                "minecraft:sword", 1, 4).hasSecondCost());
    }

    @Test
    void aTradeSurvivesASaveAndLoad() {
        NpcTrade original = new NpcTrade("minecraft:emerald", 3, "minecraft:stick", 2,
                "minecraft:diamond_sword", 1, 7);
        assertEquals(original, NpcTrade.load(original.save()));
    }

    @Test
    void anUnusedSecondCostIsNotWrittenAtAll() {
        // Keeping the tag as small as the trade actually is.
        CompoundTag tag = trade("minecraft:emerald", "minecraft:bread").save();
        assertFalse(tag.contains("B"));
    }

    // ------------------------------------------------------------ the list

    @Test
    void anOrdinaryNpcWritesNoTradeTagAtAll() {
        // Every NPC in a world carries this object; only a trader should pay for it on disk.
        CompoundTag tag = new CompoundTag();
        new NpcTradeList().saveTo(tag);
        assertTrue(tag.isEmpty());
    }

    @Test
    void authorOrderIsPreservedThroughASaveAndLoad() {
        // Vanilla's trade screen lists offers top to bottom. A stock that reshuffled between
        // openings could not be used to build a shop.
        NpcTradeList list = new NpcTradeList();
        list.add(trade("a", "1"));
        list.add(trade("b", "2"));
        list.add(trade("c", "3"));

        CompoundTag tag = new CompoundTag();
        list.saveTo(tag);
        NpcTradeList read = new NpcTradeList();
        read.loadFrom(tag);
        assertEquals(List.of("a", "b", "c"), read.all().stream().map(NpcTrade::costA).toList());
    }

    @Test
    void theListStopsAtTheEditorsEighteenSlots() {
        NpcTradeList list = new NpcTradeList();
        for (int i = 0; i < NpcTradeList.MAX_TRADES + 5; i++) {
            list.add(trade("item" + i, "out" + i));
        }
        assertEquals(NpcTradeList.MAX_TRADES, list.size());
    }

    @Test
    void aTagClaimingMoreThanTheCapIsTruncatedOnRead() {
        // The cap is a bound on read as well as write; a crafted save must not get past it.
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag oversized = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 100; i++) {
            oversized.add(trade("a" + i, "b").save());
        }
        tag.put("Trades", oversized);

        NpcTradeList read = new NpcTradeList();
        read.loadFrom(tag);
        assertEquals(NpcTradeList.MAX_TRADES, read.size());
    }

    @Test
    void onlyUsableRowsAreOffered() {
        NpcTradeList list = new NpcTradeList();
        list.add(trade("minecraft:emerald", "minecraft:bread"));
        list.add(NpcTrade.empty());
        list.add(trade("minecraft:emerald", ""));

        assertEquals(1, list.offerable().size());
        assertEquals(3, list.size(), "the half-filled rows stay in the editor");
    }

    @Test
    void anNpcWithOnlyBlankRowsHasNothingToSell() {
        NpcTradeList list = new NpcTradeList();
        list.add(NpcTrade.empty());
        assertTrue(list.isEmpty());
    }

    @Test
    void settingPastTheEndPadsRatherThanThrows() {
        // The editor addresses rows by slot number, and slot 5 can be filled before slot 2.
        NpcTradeList list = new NpcTradeList();
        list.set(4, trade("minecraft:emerald", "minecraft:bread"));
        assertEquals(5, list.size());
        assertTrue(list.get(0).costA().isEmpty());
        assertEquals("minecraft:emerald", list.get(4).costA());
    }

    @Test
    void settingPastTheCapIsRefusedRatherThanPadding() {
        NpcTradeList list = new NpcTradeList();
        list.set(500, trade("a", "b"));
        assertEquals(0, list.size());
    }

    @Test
    void removingARowMovesTheLaterOnesUp() {
        NpcTradeList list = new NpcTradeList();
        list.add(trade("a", "1"));
        list.add(trade("b", "2"));
        list.remove(0);
        assertEquals("b", list.get(0).costA());
    }

    @Test
    void readingATagTwiceDoesNotDoubleTheStock() {
        NpcTradeList list = new NpcTradeList();
        list.add(trade("a", "1"));
        CompoundTag tag = new CompoundTag();
        list.saveTo(tag);

        NpcTradeList read = new NpcTradeList();
        read.loadFrom(tag);
        read.loadFrom(tag);
        assertEquals(1, read.size(), "load replaces, it does not append");
    }
}
