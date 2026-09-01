package net.bullettrain.xenopixelsmod.mixin.common;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla caps max health at 1024 and attack damage at 2048. CustomNPCs writes its integer
 * settings directly into those attributes, so high DMZ NPC stats were silently clamped.
 * Only these two attributes are widened; movement, armor, range, and every other bound retain
 * their vanilla validation.
 */
@Mixin(RangedAttribute.class)
public abstract class VanillaCombatAttributeCapMixin {
    @Shadow @Final @Mutable private double maxValue;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void xenopixels$liftNpcCombatCaps(String descriptionId, double defaultValue,
                                              double minValue, double originalMax,
                                              CallbackInfo ci) {
        if ("attribute.name.generic.max_health".equals(descriptionId)
                || "attribute.name.generic.attack_damage".equals(descriptionId)) {
            this.maxValue = Double.MAX_VALUE;
        }
    }
}
