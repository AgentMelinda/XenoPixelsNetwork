package net.bullettrain.xenopixelsmod.combat.beam;

import com.dragonminez.common.compat.SableCompat;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import net.bullettrain.xenopixelsmod.compat.sable.SableKiClip;
import net.bullettrain.xenopixelsmod.mixin.common.KiLaserAccess;
import net.bullettrain.xenopixelsmod.mixin.common.KiProjectileGriefAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared tip-sphere block eat for surged beams. Waves already do this in DMZ;
 * lasers detonated on first contact instead, so a BEAM/LASER of the same size
 * barely broke blocks.
 */
public final class KiBeamBore {

    private static final float WAVE_RADIUS_SCALE = 3.2F;
    private static final float LASER_MAX_RANGE = 250.0F;

    private KiBeamBore() {
    }

    /**
     * Eat a sphere at {@code center} using the projectile's own grief rules.
     * Radius is {@code scaledDestructionRadius(size * 3.2)}, same as {@code KiWaveEntity}.
     */
    public static void eat(AbstractKiProjectile beam, BlockPos center) {
        if (beam == null || center == null) return;
        if (!(beam.level() instanceof ServerLevel level)) return;
        KiProjectileGriefAccess access = (KiProjectileGriefAccess) beam;
        float eatRadius = access.xenopixels$scaledDestructionRadius(beam.getSize() * WAVE_RADIUS_SCALE);
        int bRad = Math.round(eatRadius);
        if (bRad < 0) return;

        for (int x = -bRad; x <= bRad; x++) {
            for (int y = -bRad; y <= bRad; y++) {
                for (int z = -bRad; z <= bRad; z++) {
                    if (x * x + y * y + z * z > eatRadius * eatRadius) continue;
                    BlockPos target = center.offset(x, y, z);
                    if (level.getBlockState(target).isAir()) continue;
                    if (level.getBlockState(target).getExplosionResistance(level, target, null) >= 1000) continue;
                    if (!access.xenopixels$destroyKiBlock(target, false)) continue;
                    if (level.random.nextFloat() < 0.25F) {
                        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                                1, 0.5D, 0.5D, 0.5D, 0.05D);
                    }
                }
            }
        }
    }

    /**
     * Replace a laser's wall detonation: bore the tip and keep the beam alive.
     *
     * @param worldHit the already-projected hit the laser was about to explode at
     */
    public static void boreLaser(KiLaserEntity laser, Vec3 worldHit) {
        if (laser == null || worldHit == null) return;
        Vec3 start = laser.position();
        Vec3 dir = Vec3.directionFromRotation(laser.getFixedPitch(), laser.getFixedYaw());
        BlockPos center = laserCenter(laser, start, dir, worldHit);
        eat(laser, center);

        float len = (float) worldHit.distanceTo(start);
        KiLaserAccess access = (KiLaserAccess) laser;
        access.xenopixels$setBeamLength(Math.max(laser.getBeamLength(), len));
        access.xenopixels$damageEntitiesInBeam(start, dir, len);
    }

    /**
     * Wave {@code destroyBlocksAt} centre: plot-local stays plot-local when the entity is
     * already on a Sable sub-level; a world-side clip against a ship is projected out.
     */
    public static BlockPos waveCenter(AbstractKiProjectile wave, BlockPos center) {
        // Clip results against a ship are already plot-local; those chunks are the hull.
        return center;
    }

    private static BlockPos laserCenter(KiLaserEntity laser, Vec3 start, Vec3 dir, Vec3 worldHit) {
        Level level = laser.level();
        HitResult hit = SableKiClip.clip(level, start, start.add(dir.scale(LASER_MAX_RANGE)), laser);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos ship = SableKiClip.destructionCenter(level, blockHit);
            if (ship != null) return ship;
            if (SableCompat.isEntityInSubLevel(laser)) {
                return blockHit.getBlockPos();
            }
            Vec3 projected = SableCompat.projectToWorld(level, blockHit.getLocation());
            return BlockPos.containing(projected);
        }
        return BlockPos.containing(worldHit);
    }
}
