package net.bullettrain.xenopixelsmod.mixin.compat.voltaic;

import net.bullettrain.xenopixelsmod.compat.voltaic.VoltaicCompatHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import voltaic.prefab.tile.GenericTile;

/**
 * Voltaic GenericTile (base for Ballistix silos / panels) ↔ VS2 wake on load / use.
 * Mod-owned methods → remap = false.
 */
@Mixin(value = GenericTile.class, remap = false)
public abstract class GenericTileOnLoadMixin {
    @Inject(method = "onLoad", at = @At("TAIL"), remap = false)
    private void xenopixels$onLoad(CallbackInfo ci) {
        VoltaicCompatHooks.onTileLoad((BlockEntity) (Object) this);
    }

    @Inject(method = "use", at = @At("HEAD"), remap = false)
    private void xenopixels$onUse(Player player, InteractionHand hand, BlockHitResult hit,
                                  CallbackInfoReturnable<InteractionResult> cir) {
        VoltaicCompatHooks.onTileUse((BlockEntity) (Object) this);
    }
}
