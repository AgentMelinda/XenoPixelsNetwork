package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stands DragonMineZ's melee down while Xeno owns the click, instead of cancelling the click.
 *
 * <p>{@code canAttack} is consulted in exactly two places in DMZ, both in its client
 * {@code MinecraftMixin}: the {@code startAttack} head hook and the {@code continueAttack} one.
 * Returning false there makes both fall through without cancelling, so Minecraft's own attack
 * lifecycle runs untouched — block breaking stays completely native, including with a tool in
 * hand, which is not something Xeno can reproduce because a held item takes it out of the click.
 *
 * <p>The alternative was cancelling {@code startAttack} outright to keep DMZ's melee state machine
 * from being entered, but that takes the dig with it: DMZ only yields a block target to mining when
 * nothing stands in front of the block, so a mob against a wall would otherwise land a DMZ punch
 * and an Xeno fist from one press.
 *
 * <p>Client only, registered in the {@code client} section of {@code xenopixelsmod.mixins.json},
 * so this never touches server-side melee for anyone else. It also checks the player really is
 * this client's own, since the helper itself is a common class.
 */
@Mixin(value = PlayerAttackHelper.class, remap = false)
public abstract class PlayerAttackHelperGateMixin {
    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private static void xenopixels$yieldMeleeToXeno(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player == null || !player.level().isClientSide()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player != player) return;
        if (Bt3CombatClient.suppressesNativeAttack(mc)) {
            cir.setReturnValue(false);
        }
    }
}
