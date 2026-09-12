package net.bullettrain.xenopixelsmod.shop;

import net.bullettrain.xenopixelsmod.compat.mmoecon.MmoEconBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Server-side purchase path for a sign shop.
 *
 * <p>Money moves only through {@link MmoEconBridge}, which is reflective and mod-id gated. When
 * MMO Econ is absent the purchase reports {@link Result#NO_ECONOMY} instead of inventing a balance
 * source, and the sign remains an inert listing.</p>
 *
 * <p>Funds are checked and debited before the item is created, and the debit is verified before the
 * item is handed over, so a failed withdrawal never produces free goods.</p>
 */
public final class SignShopPurchase {

    private SignShopPurchase() {
    }

    /** Outcome of a purchase attempt, for reporting back to the buyer. */
    public enum Result {
        /** The item was delivered and the balance debited. */
        SUCCESS,
        /** MMO Econ is not installed, so no balance exists to charge. */
        NO_ECONOMY,
        /** The buyer's balance is below the listing price. */
        INSUFFICIENT_FUNDS,
        /** No shop is registered at that position. */
        NO_SHOP,
        /** The sign's target id no longer resolves to an item. */
        NO_TARGET
    }

    /**
     * Attempts to buy the shop at {@code pos}.
     *
     * @return the outcome; only {@link Result#SUCCESS} mutates balance or inventory.
     */
    public static Result buy(ServerPlayer buyer, BlockPos pos) {
        if (buyer == null || pos == null || !(buyer.level() instanceof ServerLevel level)) {
            return Result.NO_SHOP;
        }
        ResourceLocation dimension = level.dimension().location();
        SignShopManager.Entry entry = SignShopManager.get(level.getServer()).at(dimension, pos);
        if (entry == null) {
            return Result.NO_SHOP;
        }
        if (!MmoEconBridge.available()) {
            return Result.NO_ECONOMY;
        }
        Item item = SignShopTarget.resolve(entry.data().targetId());
        if (item == null) {
            return Result.NO_TARGET;
        }
        long price = MmoEconBridge.toUnits(entry.data().price());
        if (!MmoEconBridge.hasFunds(buyer.getUUID(), price)) {
            return Result.INSUFFICIENT_FUNDS;
        }
        if (!MmoEconBridge.withdraw(buyer.getUUID(), price)) {
            return Result.INSUFFICIENT_FUNDS;
        }
        give(buyer, item, entry.data().quantity());
        return Result.SUCCESS;
    }

    /** Human-readable failure text, or {@code null} for {@link Result#SUCCESS}. */
    @Nullable
    public static Component describe(Result result) {
        return switch (result) {
            case SUCCESS -> null;
            case NO_ECONOMY -> Component.literal("This shop needs MMO Econ, which is not installed.");
            case INSUFFICIENT_FUNDS -> Component.literal("You cannot afford this.");
            case NO_SHOP -> Component.literal("That sign is not a shop.");
            case NO_TARGET -> Component.literal("That shop's item no longer exists.");
        };
    }

    private static void give(ServerPlayer buyer, Item item, int quantity) {
        int remaining = quantity;
        int maxStackSize = Math.max(1, new ItemStack(item).getMaxStackSize());
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStackSize);
            ItemStack stack = new ItemStack(item, stackSize);
            if (!buyer.getInventory().add(stack)) {
                buyer.drop(stack, false);
            }
            remaining -= stackSize;
        }
    }
}