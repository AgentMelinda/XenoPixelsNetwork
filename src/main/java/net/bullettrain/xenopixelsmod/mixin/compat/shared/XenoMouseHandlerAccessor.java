package net.bullettrain.xenopixelsmod.mixin.compat.shared;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Xenopixels' own accessor onto vanilla {@link MouseHandler}'s private mouse-button/position
 * state, kept entirely separate from CustomNPCs-Unofficial's own accessor
 * ({@code noppes.npcs.mixin.MouseHelperMixin}) and My NPCs' own accessor
 * ({@code espi.mynpcs.mixin.MouseHelperMixin}) — different method names, so there is no
 * possibility of either mod's synthesized accessor colliding on the same target class.
 *
 * <p>Exists purely to back the redirect mixins in {@code mixin.compat.customnpcs} and
 * {@code mixin.compat.mynpcs}: both mods' Model Creation / script GUIs (and a few other
 * components) crash with {@code IllegalAccessError} calling their own accessor in some
 * multiplayer sessions — verified by decompiling
 * {@code CustomNPCs-Unofficial-NeoForge-1.21.1.20251230.jar} and the My NPCs 1.5.0 jar —
 * most likely because another mod's Mixin injection into {@code MouseHandler} collides with
 * each mod's own. Rather than chase that collision across a large mod pack, the redirect
 * mixins substitute this known-good accessor for the broken one at the exact call sites that
 * crash. Shared (not namespaced under either mod's compat package) because it applies whenever
 * CustomNPCs OR My NPCs is present — see {@code ConditionalMixinPlugin}'s {@code .compat.shared.}
 * gate.
 */
@Mixin(MouseHandler.class)
public interface XenoMouseHandlerAccessor {
    @Accessor("activeButton")
    int xenopixels$getActiveButton();

    @Accessor("mouseGrabbed")
    void xenopixels$setMouseGrabbed(boolean grabbed);

    @Accessor("xpos")
    void xenopixels$setXpos(double x);

    @Accessor("ypos")
    void xenopixels$setYpos(double y);
}
