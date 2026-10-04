package net.bullettrain.xenopixelsmod.npc.bank;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Bank role, end to end.
 *
 * <p>Source-level, because the menu needs a registered {@code MenuType} and the screen needs a
 * client, and neither exists in a unit test. What is checkable here is the shape of the decisions —
 * which is where these bugs actually are: a missing registration, a lock enforced on only one of
 * the two routes in, a control drawn with nothing behind it.
 */
class BankWiringTest {

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    void theRoleExistsAndHasItsOwnEntityType() throws IOException {
        assertEquals("bank", XenoNpcRole.BANK.id());
        String entities = code("src/main/java/net/bullettrain/xenopixelsmod/missile",
                "ModEntities.java");
        assertTrue(entities.contains("xeno_npc_bank"));
        assertTrue(entities.contains("case BANK -> XENO_NPC_BANK.get()"));
        assertTrue(entities.contains("event.put(XENO_NPC_BANK.get(), attributes)"),
                "a LivingEntity type without attributes fails on first spawn");
    }

    @Test
    void theRoleHasARendererAndADefinition() throws IOException {
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/client",
                "ClientModEvents.java").contains("XENO_NPC_BANK.get()"),
                "a registered entity type with no renderer fails on first spawn");
        assertTrue(Files.exists(RepoRoot.of(
                        "src/main/resources/data/xenopixelsmod/xeno_npcs/roles", "bank.json")),
                "without the JSON, XenoNpcRoleDefinitions warns and silently defaults");
    }

    // ------------------------------------------------------------ the menu

    @Test
    void theMenuTypeIsRegisteredAndBoundToItsScreen() throws IOException {
        // The mod's first container menu. A missing screen binding shows an empty screen with no
        // error anywhere, which is the failure worth a test of its own.
        String menus = code("src/main/java/net/bullettrain/xenopixelsmod/npc/bank",
                "ModMenus.java");
        assertTrue(menus.contains("Registries.MENU"));
        assertTrue(menus.contains("IMenuTypeExtension.create"),
                "the plain supplier has nowhere to put the tab and slot counts");
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod",
                "XenoPixelsMod.java").contains("ModMenus.register"),
                "a DeferredRegister nobody registers silently registers nothing");
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/client",
                "ClientModEvents.java").contains("RegisterMenuScreensEvent"));
    }

    @Test
    void aLockedSlotRefusesBothRoutesIn() throws IOException {
        // mayPlace alone would still let a shift-click or a double-click gather pull items out.
        String menu = code("src/main/java/net/bullettrain/xenopixelsmod/npc/bank",
                "XenoNpcBankMenu.java");
        int locked = menu.indexOf("class LockedSlot");
        assertTrue(locked > 0);
        String body = menu.substring(locked);
        assertTrue(body.contains("mayPlace"), "nothing can be put in");
        assertTrue(body.contains("mayPickup"), "and nothing can be taken out");
    }

    @Test
    void shiftClickOnlyEverMovesIntoTheUnlockedPart() throws IOException {
        // Otherwise a shift-click could put a stack somewhere the player then could not reach.
        String menu = code("src/main/java/net/bullettrain/xenopixelsmod/npc/bank",
                "XenoNpcBankMenu.java");
        assertTrue(menu.contains("quickMoveStack"),
                "an unimplemented quickMoveStack is the classic shift-click loop");
        assertTrue(menu.contains("moveItemStackTo(stack, 0, unlockedSlots, false)"),
                "the destination is the unlocked range, not the whole grid");
    }

    @Test
    void walkingAwayClosesTheVault() throws IOException {
        String menu = code("src/main/java/net/bullettrain/xenopixelsmod/npc/bank",
                "XenoNpcBankMenu.java");
        assertTrue(menu.contains("stillValid"));
        assertTrue(menu.contains("MAX_DISTANCE_SQ"));
    }

    // ------------------------------------------------------------ authority

    @Test
    void theServerChecksEverythingTheClientCouldHaveLiedAbout() throws IOException {
        String packet = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcBankPacket.java");
        assertTrue(packet.contains("role() != XenoNpcRole.BANK"), "the entity is a bank");
        assertTrue(packet.contains("MAX_DISTANCE_SQ"), "the player is close enough");
        assertTrue(packet.contains("Banks.get(profile.bankId)"),
                "the NPC's own profile names the bank, never the client");
        assertTrue(packet.contains("tabIndex >= bank.tabCount()"),
                "and the tab is one this bank actually offers");
    }

    @Test
    void theCostIsTakenBeforeTheUnlockIsRecorded() throws IOException {
        // The order is the whole guarantee: a refused payment must leave both the items and the
        // lock exactly as they were.
        String service = code("src/main/java/net/bullettrain/xenopixelsmod/npc/bank",
                "NpcBankService.java");
        int take = service.indexOf("takeCost(player, offered)");
        int unlock = service.indexOf("held.unlock()", take);
        assertTrue(take > 0 && unlock > take);
    }

    @Test
    void thePriceIsCountedInFullBeforeAnyIsTaken() throws IOException {
        // Removing as it counted would leave a player short when the total fell one behind.
        String service = code("src/main/java/net/bullettrain/xenopixelsmod/npc/bank",
                "NpcBankService.java");
        int counted = service.indexOf("found < tab.costCount()");
        int removed = service.indexOf("inventory.removeItem(slot, remaining)");
        assertTrue(counted > 0 && removed > counted);
    }

    @Test
    void anItemFromAnAbsentModCannotBeBoughtWithAndDoesNotCrash() throws IOException {
        String service = code("src/main/java/net/bullettrain/xenopixelsmod/npc/bank",
                "NpcBankService.java");
        assertTrue(service.contains("BuiltInRegistries.ITEM.getOptional"));
        assertTrue(service.contains("item == null"), "and an unresolved item refuses the unlock");
    }

    // ------------------------------------------------------------ the vault contents

    @Test
    void theVaultIsWrittenBackOnEveryChangeRatherThanOnClose() throws IOException {
        // A save-on-close loses a vault to a crash or a kick, and removed() is not a guarantee.
        String service = code("src/main/java/net/bullettrain/xenopixelsmod/npc/bank",
                "NpcBankService.java");
        assertTrue(service.contains("vault.addListener("));
        assertTrue(service.contains("createTag("));
    }

    @Test
    void aPlayersVaultIsPerPlayerAndNotInTheStore() throws IOException {
        // The half of the ownership rule that would be invisible until two players shared a vault.
        // Raw, not code(): the subject here is the javadoc, which code() strips.
        String category = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/npc/store",
                        "XenoNpcStoreCategory.java"), StandardCharsets.UTF_8);
        int banks = category.indexOf("BANKS(\"banks\"");
        String javadoc = category.substring(Math.max(0, banks - 900), banks);
        assertTrue(javadoc.contains("XenoPlayerData"),
                "the BANKS category must say where accounts actually live");
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/capability",
                "XenoPlayerData.java").contains("MAX_BANK_ACCOUNTS"),
                "an unbounded account map is an unbounded player file");
    }

    @Test
    void anAccountSurvivesDeath() throws IOException {
        // copyFrom runs on respawn. Dying must not empty a player's vault.
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/capability",
                "XenoPlayerData.java").contains("other.bankAccounts.forEach"));
    }

    // ------------------------------------------------------------ money stays optional

    @Test
    void exactlyOneFileInTheNpcSystemMentionsTheEconomy() throws IOException {
        // The containment is the point: the NPC system must not come to depend on a mod that may
        // not be installed, so one file knows MMO Econ exists and answers "no economy" when it
        // does not.
        String money = code("src/main/java/net/bullettrain/xenopixelsmod/npc/bank",
                "NpcBankMoney.java");
        assertTrue(money.contains("MmoEconBridge"));
        for (String file : new String[]{"NpcBankService.java", "XenoNpcBankMenu.java",
                "BankDefinition.java", "BankAccount.java", "Banks.java"}) {
            assertFalse(code("src/main/java/net/bullettrain/xenopixelsmod/npc/bank", file)
                    .contains("MmoEconBridge"), file + " must go through NpcBankMoney");
        }
    }

    @Test
    void cashControlsRemainUsableWithoutTheOptionalWalletEconomy() throws IOException {
        String screen = code("src/main/java/net/bullettrain/xenopixelsmod/client/npc/bank",
                "XenoNpcBankScreen.java");
        assertTrue(screen.contains("ACTION_DEPOSIT_CASH"));
        assertTrue(screen.contains("ACTION_WITHDRAW_CASH"));
        assertTrue(screen.contains("if (menu.moneyAvailable())"),
                "only the wallet mode switch requires MMO Econ");
    }

    @Test
    void theServerRechecksTheEconomyEvenThoughTheClientHidTheButton() throws IOException {
        String packet = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcBankPacket.java");
        assertEquals(2, packet.split("NpcBankMoney.available\\(\\)", -1).length - 1,
                "both deposit and withdraw re-check it");
    }

    @Test
    void moneyIsDebitedBeforeItIsCredited() throws IOException {
        // The other order credits from a balance that then turns out to be short, which is how an
        // economy gets money printed into it.
        String money = code("src/main/java/net/bullettrain/xenopixelsmod/npc/bank",
                "NpcBankMoney.java");
        int withdrawn = money.indexOf("MmoEconBridge.withdraw(player.getUUID(), wanted)");
        int credited = money.indexOf("account.setMoney(account.money() + wanted)", withdrawn);
        assertTrue(withdrawn > 0 && credited > withdrawn);
    }

    @Test
    void aFailedPayoutPutsTheMoneyBack() throws IOException {
        String money = code("src/main/java/net/bullettrain/xenopixelsmod/npc/bank",
                "NpcBankMoney.java");
        int failed = money.indexOf("!MmoEconBridge.deposit(player.getUUID(), paid)");
        assertTrue(failed > 0);
        assertTrue(money.indexOf("account.setMoney(account.money() + wanted)", failed) > failed,
                "otherwise a refused payout leaves the player short");
    }

    @Test
    void theEconomyWrapperUsesASignatureThatWasActuallyVerified() throws IOException {
        // addBalance is recorded in docs/shops-and-plots.md from a javap run against MMO Econ
        // 1.1.0. It returns void, so success is "the invoke completed" - the same trap
        // subtractBalance already documented.
        String bridge = code("src/main/java/net/bullettrain/xenopixelsmod/compat/mmoecon",
                "MmoEconBridge.java");
        assertTrue(bridge.contains("invokeStaticVoid(balanceManager(), \"addBalance\""));
        assertTrue(Files.readString(RepoRoot.of("docs", "shops-and-plots.md"),
                StandardCharsets.UTF_8).contains("addBalance(UUID, long)"));
    }

    // ------------------------------------------------------------ the editor and the sync

    @Test
    void theBanksPageIsBackedBySomethingTheClientActuallyHas() throws IOException {
        // Banks live in the world store, which only the server reads. Without a sync the page
        // would be permanently empty - the bug the faction import hit.
        String editor = code("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java");
        assertTrue(editor.contains("ClientBanks.all()"));
        assertFalse(editor.contains("disabledField(\"Tab \" + slot + \" Cost\""),
                "the disabled mirror should be gone, not sitting beside the live rows");
    }

    @Test
    void theSyncIsPushedOnMutationAndNotOnlyOnJoin() throws IOException {
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/npc/faction",
                "XenoFactionSync.java").contains("SyncBanksPacket.current()"),
                "join and /reload");
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcStoreWritePacket.java").contains("SyncBanksPacket.current()"),
                "and every write, which is the half that got missed for factions");
    }

    @Test
    void theSyncCarriesNoAccountData() throws IOException {
        // What a player holds never reaches another player's client.
        String packet = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "SyncBanksPacket.java");
        assertFalse(packet.contains("BankAccount"));
    }

    @Test
    void theKeyIsWhitelistedAndTheEditorWritesIt() throws IOException {
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcSavePolicy.java").contains("\"BankId\""));
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java").contains("profile.bankId = entry.id()"));
    }

    @Test
    void theProtocolWasBumpedForTheNewPackets() throws IOException {
        // Two new packets, one of them changing what the client is sent on join.
        assertTrue(net.bullettrain.xenopixelsmod.network.ProtocolVersion.current() >= 86,
                "the bank packets needed at least protocol 86");
        String network = code("src/main/java/net/bullettrain/xenopixelsmod/network",
                "ModNetwork.java");
        assertTrue(network.contains("XenoNpcBankPacket.class"));
        assertTrue(network.contains("SyncBanksPacket.class"));
    }
}
