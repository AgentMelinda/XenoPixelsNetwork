package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import net.bullettrain.xenopixelsmod.compat.sable.SableBlastCenter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@code KiBlastEntity#onHitBlock}, {@code explodeAndDie},
 *         {@code destroyBlocksInPath}, and {@code processDetonation}.
 * Reason: a Sable-aware clip returns a plot-local impact while the projectile remains in world
 *         coordinates. DMZ's entity-centred destruction loops must use that impact for every
 *         impact-driven path, or later ticks erase Voidsea coordinates instead of the Sable plot.
 *         DMZ 2.2 projects visual clip endpoints through SableCompat, but its destruction helpers
 *         still receive entity-world centres; only those block-edit arguments are corrected here.
 * Version: NeoForge 1.21.1, DragonMineZ 2.2, Sable 2.0.x
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
	private BlockPos xenopixels$sableImpactCenter;

	@Inject(method = "onHitBlock", at = @At("HEAD"), require = 1)
	private void xenopixels$captureShipHit(BlockHitResult result, CallbackInfo ci) {
		this.xenopixels$sableImpactCenter = SableBlastCenter.resolve((KiBlastEntity) (Object) this, result);
		net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.onKiAttackImpact(
				(KiBlastEntity) (Object) this,
				net.bullettrain.xenopixelsmod.compat.sable.SableKiClip.worldHit(((KiBlastEntity) (Object) this).level(), result));
	}

	/**
	 * Swap DMZ's world-space destruction centre for the Sable plot hit, when the ray hit a plot.
	 *
	 * <p>DMZ 2.2 passes centres directly into the shared sphere helpers, so there is no local
	 * STORE in the path and detonation methods. Modify only argument zero at each exact call;
	 * entity position, damage, particles, and the destruction radii keep DMZ's own values.
	 */
	@ModifyArg(method = "explodeAndDie", at = @At(value = "INVOKE", target =
			"Lcom/dragonminez/common/init/entities/ki/KiBlastEntity;carveKiSphere(Lnet/minecraft/core/BlockPos;FI)V"),
			index = 0, require = 1)
	private BlockPos xenopixels$sableExplosionCenter(BlockPos center) {
		return xenopixels$useSableImpact(center);
	}

	@ModifyArg(method = "destroyBlocksInPath", at = @At(value = "INVOKE", target =
			"Lcom/dragonminez/common/init/entities/ki/KiBlastEntity;eatKiSphere(Lnet/minecraft/core/BlockPos;F)Z"),
			index = 0, require = 1)
	private BlockPos xenopixels$sablePathCenter(BlockPos center) {
		return xenopixels$useSableImpact(center);
	}

	@ModifyArg(method = "processDetonation", at = @At(value = "INVOKE", target =
			"Lcom/dragonminez/common/init/entities/ki/KiBlastEntity;carveKiSphere(Lnet/minecraft/core/BlockPos;FFI)V"),
			index = 0, require = 1)
	private BlockPos xenopixels$sableDetonationCenter(BlockPos center) {
		return xenopixels$useSableImpact(center);
	}

	@Unique
	private BlockPos xenopixels$useSableImpact(BlockPos fallback) {
		BlockPos impact = this.xenopixels$sableImpactCenter;
		return impact != null ? impact : fallback;
	}
}
