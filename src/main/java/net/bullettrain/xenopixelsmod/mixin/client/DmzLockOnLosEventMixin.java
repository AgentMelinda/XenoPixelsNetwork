package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.events.LockOnEvent;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Isolated from {@link DmzLockOnNpcMixin} so a missed LOS remap cannot take down NPC pick/range.
 * Persist ({@code lambda$onClientTick$3}) always keeps lock through walls.
 * Acquire skips LOS when either client or server through-blocks is on.
 */
@Mixin(value = LockOnEvent.class, remap = false)
public abstract class DmzLockOnLosEventMixin {

    @Redirect(
            method = "lambda$onClientTick$3",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z",
                    remap = true
            ),
            require = 0
    )
    private static boolean xenopixels$persistThroughWallsPlayer(Player player, Entity target) {
        return true;
    }

    @Redirect(
            method = "lambda$onClientTick$3",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z",
                    remap = true
            ),
            require = 0
    )
    private static boolean xenopixels$persistThroughWallsLiving(LivingEntity viewer, Entity target) {
        return true;
    }

    @Redirect(
            method = "findTargetInFront",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z",
                    remap = true
            ),
            require = 0
    )
    private static boolean xenopixels$acquireThroughWallsPlayer(Player player, Entity target) {
        if (acquireThrough()) {
            return true;
        }
        return player.hasLineOfSight(target);
    }

    @Redirect(
            method = "findTargetInFront",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z",
                    remap = true
            ),
            require = 0
    )
    private static boolean xenopixels$acquireThroughWallsLiving(LivingEntity viewer, Entity target) {
        if (acquireThrough()) {
            return true;
        }
        return viewer.hasLineOfSight(target);
    }

    private static boolean acquireThrough() {
        return XenoClientConfig.lockOnThroughBlocks || XenoServerClientState.lockOnThroughBlocks();
    }
}
