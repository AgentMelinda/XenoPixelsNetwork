package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.events.FlySkillEvent;
import net.bullettrain.xenopixelsmod.client.combat.ClientChaseFlightState;
import net.bullettrain.xenopixelsmod.client.camera.UltimateFinisherCamera;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents DMZ's client flight tick from overwriting server-authored Xeno chase velocity. */
@Mixin(value = FlySkillEvent.class, remap = false)
public abstract class DmzFlyMovementDuringChaseMixin {
    @Shadow private static boolean pendingGroundActivation;
    @Shadow private static boolean pendingFlightActivation;

    /** DMZ's delayed ground jump runs before handleFlightMovement. Cancel only
     * its pending activation while Xeno owns movement; preserve the stats/ki tick. */
    @Inject(method = "onClientTick", at = @At("HEAD"), require = 1)
    private static void xenopixels$clearPendingGroundJump(
            net.neoforged.neoforge.client.event.ClientTickEvent.Post event, CallbackInfo ci) {
        if (ClientChaseFlightState.isActive()
                || net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState.recovery() != null) {
            pendingGroundActivation = false;
            pendingFlightActivation = false;
        }
    }
    @Inject(method = "handleFlightMovement", at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$pauseSearchFlightDuringChase(LocalPlayer player, int level,
                                                                boolean canSprint, CallbackInfo ci) {
        // Chase ends on arrival; the finisher still owns movement through its remaining phases.
        if (ClientChaseFlightState.isActive() || UltimateFinisherCamera.active()
                || net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState.recovery() != null) ci.cancel();
    }
}
