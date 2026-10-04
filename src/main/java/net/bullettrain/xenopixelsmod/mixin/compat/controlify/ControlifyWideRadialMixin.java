package net.bullettrain.xenopixelsmod.mixin.compat.controlify;

import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.gui.screen.RadialMenuScreen;
import net.bullettrain.xenopixelsmod.client.pad2.PadWideRadial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Gives the in-game radial menu more than eight slots.
 *
 * <p>{@code RadialItems.createBindings} allocates {@code new RadialItem[8]} and fills it from the
 * eight ids Controlify saves. Its screen is already generic — {@code RadialMenuScreen.init} spaces
 * the ring at 2π/{@code arraylength} and grows the radius with {@code Math.max(…, 43f)} — so the
 * only thing standing between this mod's ~22 radial candidates and a usable menu is the length of
 * that one array.
 *
 * <p><b>Why the call site and not the method.</b> {@code createBindings} has two callers.
 * {@code InGameInputHandler.handleKeybinds} opens the playable radial with a <b>null</b> edit mode:
 * nothing is editable and nothing is written back, so a longer array is inert.
 * {@code ControllerConfigScreenFactory} opens the editor with a {@code BindingEditMode}, whose
 * {@code setRadialItem} does {@code radialActions.set(i, ((RadialItemRecord) item).id())} — a cast
 * to a package-private type and an index into the eight-entry list. Redirecting the method itself
 * would reach both and break the editor two ways at once. Redirecting <em>this call</em> leaves
 * Controlify's own configuration screen exactly as it was.
 *
 * <p>{@code require = 0} for the reason every Controlify redirect in this package has it: a
 * Controlify update that moves or inlines this call must leave the player with the stock eight-slot
 * radial, never with a client that will not start.
 */
@Mixin(targets = "dev.isxander.controlify.ingame.InGameInputHandler", remap = false)
public abstract class ControlifyWideRadialMixin {

    @Redirect(
            method = "handleKeybinds",
            at = @At(value = "INVOKE",
                    target = "Ldev/isxander/controlify/gui/screen/RadialItems;"
                            + "createBindings(Ldev/isxander/controlify/controller/ControllerEntity;)"
                            + "[Ldev/isxander/controlify/gui/screen/RadialMenuScreen$RadialItem;"),
            require = 0
    )
    private RadialMenuScreen.RadialItem[] xenopixels$widenRadial(ControllerEntity controller) {
        return PadWideRadial.widen(
                dev.isxander.controlify.gui.screen.RadialItems.createBindings(controller),
                controller);
    }
}
