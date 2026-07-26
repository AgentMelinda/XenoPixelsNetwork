package net.bullettrain.xenopixelsmod.mixin.compat.vs2;

import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.impl.game.ships.ShipData;

/**
 * Optional VS2 hook: log / gate bulk static transitions when our perf system is enabled.
 * Target is an internal impl class; if VS renames it, plugin still loads and mixin fails soft
 * (defaultRequire 0).
 *
 * <p>Primary sleep logic remains {@link net.bullettrain.xenopixelsmod.perf.Vs2ShipSleepManager}
 * via public {@code ServerShip#setStatic}.
 */
@Mixin(value = ShipData.class, remap = false)
public abstract class ServerShipStaticMixin {
    // Placeholder inject — keeps a VS2-gated mixin class so the plugin path is exercised.
    // ShipData package may differ by VS version; if apply fails, required=false skips crash.
    @Inject(method = "setStatic", at = @At("HEAD"), require = 0, remap = false)
    private void xenopixels$onSetStatic(boolean value, CallbackInfo ci) {
        if (!XenoPerfConfig.perfEnabled || !XenoPerfConfig.vs2SleepEnabled) {
            return;
        }
        // No cancel — observe only. Sleep authority is Vs2ShipSleepManager.
    }
}
