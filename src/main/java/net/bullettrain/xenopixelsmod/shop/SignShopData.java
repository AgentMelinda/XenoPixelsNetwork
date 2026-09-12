package net.bullettrain.xenopixelsmod.shop;

import net.minecraft.resources.ResourceLocation;

/**
 * A parsed, validated sign shop.
 *
 * <p>{@code targetId} is the registry id exactly as written on the sign, kept alongside the
 * resolved item so the sign can round-trip and so diagnostics can report the original text.</p>
 *
 * <p>{@code price} is the decimal as typed on the sign, not a converted minor unit. MMO Econ owns
 * the unit scale through {@code Money.fromDouble(double)}; keeping the decimal here means the
 * syntax layer never has to assume that scale.</p>
 *
 * @param targetId registry id of the item or block being sold
 * @param quantity units per purchase, 1..{@link SignShopSyntax#MAX_QUANTITY}
 * @param price    cost per purchase as written, always &gt;= 0
 */
public record SignShopData(ResourceLocation targetId, int quantity, double price) {

    public SignShopData {
        if (quantity < 1 || quantity > SignShopSyntax.MAX_QUANTITY) {
            throw new IllegalArgumentException("quantity out of range: " + quantity);
        }
        if (!(price >= 0.0) || Double.isInfinite(price)) {
            throw new IllegalArgumentException("price must be a finite value >= 0: " + price);
        }
    }
}