package net.bullettrain.xenopixelsmod.anim;

import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatStateAnimTest {

    @Test
    void everyNamedStateIsABindableSlot() {
        Set<String> names = Set.of(CombatStateAnim.slotNames());
        assertTrue(names.containsAll(Arrays.asList(
                "TRANSFORM", "PUNCH", "CHARGE_PUNCH", "CHARGE_PUNCH_FIRE",
                "CHARGE_KICK", "CHARGE_KICK_FIRE", "CHARGE_KI")));
        assertEquals(TechniqueAnimSlot.TRANSFORM, CombatStateAnim.slotOf("transform"));
        assertEquals(TechniqueAnimSlot.CHARGE_PUNCH, CombatStateAnim.slotOf("CHARGE_PUNCH"));
    }

    @Test
    void holdSlotsAreTheOnesThatShouldKeepTheLastFrame() {
        assertTrue(TechniqueAnimSlot.TRANSFORM.hold());
        assertTrue(TechniqueAnimSlot.CHARGE_PUNCH.hold());
        assertTrue(TechniqueAnimSlot.CHARGE_KICK.hold());
        assertTrue(TechniqueAnimSlot.CHARGE_KI.hold());
        assertFalse(TechniqueAnimSlot.PUNCH.hold());
        assertFalse(TechniqueAnimSlot.CHARGE_PUNCH_FIRE.hold());
        assertFalse(TechniqueAnimSlot.CHARGE_KICK_FIRE.hold());
    }

    @Test
    void emptyDefaultsLeaveTheBuiltInMoveInPlace() {
        assertEquals("", TechniqueAnimSlot.TRANSFORM.defaultAnim());
        assertEquals("", TechniqueAnimSlot.PUNCH.defaultAnim());
        assertEquals(net.bullettrain.xenopixelsmod.combat.v2.V2ChargeRules.PUNCH_HOLD, TechniqueAnimSlot.CHARGE_PUNCH.defaultAnim());
        assertEquals(net.bullettrain.xenopixelsmod.combat.v2.V2ChargeRules.KICK_HOLD, TechniqueAnimSlot.CHARGE_KICK.defaultAnim());
    }

    @Test
    void unknownSlotIsRejected() {
        assertEquals(null, CombatStateAnim.slotOf("not_a_state"));
        assertFalse(CombatStateAnim.bindGlobal("not_a_state", "my_jab"));
    }
}
