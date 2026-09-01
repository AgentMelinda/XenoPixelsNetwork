package net.bullettrain.xenopixelsmod.mixin.compat.cnpcgecko;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Addon {@code register} builds {@code minecraft:} Type ids, which NeoForge rejects.
 * {@link net.bullettrain.xenopixelsmod.compat.npc.CnpcGeckoPayloads} registers the
 * namespaced replacements instead.
 */
@Mixin(targets = "com.goodbird.cnpcgeckoaddon.network.NetworkWrapper", remap = false)
public abstract class NetworkWrapperRegisterMixin {
    @Inject(method = "register", at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$skipBrokenRegister(RegisterPayloadHandlersEvent event, CallbackInfo ci) {
        ci.cancel();
    }
}
