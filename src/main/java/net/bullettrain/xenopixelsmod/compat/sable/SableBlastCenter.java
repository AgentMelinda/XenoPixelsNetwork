package net.bullettrain.xenopixelsmod.compat.sable;

import com.dragonminez.common.compat.SableCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Where a ki blast should edit blocks when it detonates on a Sable sub-level.
 *
 * <p>Sable ships live in their own plot, so a clip result against ship geometry carries a
 * <em>plot-local</em> block position while the projectile entity reports a world position. DMZ
 * centres its destruction sphere on the entity, which is right everywhere except on a ship, where it
 * edits blocks somewhere out in the world instead of at the impact point.
 *
 * <p>This deliberately answers "nothing to change" for every ordinary hit. Centring on the hit block
 * rather than the entity is not a harmless generalisation: a blast stops in the air just short of
 * the face it struck, so an entity-centred sphere usually overlaps no solid block and breaks
 * nothing, whereas a block-centred one starts inside terrain and is guaranteed to crater. Only a
 * ship hit gets the substitution.
 *
 * <p>The logic lives here rather than in the mixin so the mixin stays a hook and nothing else, per
 * the project's mixin guidelines.
 */
public final class SableBlastCenter {

	private SableBlastCenter() {
	}

	/**
	 * The plot-local block to centre destruction on, or {@code null} to leave DMZ's choice alone.
	 *
	 * <p>Called once per projectile impact, never per tick or per block.
	 */
	public static BlockPos resolve(Entity blast, BlockHitResult hit) {
		if (blast == null || hit == null) return null;
		BlockPos ship = SableKiClip.destructionCenter(blast.level(), hit);
		if (ship != null) return ship;
		if (!SableCompat.isEntityInSubLevel(blast)) return null;
		return hit.getBlockPos().immutable();
	}
}
