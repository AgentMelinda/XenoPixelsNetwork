package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.Techniques;
import com.dragonminez.common.stats.techniques.TechniqueType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UltimateFinisherTechniqueTest {
    @Test void nativeStrikeCanBeUnlockedEquippedAndSelected() {
        UltimateFinisherTechnique.register();
        var strike = PredefinedTechniques.STRIKE_REGISTRY.get(UltimateFinisherTechnique.ID);
        assertNotNull(strike);
        assertEquals("UltimateFinisher", strike.getName());
        assertEquals(TechniqueType.STRIKE_ATTACK, strike.getType());
        assertEquals(40.0, strike.getCalculatedCost(null));
        assertEquals(400, strike.getActualCooldown());
        Techniques techniques = new Techniques();
        techniques.unlockTechnique(strike);
        techniques.equipTechnique(2, strike.getId());
        techniques.selectSlot(2);
        assertEquals(strike.getId(), techniques.getSelectedTechnique().getId());
        assertFalse(XenoRushTechniques.isRushId(strike.getId()));
        assertEquals(XenoRushTechniques.RUSH_LEFT, XenoRushTechniques.idForRushStep(5));
    }

    @Test void registrationPreservesExistingDefinition() {
        UltimateFinisherTechnique.register();
        var before = PredefinedTechniques.STRIKE_REGISTRY.get(UltimateFinisherTechnique.ID);
        UltimateFinisherTechnique.register();
        assertSame(before, PredefinedTechniques.STRIKE_REGISTRY.get(UltimateFinisherTechnique.ID));
    }
}
