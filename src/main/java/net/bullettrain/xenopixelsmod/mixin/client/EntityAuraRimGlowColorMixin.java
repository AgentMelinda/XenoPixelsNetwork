package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.aura.AuraRimGlow;
import net.bullettrain.xenopixelsmod.client.combat.HakaiFade;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Aura-off rim glow colour: the player's aura colour (a Hakai target keeps the Hakai colour). */
@Mixin(Entity.class)
public abstract class EntityAuraRimGlowColorMixin {
    @Inject(method = "getTeamColor()I", at = @At("RETURN"), cancellable = true)
    private void xeno$auraRimColour(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (HakaiFade.targeted(self)) return;
        Integer rgb = AuraRimGlow.colour(self.getId());
        if (rgb != null) cir.setReturnValue(rgb);
    }
}
