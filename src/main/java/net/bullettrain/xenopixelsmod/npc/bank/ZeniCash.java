package net.bullettrain.xenopixelsmod.npc.bank;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.item.ModsItems;
import net.bullettrain.xenopixelsmod.item.custom.ZeniCashItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;

/** Atomic, server-side exchange between physical Zeni and an NPC bank vault. */
public final class ZeniCash {
    private static final int PLAYER_SLOTS = 36;
    private static final List<DeferredHolder<Item, Item>> DESCENDING = List.of(
            ModsItems.ZENI_NOTE_1000000, ModsItems.ZENI_NOTE_100000,
            ModsItems.ZENI_NOTE_10000, ModsItems.ZENI_1000, ModsItems.ZENI_500,
            ModsItems.ZENI_250, ModsItems.ZENI_200, ModsItems.ZENI_100,
            ModsItems.ZENI_50, ModsItems.ZENI_25, ModsItems.ZENI_10, ModsItems.ZENI_1);

    private ZeniCash() {}

    public static long value(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ZeniCashItem cash))
            return 0L;
        return XenoPixelsMod.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())
                ? cash.zeni() : 0L;
    }

    /** All carried cash is deposited together, or nothing changes. Returns its value. */
    public static long depositAll(ServerPlayer player, BankAccount account) {
        if (player == null || account == null) return 0L;
        Inventory inventory = player.getInventory();
        long total = 0L;
        for (int i = 0; i < PLAYER_SLOTS; i++) {
            ItemStack stack = inventory.getItem(i);
            total += value(stack) * stack.getCount();
        }
        if (total <= 0L || total > NpcBankMoney.MAX_TRANSFER
                || account.money() > Long.MAX_VALUE - total) return 0L;
        for (int i = 0; i < PLAYER_SLOTS; i++) {
            if (value(inventory.getItem(i)) > 0L) inventory.setItem(i, ItemStack.EMPTY);
        }
        account.setMoney(account.money() + total);
        inventory.setChanged();
        return total;
    }

    /** Returns the cash paid after the bank fee. Full inventories leave both sides unchanged. */
    public static long withdraw(ServerPlayer player, BankAccount account, long amount, int feePercent) {
        if (player == null || account == null || amount <= 0L
                || amount > NpcBankMoney.MAX_TRANSFER || account.money() < amount) return 0L;
        long paid = amount - NpcBankMoney.fee(amount, feePercent);
        if (paid <= 0L) return 0L;
        Inventory inventory = player.getInventory();
        ItemStack[] current = new ItemStack[PLAYER_SLOTS];
        for (int i = 0; i < PLAYER_SLOTS; i++) current[i] = inventory.getItem(i);
        ItemStack[] planned = planInsert(current, change(paid));
        if (planned == null) return 0L;
        account.setMoney(account.money() - amount);
        for (int i = 0; i < PLAYER_SLOTS; i++) {
            if (!ItemStack.matches(current[i], planned[i])) inventory.setItem(i, planned[i]);
        }
        inventory.setChanged();
        return paid;
    }

    /** Exact change, split to Minecraft's maximum stack size. */
    public static List<ItemStack> change(long value) {
        long[] counts = ZeniChange.counts(value);
        if (counts.length == 0) return List.of();
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            Item item = DESCENDING.get(i).get();
            long count = counts[i];
            while (count > 0L) {
                int stackSize = (int) Math.min(count, item.getDefaultMaxStackSize());
                result.add(new ItemStack(item, stackSize));
                count -= stackSize;
            }
        }
        return result;
    }

    /** Plans without mutating the source, so a full inventory cannot debit the vault. */
    static ItemStack[] planInsert(ItemStack[] current, List<ItemStack> payout) {
        if (current == null || current.length != PLAYER_SLOTS || payout == null) return null;
        ItemStack[] slots = new ItemStack[PLAYER_SLOTS];
        for (int i = 0; i < PLAYER_SLOTS; i++)
            slots[i] = current[i] == null ? ItemStack.EMPTY : current[i].copy();
        for (ItemStack payment : payout) {
            int left = payment.getCount();
            for (int i = 0; i < PLAYER_SLOTS && left > 0; i++) {
                ItemStack slot = slots[i];
                if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, payment)) {
                    int added = Math.min(left, slot.getMaxStackSize() - slot.getCount());
                    if (added > 0) {
                        slot.grow(added);
                        left -= added;
                    }
                }
            }
            for (int i = 0; i < PLAYER_SLOTS && left > 0; i++) {
                if (slots[i].isEmpty()) {
                    int added = Math.min(left, payment.getMaxStackSize());
                    slots[i] = payment.copyWithCount(added);
                    left -= added;
                }
            }
            if (left > 0) return null;
        }
        return slots;
    }
}
