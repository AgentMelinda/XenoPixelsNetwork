package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.shop.SignShopRender;
import net.bullettrain.xenopixelsmod.plot.PlotSignData;
import net.bullettrain.xenopixelsmod.plot.PlotSignReader;
import net.bullettrain.xenopixelsmod.shop.SignShopData;
import net.bullettrain.xenopixelsmod.shop.SignShopReader;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

/**
 * Replaces the rendered lines of a sign shop with the rainbow listing.
 *
 * <p>The redirect targets the single {@code SignText.getRenderMessages} call inside
 * {@code SignRenderer.renderSignText}. Vanilla still performs all of its own layout — the sign
 * transform, the per-line offset, the outline, and the {@code POLYGON_OFFSET} display mode — so
 * only the sequences handed to the font differ. Replacing the whole method would mean duplicating
 * that layout and drifting from it on any update.</p>
 *
 * <p>A non-shop sign returns vanilla's own sequences unchanged, so plain signs are untouched.</p>
 */
@Mixin(SignRenderer.class)
public abstract class SignRendererShopMixin {

    @Redirect(
            method = "renderSignText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/SignText;"
                            + "getRenderMessages(ZLjava/util/function/Function;)"
                            + "[Lnet/minecraft/util/FormattedCharSequence;"))
    private FormattedCharSequence[] xenopixels$shopRows(SignText text, boolean filtered,
                                                        Function<Component, FormattedCharSequence> splitter) {
        SignShopData data = SignShopReader.fromSignText(text, filtered);
        if (data != null) {
            FormattedCharSequence[] rows = SignShopRender.rows(data, splitter);
            if (rows != null) {
                return rows;
            }
        }
        // A sign carries one marker, so a shop and a plot listing can never both match. Trying the
        // plot grammar after the shop grammar keeps the two independent rather than making one a
        // special case of the other.
        PlotSignData plot = PlotSignReader.fromSignText(text, filtered);
        if (plot != null) {
            FormattedCharSequence[] rows = SignShopRender.plotRows(plot, splitter);
            if (rows != null) {
                return rows;
            }
        }
        return text.getRenderMessages(filtered, splitter);
    }
}