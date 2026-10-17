package net.bullettrain.xenopixelsmod.compat.sable;

import com.dragonminez.common.init.MainGameRules;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import dev.ryanhcode.sable.Sable;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.mixin.common.KiProjectileGriefAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Narrow opt-in bridge for DMZ Ki damage against XenoPixels' otherwise-unbreakable Sable planet shell. */
public final class KiPlanetShellDestruction {
    private KiPlanetShellDestruction() {}

    public static boolean canBreak(AbstractKiProjectile projectile, BlockPos pos) {
        if (!XenoServerConfig.kiPlanetShellDestructionEnabled || projectile == null || pos == null
                || !projectile.isBlockDestructionEnabled()) return false;
        var level = projectile.level();
        if (level == null || level.isClientSide || level.getBlockState(pos).getBlock() != ModBlocks.PLANET_SHELL.get())
            return false;
        // Physical planet voxels live in Sable's plot storage; this avoids granting the exception to a copied
        // or hand-placed shell block in an ordinary level.
        if (Sable.HELPER.getContaining(level, pos) == null) return false;
        var source = ((KiProjectileGriefAccess) projectile).xenopixels$getKiGriefingSource();
        return source != null && MainGameRules.canKiGrief(level, pos, source);
    }

    public static boolean allowResistanceBypass(AbstractKiProjectile projectile, BlockState state, BlockPos pos) {
        return state != null && state.getBlock() == ModBlocks.PLANET_SHELL.get() && canBreak(projectile, pos);
    }
}
