package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import net.bullettrain.xenopixelsmod.compat.sable.SableBlastCenter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@code KiBlastEntity#onHitBlock} and {@code KiBlastEntity#explodeAndDie}
 * Reason: on a Sable ship a clip result is in plot-local coordinates while the entity is in world
 *         coordinates, so DMZ's entity-centred destruction edits blocks in the wrong place. DMZ
 *         exposes no hook for "where should this blast edit blocks", and Sable is not a DMZ
 *         dependency, so the correction belongs here rather than in DMZ.
 * Version: NeoForge 1.21.1, DragonMineZ 2.1.3, Sable 2.0.x
 * Side: common; the destruction branch it feeds is server-only.
 * Notes: no-ops entirely without Sable — {@link SableBlastCenter} returns null when the blast is not
 *        on a sub-level, so ordinary hits keep DMZ's own behaviour untouched. Runs once per
 *        projectile impact, never per tick or per block, and allocates nothing on the common path.
 */
@Mixin(value = KiBlastEntity.class, remap = false)
public abstract class KiBlastSableCenterMixin {

	/**
	 * Impact point in ship-local space, or null for an ordinary hit.
	 *
	 * <p>A {@code BlockPos} is immutable and tiny, and this is per-entity rather than static, so it
	 * holds no world reference beyond the projectile's own lifetime.
	 */
	@Unique
	private BlockPos xenopixels$shipHitCenter;

	@Inject(method = "onHitBlock", at = @At("HEAD"), require = 1)
	private void xenopixels$captureShipHit(BlockHitResult result, CallbackInfo ci) {
		this.xenopixels$shipHitCenter = SableBlastCenter.resolve((KiBlastEntity) (Object) this, result);
	}

	/**
	 * Swap the destruction centre for the ship-local one, but only when there is one.
	 *
	 * <p>{@code @ModifyVariable} rather than capturing the local: it is the least invasive injector
	 * that can do this, and it cannot disturb anything else in the method.
	 */
	@ModifyVariable(method = "explodeAndDie", at = @At("STORE"), require = 1)
	private BlockPos xenopixels$shipDestructionCenter(BlockPos center) {
		BlockPos shipCenter = this.xenopixels$shipHitCenter;
		return shipCenter != null ? shipCenter : center;
	}
}
