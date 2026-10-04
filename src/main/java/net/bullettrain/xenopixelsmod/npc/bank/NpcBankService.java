package net.bullettrain.xenopixelsmod.npc.bank;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Everything the bank does on the server.
 *
 * <p>Opening, unlocking and upgrading all live here rather than in the packet, so the packet stays
 * what it should be — a set of checks in front of one call — and so the rules can be exercised by a
 * test without a network.
 *
 * <p><b>The player's vault contents are written back on every change</b>, not on close. A
 * save-on-close would lose a vault to a crash or a kick, and {@code removed()} is not a guarantee.
 * The cost is bounded: it only runs while that one player has that one tab open.
 */
public final class NpcBankService {

    private NpcBankService() {
    }

    /**
     * Opens one tab of this NPC's bank for one player.
     *
     * @return false when there is nothing to open, so the caller lets the NPC talk instead - the
     *         same contract {@code TransportMenu.open} follows
     */
    public static boolean open(ServerPlayer player, XenoNpcEntity npc, int requestedTab) {
        if (player == null || npc == null) {
            return false;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        // Resolved through the store rather than read off the NPC: a bank an operator has since
        // deleted resolves to nothing, and an empty vault screen reads as broken.
        BankDefinition bank = Banks.get(profile.bankId);
        if (bank == null || bank.isEmpty()) {
            return false;
        }
        BankAccount account = accountFor(player, bank.id());
        if (account == null) {
            // The per-player cap is reached. Saying so beats opening a vault that cannot be saved.
            player.sendSystemMessage(Component.literal("You have accounts at too many banks."));
            return true;
        }

        int tabIndex = Math.max(0, Math.min(bank.tabCount() - 1, requestedTab));
        int unlockedSlots = unlockedSlots(bank, account, tabIndex);

        BankAccount.Tab held = account.tab(tabIndex);
        SimpleContainer vault = new SimpleContainer(XenoNpcBankMenu.VAULT_SLOTS);
        vault.fromTag(held.items(), player.level().registryAccess());
        vault.addListener(container -> held.setItems(
                ((SimpleContainer) container).createTag(player.level().registryAccess())));

        boolean money = NpcBankMoney.available();
        long wallet = NpcBankMoney.wallet(player);
        int fee = bank.withdrawFeePercent();
        String name = bank.name();
        int entityId = npc.getId();
        long vaultMoney = account.money();
        int tabCount = bank.tabCount();

        player.openMenu(new SimpleMenuProvider(
                (windowId, inventory, opener) -> new XenoNpcBankMenu(windowId, inventory, vault,
                        npc, entityId, bank.id(), name, tabIndex, tabCount, unlockedSlots,
                        money, vaultMoney, wallet, fee),
                Component.literal(name)),
                buf -> {
                    buf.writeVarInt(entityId);
                    buf.writeUtf(bank.id(), 64);
                    buf.writeUtf(name, 64);
                    buf.writeVarInt(tabIndex);
                    buf.writeVarInt(tabCount);
                    buf.writeVarInt(unlockedSlots);
                    buf.writeBoolean(money);
                    buf.writeVarLong(vaultMoney);
                    buf.writeVarLong(wallet);
                    buf.writeVarInt(fee);
                });
        return true;
    }

    /**
     * How many slots this player can actually use in one tab.
     *
     * <p>A tab whose definition names no cost is <b>implicitly unlocked</b>: charging nothing and
     * still demanding a click to unlock would be a step that means nothing, and it is how a bank's
     * first tab normally reads.
     */
    public static int unlockedSlots(BankDefinition bank, BankAccount account, int tabIndex) {
        if (bank == null || account == null || tabIndex < 0 || tabIndex >= bank.tabCount()) {
            return 0;
        }
        BankTab offered = bank.tab(tabIndex);
        BankAccount.Tab held = account.tab(tabIndex);
        if (!held.unlocked() && !offered.free()) {
            return 0;
        }
        // A tab the player has never opened has not had its size set yet, so it takes the
        // definition's starting size rather than the account's default.
        return held.unlocked() ? held.slots() : offered.startSlots();
    }

    /**
     * Buys a tab.
     *
     * <p>The cost is taken <b>before</b> the unlock is recorded and only if it was taken in full,
     * so a refused payment leaves both the items and the lock exactly as they were.
     *
     * @return null on success, or a reason to show the player
     */
    @Nullable
    public static String unlock(ServerPlayer player, BankDefinition bank, BankAccount account,
                                int tabIndex) {
        if (player == null || bank == null || account == null
                || tabIndex < 0 || tabIndex >= bank.tabCount()) {
            return "That tab does not exist.";
        }
        BankAccount.Tab held = account.tab(tabIndex);
        BankTab offered = bank.tab(tabIndex);
        if (held.unlocked() || offered.free()) {
            return "You already have that tab.";
        }
        if (!takeCost(player, offered)) {
            return "You cannot afford that tab.";
        }
        held.unlock();
        held.setSlots(offered.startSlots());
        return null;
    }

    /**
     * Buys another row on a tab the player already has.
     *
     * <p>Charged at the tab's own cost again, which is the simplest rule an operator can predict:
     * a second row costs what the first did.
     *
     * @return null on success, or a reason to show the player
     */
    @Nullable
    public static String upgrade(ServerPlayer player, BankDefinition bank, BankAccount account,
                                 int tabIndex) {
        if (player == null) {
            return "That tab does not exist.";
        }
        String refusal = canUpgrade(bank, account, tabIndex);
        if (refusal != null) {
            return refusal;
        }
        BankTab offered = bank.tab(tabIndex);
        BankAccount.Tab held = account.tab(tabIndex);
        int current = unlockedSlots(bank, account, tabIndex);
        if (!takeCost(player, offered)) {
            return "You cannot afford that upgrade.";
        }
        // unlock() as well as setSlots(): an implicitly-unlocked free tab has never been recorded
        // as unlocked, and without this its upgrade would be forgotten on the next open.
        held.unlock();
        held.setSlots(current + BankTab.SLOTS_PER_ROW);
        return null;
    }

    /**
     * Whether a tab could be made bigger, ignoring whether the player can pay.
     *
     * <p>Split out of {@link #upgrade} so the rule is decidable without a live player - it was
     * reachable only behind an argument guard, which meant a test could believe it had checked
     * the rule when it had only tripped the guard.
     *
     * @return null when the upgrade is allowed, or the reason it is not
     */
    @Nullable
    public static String canUpgrade(BankDefinition bank, BankAccount account, int tabIndex) {
        if (bank == null || account == null || tabIndex < 0 || tabIndex >= bank.tabCount()) {
            return "That tab does not exist.";
        }
        if (!bank.tab(tabIndex).upgradable()) {
            return "That tab cannot be made bigger.";
        }
        int current = unlockedSlots(bank, account, tabIndex);
        if (current <= 0) {
            return "Unlock that tab first.";
        }
        if (current >= BankTab.MAX_SLOTS) {
            return "That tab is already as big as it goes.";
        }
        return null;
    }

    /** This player's account at a bank, or null when they are at the cap. */
    @Nullable
    public static BankAccount accountFor(ServerPlayer player, String bankId) {
        return XenoCapabilities.get(player)
                .map(data -> data.bankAccount(bankId))
                .orElse(null);
    }

    /**
     * Takes a tab's cost out of the player's inventory, all or nothing.
     *
     * <p>Counts first and removes second. Removing as it counted would leave a player short when
     * the total fell one item behind the price.
     */
    static boolean takeCost(ServerPlayer player, BankTab tab) {
        if (tab.free()) {
            return true;
        }
        ResourceLocation id = ResourceLocation.tryParse(tab.costItem());
        if (id == null) {
            return false;
        }
        // An item from a mod that is not installed resolves to nothing. The tab then simply cannot
        // be unlocked, which is the documented degradation - not a crash, and not a free tab.
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        int found = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        if (found < tab.costCount()) {
            return false;
        }
        int remaining = tab.costCount();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                remaining -= inventory.removeItem(slot, remaining).getCount();
            }
        }
        return remaining <= 0;
    }
}
