package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.minecraft.world.damagesource.DamageTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PunchEffectRulesTest {
    @Test
    void onlyDirectMeleeDamageTypesCount() {
        assertTrue(PunchEffectRules.isMeleeType(DamageTypes.PLAYER_ATTACK));
        assertTrue(PunchEffectRules.isMeleeType(DamageTypes.MOB_ATTACK));
        assertFalse(PunchEffectRules.isMeleeType(DamageTypes.ARROW));
        assertFalse(PunchEffectRules.isMeleeType(DamageTypes.EXPLOSION));
        assertFalse(PunchEffectRules.isMeleeType(DamageTypes.MAGIC));
        assertFalse(PunchEffectRules.isMeleeType(null));
    }

    @Test
    void hitWeightsPickTheirEffect() {
        assertEquals(EffectSlot.PUNCH_IMPACT, PunchEffectRules.slotFor(CombatFx.Weight.LIGHT));
        assertEquals(EffectSlot.PUNCH_HEAVY, PunchEffectRules.slotFor(CombatFx.Weight.HEAVY));
        assertEquals(EffectSlot.PUNCH_HEAVY, PunchEffectRules.slotFor(CombatFx.Weight.ULTIMATE));
        assertEquals(EffectSlot.PUNCH_GUARD, PunchEffectRules.slotFor(CombatFx.Weight.GUARD));
        assertEquals(1.6f, PunchEffectRules.scaleFor(CombatFx.Weight.ULTIMATE), 1e-6);
        assertEquals(1.0f, PunchEffectRules.scaleFor(CombatFx.Weight.LIGHT), 1e-6);
    }
}
