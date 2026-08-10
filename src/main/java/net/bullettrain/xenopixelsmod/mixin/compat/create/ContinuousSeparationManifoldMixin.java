package net.bullettrain.xenopixelsmod.mixin.compat.create;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Half one of the fix for the Create contraption collision crash
 * ("Cannot read field \"x\" because \"mf.axis\" is null", thrown from
 * {@code ContinuousOBBCollider.collideMany}).
 *
 * <p>Create's separation manifold only records {@code axis}/{@code normalAxis} when the
 * projected centre distance along the tested axis is non-zero. When an entity's OBB ends up
 * exactly concentric with a collider on every tested axis — which happens on bearing-driven
 * contraptions, and much more easily once other mods move entities around during the
 * collision pass — all six {@code separate} calls report a collision but leave both fields
 * null, and {@code collideMany} then dereferences them.
 *
 * <p>This mirrors upstream Create PR #10301: record the axis for the zero-distance case too,
 * using the raw penetration depth ({@code rA + rB}) as the separation, so the collision
 * response stays well defined instead of crashing the server.
 */
@Mixin(targets = "com.simibubi.create.foundation.collision.ContinuousOBBCollider$ContinuousSeparationManifold", remap = false)
public abstract class ContinuousSeparationManifoldMixin {

    @Shadow(remap = false)
    Vec3 normalAxis;
    @Shadow(remap = false)
    double normalSeparation;
    @Shadow(remap = false)
    Vec3 axis;
    @Shadow(remap = false)
    double separation;
    @Shadow(remap = false)
    double collisionX;
    @Shadow(remap = false)
    double collisionY;
    @Shadow(remap = false)
    double collisionZ;

    /**
     * {@code separate} returns {@code true} when the boxes are separated on the tested axis;
     * {@code TAIL} is only reached on the colliding ({@code return false}) path.
     */
    @Inject(method = "separate(Lnet/minecraft/world/phys/Vec3;DDDDZ)Z", at = @At("TAIL"), remap = false, require = 0)
    private void xeno$recordZeroDistanceAxis(Vec3 testedAxis, double tl, double rA, double rB,
                                             double projectedMotion, boolean axisOfObjA,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || testedAxis == null) {
            return;
        }
        if (Math.abs(tl) != 0.0D) {
            // Create already recorded this axis itself.
            return;
        }

        double penetration = rA + rB;
        if (!(penetration > 0.0D) || !Double.isFinite(penetration)) {
            return;
        }

        if (axisOfObjA && this.normalAxis == null && penetration <= Math.abs(this.normalSeparation)) {
            this.normalAxis = testedAxis;
            this.normalSeparation = penetration;
        }

        if (this.axis == null && penetration <= Math.abs(this.separation)) {
            this.axis = testedAxis;
            this.separation = penetration;
            // signum(TL) is 0 here, so the only meaningful term is the epsilon push-out.
            double scale = -0.125D;
            this.collisionX = testedAxis.x * scale;
            this.collisionY = testedAxis.y * scale;
            this.collisionZ = testedAxis.z * scale;
        }
    }
}
