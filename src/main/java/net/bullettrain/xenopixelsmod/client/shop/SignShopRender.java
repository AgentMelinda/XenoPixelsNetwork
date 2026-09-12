package net.bullettrain.xenopixelsmod.client.shop;

import net.bullettrain.xenopixelsmod.shop.SignShopData;
import net.bullettrain.xenopixelsmod.shop.SignShopSyntax;
import net.bullettrain.xenopixelsmod.shop.SignShopTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

/**
 * Builds the three rendered rows of a sign shop listing.
 *
 * <p>Rendering reuses vanilla's own line splitter, so wrapping and the sign's width limit behave
 * exactly as they do for ordinary sign text. Only the styles differ.</p>
 *
 * <p>The rainbow is derived from the listing text, so it is deterministic and stable: the same
 * listing always produces the same colours and the sign never flickers between frames. Hue steps
 * per character, and the starting hue comes from the target id so two different listings never
 * share a palette.</p>
 */
public final class SignShopRender {

    /** Row 0 is the activation marker and renders blank. */
    private static final int ROW_ITEM = 1;
    private static final int ROW_QUANTITY = 2;
    private static final int ROW_PRICE = 3;

    private static final float SATURATION = 0.85F;
    private static final float BRIGHTNESS = 1.0F;
    private static final float HUE_STEP = 24.0F;

    /** Gold at the start of the price gradient. */
    private static final int PRICE_START = 0xFFAA00;
    /** Amber at the end of the price gradient. */
    private static final int PRICE_END = 0xFFD24A;

    private SignShopRender() {
    }

    /**
     * @return a four-entry array matching vanilla's layout, or {@code null} when the listing
     *         cannot be rendered and vanilla should draw instead.
     */
    public static FormattedCharSequence[] rows(SignShopData data,
                                               Function<Component, FormattedCharSequence> splitter) {
        if (data == null || splitter == null) {
            return null;
        }
        Item item = SignShopTarget.resolve(data.targetId());
        if (item == null) {
            return null;
        }
        String name = new ItemStack(item).getHoverName().getString();
        if (name.isEmpty()) {
            name = data.targetId().toString();
        }
        FormattedCharSequence[] rows = new FormattedCharSequence[SignShopSyntax.LINE_COUNT];
        rows[0] = FormattedCharSequence.EMPTY;
        rows[ROW_ITEM] = splitter.apply(rainbow(name, hueSeed(data)));
        rows[ROW_QUANTITY] = splitter.apply(Component.literal(data.quantity() + "x")
                .withStyle(ChatFormatting.WHITE));
        rows[ROW_PRICE] = splitter.apply(price(SignShopSyntax.formatPrice(data.price())));
        return rows;
    }

    /**
     * Builds the four rendered rows of a plot-sale sign.
     *
     * <p>Shares this class's renderer rather than duplicating it, so the rainbow row and the
     * gold-to-amber price row are literally the same code for a shop and a plot. A plot has no
     * quantity, so the middle row carries the footprint instead.</p>
     *
     * @return a four-entry array matching vanilla's layout, or {@code null} when the listing
     *         cannot be rendered and vanilla should draw instead.
     */
    public static FormattedCharSequence[] plotRows(
            net.bullettrain.xenopixelsmod.plot.PlotSignData data,
            Function<Component, FormattedCharSequence> splitter) {
        if (data == null || splitter == null) {
            return null;
        }
        FormattedCharSequence[] rows = new FormattedCharSequence[SignShopSyntax.LINE_COUNT];
        rows[0] = FormattedCharSequence.EMPTY;
        rows[ROW_ITEM] = splitter.apply(rainbow("Plot", hueSeed(data)));
        rows[ROW_QUANTITY] = splitter.apply(Component.literal(data.width() + "x" + data.length())
                .withStyle(ChatFormatting.WHITE));
        rows[ROW_PRICE] = splitter.apply(price(SignShopSyntax.formatPrice(data.price())));
        return rows;
    }

    /** Steps hue per character from a seed derived from the listing, so colours are stable. */
    private static MutableComponent rainbow(String text, int seed) {
        MutableComponent root = Component.literal("");
        float hue = Math.floorMod(seed, 360);
        for (int i = 0; i < text.length(); i++) {
            int rgb = Mth.hsvToRgb(((hue + i * HUE_STEP) % 360.0F) / 360.0F, SATURATION, BRIGHTNESS)
                    & 0xFFFFFF;
            root.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(rgb)));
        }
        return root;
    }

    /** Gold to amber across the digits, bold, so the price reads as the emphasis line. */
    private static MutableComponent price(String text) {
        MutableComponent root = Component.literal("");
        int last = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            int rgb = lerpColor(PRICE_START, PRICE_END, (float) i / last);
            root.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(rgb).withBold(true)));
        }
        return root;
    }

    private static int hueSeed(SignShopData data) {
        return Math.floorMod(data.targetId().hashCode() * 31 + Double.hashCode(data.price()), 360);
    }

    private static int hueSeed(net.bullettrain.xenopixelsmod.plot.PlotSignData data) {
        int bounds = data.minX() * 31 + data.minZ();
        return Math.floorMod(bounds * 31 + Double.hashCode(data.price()), 360);
    }

    private static int lerpColor(int from, int to, float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        int r = (int) Mth.lerp(clamped, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(clamped, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(clamped, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}