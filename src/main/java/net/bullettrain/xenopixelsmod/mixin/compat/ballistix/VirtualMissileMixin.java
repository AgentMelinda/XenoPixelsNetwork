package net.bullettrain.xenopixelsmod.mixin.compat.ballistix;

import ballistix.api.missile.virtual.VirtualMissile;
import net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat;
import net.bullettrain.xenopixelsmod.compat.ballistix.MissileChunkLoadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Long-range Ballistix virtual missiles: VS2 wake, shipyard→world fix, chunkloading corridor.
 */
@Mixin(value = VirtualMissile.class, remap = false)
public abstract class VirtualMissileMixin {
    @Shadow
    public Vec3 position;

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void xenopixels$virtualMissileTick(ServerLevel level, CallbackInfo ci) {
        if (position == null || level == null) return;
        Vec3 world = BallistixVs2Compat.shipyardToWorld(level, position);
        if (world != null && world.distanceToSqr(position) > 1.0) {
            position = world;
        }
        BallistixVs2Compat.onVirtualMissileTick(level, position);
        BlockPos target = null;
        try {
            Object self = this;
            var f = self.getClass().getDeclaredField("targetData");
            f.setAccessible(true);
            Object td = f.get(self);
            if (td != null) {
                for (String name : new String[]{"target", "targetPos", "blockPos"}) {
                    try {
                        var tf = td.getClass().getDeclaredField(name);
                        tf.setAccessible(true);
                        Object v = tf.get(td);
                        if (v instanceof BlockPos bp) {
                            target = bp;
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        MissileChunkLoadManager.trackMissile(level, position, target);
    }
}
