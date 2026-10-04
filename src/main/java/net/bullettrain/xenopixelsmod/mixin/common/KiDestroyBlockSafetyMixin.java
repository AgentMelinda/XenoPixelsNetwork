package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Target: {@code AbstractKiProjectile#destroyKiBlock}
 * Reason: a waterlogged/stale chest BE on water throws {@code IllegalStateException}
 *         inside {@code Level.destroyBlock} and can hitch {@code runClient} when an NPC
 *         wave griefs a Sable plot.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x
 * Side: common. Destruction is server-only.
 */
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class KiDestroyBlockSafetyMixin {

    @WrapOperation(
            method = "destroyKiBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z",
                    remap = true))
    private boolean xenopixels$safeDestroy(Level level, BlockPos pos, boolean drop,
                                           Operation<Boolean> original) {
        if (level == null || pos == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null && !be.getType().isValid(state)) {
            return false;
        }
        try {
            return original.call(level, pos, drop);
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}
