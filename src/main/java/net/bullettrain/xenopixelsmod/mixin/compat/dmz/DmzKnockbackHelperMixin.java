package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.server.events.players.combat.KnockbackHelper;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProtection;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KnockbackHelper.class, remap = false)
public abstract class DmzKnockbackHelperMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true, require = 1)
    private static void xenopixels$respectNpcKnockable(LivingEntity entity, Vec3 velocity,
                                                        CallbackInfo ci) {
        if (!NpcCombatProtection.isKnockable(entity)) ci.cancel();
    }
}
