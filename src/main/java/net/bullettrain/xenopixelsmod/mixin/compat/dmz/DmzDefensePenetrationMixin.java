package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.server.events.players.combat.CombatEvent;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.bullettrain.xenopixelsmod.features.transformation.passive.FormPassive;
import net.bullettrain.xenopixelsmod.features.transformation.passive.FormPassiveRules;
import net.bullettrain.xenopixelsmod.features.transformation.passive.FormPassives;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Target: {@code CombatEvent#computeDefensePenetration(LivingEntity attacker, DamageSource, double)}
 * Reason: Ultra Ego's "bonus defense penetration" on ki and melee (2026-09-29). DMZ's own figure
 *         (the {@code defense_penetration} skill, the weapon enchant, or the ki blast's pen; read from
 *         the bytecode) is kept and the form's bonus is added on top, so both kinds of attack pierce
 *         at DMZ's own step instead of a second damage pass.
 * Version: DMZ 2.1.3. Side: server.
 */
@Mixin(value = CombatEvent.class, remap = false)
public abstract class DmzDefensePenetrationMixin {

    @ModifyReturnValue(method = "computeDefensePenetration", at = @At("RETURN"), require = 1)
    private static double xenopixels$formPenetration(double original, LivingEntity attacker,
                                                      DamageSource source, double base) {
        if (attacker == null || !FormPassiveRules.enabled(FormPassiveRules.Kind.PENETRATION)) {
            return original;
        }
        FormPassive passive = FormPassives.of(attacker);
        return passive.defPenBonus() > 0.0f
                ? FormPassiveRules.withPenetration(original, passive.defPenBonus()) : original;
    }
}
