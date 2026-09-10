package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.util.TextUtil;
import net.bullettrain.xenopixelsmod.client.hud.DmzMenuThemeState;
import net.bullettrain.xenopixelsmod.client.screen.StatText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Gives the themed DragonMineZ stats panel its gold multiplier column.
 *
 * <p>DMZ has no such column and no texture can create one, because the multiplier is not drawn
 * separately: {@code renderStatsInfo} folds it into the value string and drops it entirely when it
 * is 1.0 —
 *
 * <pre>
 * statText = hasMult ? formatted + " x" + String.format("%.1f", totalMult) : formatted;
 * </pre>
 *
 * <p>So the text has to be taken apart. This redirects that one draw and issues two in its place:
 * the number where DMZ put it, and the multiplier right-aligned in its own column, gold, on every
 * row including the unboosted ones.
 *
 * <p><b>Only the value draw is touched.</b> The call is pinned with a {@link Slice} that opens at
 * the {@code character_stats.stats} header constant, after which exactly two calls to this overload
 * remain — the stat label, then the stat value — so {@code ordinal = 1} is the value. Pinning it by
 * method-wide ordinal instead would break the moment DragonMineZ edited anything above the stat
 * block. {@code require = 0} means a DMZ update that moves it silently falls back to stock
 * rendering rather than refusing to load the mod.
 */
@Mixin(targets = "com.dragonminez.client.gui.character.CharacterStatsScreen", remap = false)
public abstract class DmzStatsMultiplierMixin {

    /** Right edge of the yellow column, in DMZ's menu space. Its panel is 141 wide. */
    private static final int MULTIPLIER_COLUMN_RIGHT = 136;
    /** Clear space kept between a stat value and its multiplier. */
    private static final int COLUMN_GAP = 3;
    // Both from StatText, which sampled them out of the bundle art, so the themed DMZ panel and our
    // own stats screen show the same yellow.
    private static final int GOLD = StatText.MULTIPLIER;
    private static final int GOLD_DIM = StatText.MULTIPLIER_NEUTRAL;

    @Redirect(
            method = "renderStatsInfo",
            slice = @Slice(from = @At(value = "CONSTANT",
                    args = "stringValue=gui.dragonminez.character_stats.stats")),
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lcom/dragonminez/client/util/TextUtil;drawStringWithBorder("
                            + "Lnet/minecraft/client/gui/GuiGraphics;"
                            + "Lnet/minecraft/client/gui/Font;"
                            + "Lnet/minecraft/network/chat/Component;IIII)V"),
            require = 0)
    private void xenopixels$splitStatValue(GuiGraphics graphics, Font font, Component text,
                                           int x, int y, int colour, int borderColour) {
        if (!DmzMenuThemeState.isThemed()) {
            // /xenohud menus stock: DragonMineZ draws its own screen exactly as it always did.
            TextUtil.drawStringWithBorder(graphics, font, text, x, y, colour, borderColour);
            return;
        }

        StatText.Split split = StatText.split(text.getString());
        int multX = MULTIPLIER_COLUMN_RIGHT - font.width(split.mult());

        // Seven-figure stats are wide enough to run into the multiplier -- "10,004,999" and "x1"
        // were touching. The exact number is kept whenever it fits, because that is what DMZ shows
        // and what players read; only a value that would actually collide is abbreviated, which
        // costs precision on exactly the rows too large to read digit by digit anyway.
        String number = split.number();
        if (x + font.width(number) > multX - COLUMN_GAP) {
            String condensed = StatText.condense(number);
            if (font.width(condensed) < font.width(number)) {
                number = condensed;
            }
        }

        // The number keeps DMZ's own x and colour, so a boosted row stays the colour DMZ chose for
        // it; only the multiplier moves out of the string.
        TextUtil.drawStringWithBorder(graphics, font, Component.literal(number),
                x, y, colour, borderColour);
        TextUtil.drawStringWithBorder(graphics, font, Component.literal(split.mult()),
                multX, y, split.neutral() ? GOLD_DIM : GOLD, borderColour);
    }
}
