package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sable 2.0.3 shouldLoad uses centre distance and omits Earth at a normal altitude handoff.
 * Its addition, removal and re-addition paths all pass the actual body's logicalPose.position vector.
 * Preserve native true decisions; only this saved Earth (and planet-form dragon ball bench bodies) may extend an
 * otherwise false decision.
 * Optional-Sable class loading is gated by the existing ConditionalMixinPlugin compat.sable rule.
 */
@Mixin(targets="dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem",remap=false)
public abstract class SableEarthTrackingMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method="shouldLoad(Lnet/minecraft/world/entity/player/Player;Lorg/joml/Vector3dc;)Z",
            at=@At("RETURN"),cancellable=true,require=1)
    private void xenopixels$trackManagedEarth(Player player,Vector3dc centre,CallbackInfoReturnable<Boolean> result) {
        if(!result.getReturnValueZ() && (PhysicalEarth.extendsNativeTracking(level,player,centre)
                || net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets.extendsNativeTracking(level,player,centre)))
            result.setReturnValue(true);
    }
}
