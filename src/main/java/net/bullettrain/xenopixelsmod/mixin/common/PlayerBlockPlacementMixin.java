package net.bullettrain.xenopixelsmod.mixin.common;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes bug where guard/blocking mechanics incorrectly prevent legitimate block placement.
 * 
 * <p>This mixin intercepts the block placement check and ensures that:
 * <ul>
 *   <li>Players can always place blocks when not actively attacking</li>
 *   <li>Guard mechanics only block attack actions, not building</li>
 * </ul>
 */
@Mixin(Player.class)
public class PlayerBlockPlacementMixin {

    @Unique
    private boolean xenopixelsmod$justPlacedBlock = false;

    /**
     * Allow block placement even when player has items that might trigger guard logic.
     * This fixes the bug where placing blocks is blocked by guard systems.
     */
    @Inject(method = "mayBuild", at = @At("HEAD"), cancellable = true)
    private void xenopixelsmod$allowBlockPlacement(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        
        // Always allow creative players to build
        if (self.isCreative()) {
            cir.setReturnValue(true);
            return;
        }
        
        // If not actively using item (blocking), allow building
        if (!self.isUsingItem()) {
            cir.setReturnValue(true);
            return;
        }
        
        // Allow building if the blocking animation is about to complete
        if (self.getUseItemRemainingTicks() < 5) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Track when player swings arm to reset guard state.
     */
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("TAIL"))
    private void xenopixelsmod$onSwing(InteractionHand hand, CallbackInfoReturnable<Void> ci) {
        // Reset guard state after swing to prevent stuck blocking
        xenopixelsmod$justPlacedBlock = false;
    }

    /**
     * Mark when block placement occurs to prevent guard interference.
     */
    @Inject(method = "placeBlockAsPlayer", at = @At("HEAD"))
    private void xenopixelsmod$markBlockPlacement(CallbackInfoReturnable<Boolean> ci) {
        xenopixelsmod$justPlacedBlock = true;
    }
}
