package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.util.KeyBinds;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.client.pad.XenoPadInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        Minecraft minecraft = (Minecraft) (Object) this;
        if (padDescendOwnsAttack(minecraft)
                || Bt3CombatClient.suppressesNativeMelee(minecraft)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void xenopixels$withholdDescendAttack(boolean leftClick, CallbackInfo ci) {
        if (padDescendOwnsAttack((Minecraft) (Object) this)) ci.cancel();
    }

    private static boolean padDescendOwnsAttack(Minecraft minecraft) {
        if (!XenoPadInput.suppressesAttack()) return false;
        try {
            return !KeyBinds.isPhysicallyDown(minecraft.options.keyAttack);
        } catch (Throwable ignored) {
            return true;
        }
    }
}
