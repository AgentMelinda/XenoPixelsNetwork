package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoRushTechniquesTest {

    @Test
    void registersFourNativeStrikeDefinitions() {
        XenoRushTechniques.register();

        assertEquals(4, java.util.stream.Stream.of(
                XenoRushTechniques.RUSH_LEFT,
                XenoRushTechniques.RUSH_RIGHT,
                XenoRushTechniques.RUSH_BREAKER,
                XenoRushTechniques.RUSH_FINISHER)
                .map(id -> PredefinedTechniques.STRIKE_REGISTRY.get(id))
                .peek(org.junit.jupiter.api.Assertions::assertNotNull)
                .count());
        assertNotNull(PredefinedTechniques.STRIKE_REGISTRY.get(XenoRushTechniques.RUSH_FINISHER));
        assertTrue(PredefinedTechniques.isPredefinedTechniqueId(XenoRushTechniques.RUSH_LEFT));
    }

    @Test
    void rushSequenceUsesAllFourNativeTechniquesInOrder() {
        assertEquals(XenoRushTechniques.RUSH_LEFT, XenoRushTechniques.idForRushStep(1));
        assertEquals(XenoRushTechniques.RUSH_RIGHT, XenoRushTechniques.idForRushStep(2));
        assertEquals(XenoRushTechniques.RUSH_BREAKER, XenoRushTechniques.idForRushStep(3));
        assertEquals(XenoRushTechniques.RUSH_FINISHER, XenoRushTechniques.idForRushStep(4));
        assertEquals(XenoRushTechniques.RUSH_LEFT, XenoRushTechniques.idForRushStep(5));
        assertEquals(0, XenoRushTechniques.slotForRushId(XenoRushTechniques.RUSH_LEFT));
        assertEquals(1, XenoRushTechniques.slotForRushId(XenoRushTechniques.RUSH_RIGHT));
        assertEquals(4, XenoRushTechniques.slotForRushId(XenoRushTechniques.RUSH_BREAKER));
        assertEquals(5, XenoRushTechniques.slotForRushId(XenoRushTechniques.RUSH_FINISHER));
    }
}
