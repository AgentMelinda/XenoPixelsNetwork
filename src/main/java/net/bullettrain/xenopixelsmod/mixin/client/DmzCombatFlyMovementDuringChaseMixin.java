package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.flight.CombatFlightHandler;
import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.client.combat.ClientChaseFlightState;
import net.bullettrain.xenopixelsmod.client.camera.UltimateFinisherCamera;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents DMZ Combat Fly from replacing Xeno chase velocity. */
@Mixin(value = CombatFlightHandler.class, remap = false)
public abstract class DmzCombatFlyMovementDuringChaseMixin {
    @Inject(method = "handle(Lnet/minecraft/client/player/LocalPlayer;Lcom/dragonminez/common/stats/StatsData;Z)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void xenopixels$pauseCombatFlightDuringChase(LocalPlayer player, StatsData data,
                                                                boolean canSprint, CallbackInfo ci) {
        if (ClientChaseFlightState.isActive() || UltimateFinisherCamera.active()
                || net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState.recovery() != null) ci.cancel();
    }
}
