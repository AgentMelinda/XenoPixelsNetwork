package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Minecraft's own melee from doubling up with an Xeno fist.
 *
 * <p>Narrow on purpose. DragonMineZ's punch and kick sequence is stood down separately by
 * {@link PlayerAttackHelperGateMixin}, so all that is left here is {@code gameMode.attack} on an
 * entity. A block target is deliberately never cancelled: the vanilla dig runs untouched, which is
 * what makes mining behave normally with a tool in hand as well as with fists.
 *
 * <p>The higher priority still puts this ahead of DragonMineZ's own {@code startAttack} head hook,
 * which starts melee before NeoForge's click event fires.
 */
@Mixin(value = Minecraft.class, priority = 1100)
public abstract class MinecraftFistOwnershipMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void xenopixels$fistStart(CallbackInfoReturnable<Boolean> cir) {
        if (Bt3CombatClient.suppressesNativeMelee((Minecraft) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
