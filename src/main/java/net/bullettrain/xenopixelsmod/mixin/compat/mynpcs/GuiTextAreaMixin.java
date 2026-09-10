package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.bullettrain.xenopixelsmod.mixin.compat.shared.XenoMouseHandlerAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * See {@link XenoMouseHandlerAccessor}. {@code render} calls the broken accessor twice
 * (once for the click edge, once for the click-drag/scroll edge); a single {@code @Redirect}
 * without an {@code ordinal} applies to every matching call site within the method, so one
 * handler covers both. This is My NPCs' own {@code GuiTextArea}, reached from its script editor
 * ({@code GuiScript}) — the exact crash reported against this mod.
 *
 * <p>The second injector below fixes a bug that the first one <b>made reachable</b>. See
 * {@link #xenopixels$clampSelectionToText}.
 */
@Mixin(targets = "espi.mynpcs.shared.client.gui.components.GuiTextArea", remap = false)
public abstract class GuiTextAreaMixin {

    @Shadow
    public String text;

    /**
     * Keeps a click or drag from selecting past the end of the text.
     *
     * <p><b>The crash.</b> {@code StringIndexOutOfBoundsException: Range [2319, 2246) out of bounds
     * for length 2246}, from {@code getSelectionAfterText} on the first character typed after a
     * click in the script editor.
     *
     * <p><b>The bug is My NPCs'.</b> Its two selection accessors do not agree with each other:
     *
     * <pre>
     * getSelectionBeforeText() -> text.substring(0, Math.min(startSelection, text.length()))  // clamped
     * getSelectionAfterText()  -> text.substring(endSelection)                                // not clamped
     * </pre>
     *
     * <p>And {@code getSelectionPos} — which {@code render} stores straight into
     * {@code startSelection}/{@code endSelection} on a click-drag — falls back to
     * {@code container.text.length()}. {@code container.text} is the <i>wrapped</i> text, so it is
     * longer than {@code text} by one character per soft-wrapped line. The reported numbers say
     * exactly that: 2319 against 2246, a 73-character difference in a wrapped script.
     *
     * <p><b>Why it is ours to fix.</b> That click-drag branch in {@code render} is gated on the
     * {@code getActiveButton()} call the redirect below repairs. Before that repair the call threw
     * {@code IllegalAccessError} and the branch never completed, so this latent bug was unreachable;
     * fixing the accessor is what exposed it. Clamping the position at its source fixes every writer
     * at once — the click path and the drag path both go through here — rather than patching each
     * reader.
     *
     * <p><b>Guideline notes</b> (§18): {@code @ModifyReturnValue} on the private helper, so the
     * value is corrected once where it is produced. The body is a clamp against the field the
     * accessors actually index into. {@code require = 0} because the target belongs to another mod.
     * My NPCs is not modified.
     */
    @ModifyReturnValue(
            method = "getSelectionPos(DD)I",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private int xenopixels$clampSelectionToText(int position) {
        int length = this.text == null ? 0 : this.text.length();
        return Math.max(0, Math.min(position, length));
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At(value = "INVOKE",
                    target = "Lespi/mynpcs/mixin/MouseHelperMixin;getActiveButton()I",
                    remap = false),
            require = 1
    )
    private int xenopixels$getActiveButton(@Coerce Object mouseHandler) {
        return ((XenoMouseHandlerAccessor) mouseHandler).xenopixels$getActiveButton();
    }
}
