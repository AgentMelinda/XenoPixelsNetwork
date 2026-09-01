package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.events.LockOnEvent;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wallhack lock: vanilla {@code hasLineOfSight} is a block clip that DMZ uses both
 * to acquire ({@code findTargetInFront}) and to drop lock ({@code lambda$onClientTick$3}).
 * When through-blocks is on, the local player's LOS checks skip blocks entirely.
 */
@Mixin(LivingEntity.class)
public abstract class DmzLockOnLosMixin {

    @Inject(method = "hasLineOfSight", at = @At("HEAD"), cancellable = true)
    private void xenopixels$lockThroughWalls(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (target == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mc.player.getId() != ((LivingEntity) (Object) this).getId()) {
            return;
        }
        LivingEntity locked = LockOnEvent.getLockedTarget();
        if (locked != null && locked.getId() == target.getId()) {
            cir.setReturnValue(true);
            return;
        }
        if (XenoClientConfig.lockOnThroughBlocks || XenoServerClientState.lockOnThroughBlocks()) {
            cir.setReturnValue(true);
        }
    }
}
