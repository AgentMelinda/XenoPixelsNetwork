package net.bullettrain.xenopixelsmod.mixin.compat.shared;

import net.bullettrain.xenopixelsmod.compat.npc.NpcMeleeDamage;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the vanilla arm swing from fighting an NPC attack animation we are about to play.
 *
 * <p>Both NPC mods' melee goal does the same thing: {@code swing(hand)} and then
 * {@code doHurtTarget(target)}. We hook the second of those to start the configured attack clip, so
 * the swing and the clip end up on the same model in the same tick — a DragonMineZ punch played
 * under a humanoid arm swing, which is what made attacking NPCs look like their limbs were snapping.
 *
 * <p>Only suppressed when a clip really will play; an NPC we do not animate keeps its vanilla swing,
 * which is the NPC mods' own melee animation and the right thing for it to have.
 *
 * <p>This sits on {@code LivingEntity}, so it is asked about every swing in the game — players,
 * mobs, everything. {@link NpcMeleeDamage#playsOwnAttackAnimation} is ordered to fail on a class
 * check first for exactly that reason.
 */
@Mixin(LivingEntity.class)
public abstract class NpcSwingSuppressMixin {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void xenopixels$suppressSwingUnderOwnClip(InteractionHand hand, CallbackInfo ci) {
        if (NpcMeleeDamage.playsOwnAttackAnimation((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
