package net.bullettrain.xenopixelsmod.features.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-10-02 owner: the shadow gets the V9 brain "just with ki blasts and chase"; the dummytrain
 * one "only attacks you no ki blasts".
 */
class TrainingNpcTest {

    @Test
    void theMeleePartnerFiresNoKiAtAll() {
        for (String action : TrainingNpc.KI_ACTIONS) {
            assertFalse(TrainingNpc.kiActionOn(TrainingNpc.Kind.MELEE, action), action);
        }
    }

    @Test
    void theShadowKeepsKiBlastsOnly() {
        assertTrue(TrainingNpc.kiActionOn(TrainingNpc.Kind.KI, "kiblast"));
        for (String action : TrainingNpc.KI_ACTIONS) {
            if (!action.equals("kiblast")) assertFalse(TrainingNpc.kiActionOn(TrainingNpc.Kind.KI, action), action);
        }
    }
}
