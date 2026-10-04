package net.bullettrain.xenopixelsmod.npc.trade;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/**
 * Turns a trader's stock into vanilla's {@code MerchantOffers}.
 *
 * <p>Vanilla's trading screen, its client sync, its "trade used up" handling and its result slot
 * all come free from implementing {@code Merchant}, so the work here is only translation. Writing a
 * bespoke shop screen would have meant reimplementing every one of those and getting the
 * server-authority right again from scratch.
 *
 * <p>An id that resolves to nothing is <b>skipped, not substituted</b>. A trader authored on a
 * server with a mod installed and opened on one without it should offer fewer trades, never a
 * different item at the same price — a silent substitution is how a shop quietly starts selling
 * the wrong thing.
 */
public final class NpcTradeOffers {

    /** Vanilla's price multiplier for a trade whose price never drifts. */
    private static final float FIXED_PRICE = 0.0f;

    /** Trading with an NPC grants no villager experience; nothing here levels up. */
    private static final int NO_XP = 0;

    private NpcTradeOffers() {
    }

    /** Every offerable trade, in author order, skipping any whose items do not resolve. */
    public static MerchantOffers build(NpcTradeList stock) {
        return build(stock, true, true);
    }

    /**
     * As above, with the trader's matching rules. Both default to true, which is how every
     * trade matched before the switches existed: vanilla's {@code ItemCost} with no component
     * predicate accepts a worn or renamed item. Switching one off makes the offer strict.
     */
    public static MerchantOffers build(NpcTradeList stock, boolean ignoreDamage, boolean ignoreNbt) {
        MerchantOffers offers = new MerchantOffers();
        if (stock == null) {
            return offers;
        }
        for (NpcTrade trade : stock.offerable()) {
            MerchantOffer offer = toOffer(trade);
            if (offer != null) {
                offers.add(ignoreDamage && ignoreNbt ? offer
                        : new StrictOffer(offer, ignoreDamage, ignoreNbt));
            }
        }
        return offers;
    }

    /**
     * An offer that also refuses damaged and/or component-carrying payment. Matching is decided
     * on the server (the result slot and {@code take}), which is where this subclass lives; the
     * client's copy is the plain offer vanilla syncs.
     */
    static final class StrictOffer extends MerchantOffer {
        private final boolean ignoreDamage;
        private final boolean ignoreNbt;

        StrictOffer(MerchantOffer base, boolean ignoreDamage, boolean ignoreNbt) {
            super(base.getItemCostA(), base.getItemCostB(), base.getResult(), base.getUses(),
                    base.getMaxUses(), base.getXp(), base.getPriceMultiplier(), base.getDemand());
            this.ignoreDamage = ignoreDamage;
            this.ignoreNbt = ignoreNbt;
        }

        @Override
        public boolean satisfiedBy(ItemStack a, ItemStack b) {
            return acceptable(a, ignoreDamage, ignoreNbt) && (b.isEmpty() || acceptable(b, ignoreDamage, ignoreNbt))
                    && super.satisfiedBy(a, b);
        }

        @Override
        public MerchantOffer copy() {
            return new StrictOffer(super.copy(), ignoreDamage, ignoreNbt);
        }
    }

    /** Whether a payment stack passes the trader's damage and component rules. */
    static boolean acceptable(ItemStack stack, boolean ignoreDamage, boolean ignoreNbt) {
        if (stack.isEmpty()) {
            return true;
        }
        if (!ignoreDamage && stack.isDamaged()) {
            return false;
        }
        if (!ignoreNbt) {
            // Anything changed from the item's defaults, other than wear when wear is allowed.
            for (var entry : stack.getComponentsPatch().entrySet()) {
                if (ignoreDamage && entry.getKey() == net.minecraft.core.component.DataComponents.DAMAGE) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    /** A short authoring diagnosis for a row that will not appear in the shop. */
    public static String problem(NpcTrade trade) {
        if (trade == null || !trade.usable()) {
            return "Set both Costs and Gives";
        }
        if (resolve(trade.costA()) == null) {
            return "Unknown Costs item: " + trade.costA();
        }
        if (trade.hasSecondCost() && resolve(trade.costB()) == null) {
            return "Unknown second cost item: " + trade.costB();
        }
        if (resolve(trade.result()) == null) {
            return "Unknown Gives item: " + trade.result();
        }
        return null;
    }

    /** One trade, or null when an item id names nothing this installation has. */
    static MerchantOffer toOffer(NpcTrade trade) {
        Item costItem = resolve(trade.costA());
        Item resultItem = resolve(trade.result());
        if (costItem == null || resultItem == null) {
            return null;
        }
        Optional<ItemCost> second = Optional.empty();
        if (trade.hasSecondCost()) {
            Item secondItem = resolve(trade.costB());
            if (secondItem == null) {
                // The trade asked for something this server does not have. Offering it without
                // that cost would hand the player a discount nobody authored.
                return null;
            }
            second = Optional.of(new ItemCost(secondItem, trade.countB()));
        }
        return new MerchantOffer(
                new ItemCost(costItem, trade.countA()),
                second,
                new ItemStack(resultItem, trade.resultCount()),
                trade.effectiveMaxUses(),
                NO_XP,
                FIXED_PRICE);
    }

    /**
     * An item id to an item, or null when it names nothing.
     *
     * <p>{@code minecraft:air} counts as nothing: the registry resolves it happily, and an
     * air-for-air trade is exactly the empty row this is meant to exclude.
     */
    private static Item resolve(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            XenoPixelsMod.LOGGER.warn("NPC trade: '{}' is not a valid item id", id);
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        return item == null || item == Items.AIR ? null : item;
    }
}
