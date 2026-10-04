package net.bullettrain.xenopixelsmod.npc.trade;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Trader page stops being dead controls.
 *
 * <p>Eighteen {@code disabledAction} slots had been drawn on that page for a long time with
 * nothing behind them. They are live now because something finally reads what they write:
 * {@code XenoNpcEntity} implements vanilla's {@code Merchant}, so a trader with stock opens the
 * ordinary trade screen — with vanilla's client sync, result slot and use counting included rather
 * than reimplemented.
 */
class TraderWiringTest {

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    void tradesRideTheProfileAndSurviveASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.trades.add(new NpcTrade("minecraft:emerald", 2, "", 1, "minecraft:bread", 3, 8));

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(1, read.trades.size());
        assertEquals("minecraft:emerald", read.trades.get(0).costA());
        assertEquals(3, read.trades.get(0).resultCount());
    }

    @Test
    void anOrdinaryNpcCarriesNoTradeTag() {
        // Every NPC in a world holds this object; only a trader should pay for it on disk.
        assertFalse(new NpcCombatProfile().toTag().contains("Trades"));
    }

    @Test
    void theEntityImplementsVanillaMerchant() throws IOException {
        // The point of the whole approach: the screen, the sync and the use counting are vanilla's.
        String entity = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcEntity.java");
        assertTrue(entity.contains("net.minecraft.world.item.trading.Merchant"),
                "a bespoke shop screen would mean reimplementing all of it");
        assertTrue(entity.contains("openTradingScreen("));
    }

    @Test
    void offersAreRebuiltRatherThanStored() throws IOException {
        // An operator editing the stock while a shop is open must not leave a stale copy behind.
        String entity = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcEntity.java");
        assertTrue(entity.contains("NpcTradeOffers.build("));
    }

    @Test
    void onlyATraderWithStockOpensAShop() throws IOException {
        // An empty trader should still talk. Opening a blank trade screen would look broken.
        String entity = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcEntity.java");
        assertTrue(entity.contains("role() == XenoNpcRole.TRADER"));
        assertTrue(entity.contains("!shopProfile.trades.isEmpty()"));
    }

    @Test
    void shiftClickStillReachesTheDialogue() throws IOException {
        // Otherwise a trader could never be given a conversation, or reach the debug readout.
        String entity = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcEntity.java");
        int shop = entity.indexOf("role() == XenoNpcRole.TRADER");
        assertTrue(shop >= 0);
        String guard = entity.substring(Math.max(0, shop - 250), shop);
        assertTrue(guard.contains("isShiftKeyDown()"), "the shop must yield to shift-click");
    }

    @Test
    void theEighteenDeadSlotsAreGone() throws IOException {
        String editor = code("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java");
        assertFalse(editor.contains("disabledAction(\"Trade Slot \" + slot)"),
                "the placeholder loop is what this replaced");
        assertTrue(editor.contains("profile.trades"), "and the rows now write real stock");
    }

    @Test
    void matchingSwitchesAreLiveAndOnlyLinkedMarketsStayDisabled() throws IOException {
        // Ignore damage / Ignore NBT gained a consumer (NpcTradeOffers.StrictOffer); Linked
        // Marketname still has no shared-market store, so it stays an honest placeholder.
        String editor = code("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java");
        assertTrue(editor.contains("toggle(\"Ignore damage\", profile.tradeIgnoreDamage"));
        assertTrue(editor.contains("toggle(\"Ignore NBT\", profile.tradeIgnoreNbt"));
        assertTrue(editor.contains("disabledField(\"Linked Marketname\""));
        String offers = code("src/main/java/net/bullettrain/xenopixelsmod/npc/trade",
                "NpcTradeOffers.java");
        assertTrue(offers.contains("class StrictOffer extends MerchantOffer"));
    }

    @Test
    void theKeyIsWhitelistedAndTheShapeSampleCarriesIt() throws IOException {
        // A whitelisted key absent from PROFILE_SHAPE is rejected at the type check - the trap
        // that bit NpcLines. Trades is written only when non-empty, so the sample needs one.
        String policy = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcSavePolicy.java");
        assertTrue(policy.contains("\"Trades\""));
        assertTrue(policy.contains("sample.trades.add("),
                "otherwise every trade save is refused as a wrong tag type");
    }

    @Test
    void aTradeWrittenByTheEditorPassesTheWhitelistShapeCheck() {
        // The end-to-end shape guarantee, cheaply: what the editor writes must match what the
        // policy expects, or saving a shop fails at the last step.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.trades.add(new NpcTrade("minecraft:emerald", 1, "", 1, "minecraft:bread", 1, 16));
        CompoundTag written = profile.toTag();
        assertTrue(written.contains("Trades"));
        assertEquals(net.minecraft.nbt.Tag.TAG_LIST, written.get("Trades").getId());
    }
}
