package net.bullettrain.xenopixelsmod.combat.technique;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoStrikeSkillsTest {

    @Test
    void dmzStrikeListIncludesRushAttacksAndRushCombo() {
        List<String> ids = XenoStrikeSkills.ids();
        assertTrue(ids.contains(XenoRushTechniques.RUSH_LEFT));
        assertTrue(ids.contains(XenoRushTechniques.RUSH_RIGHT));
        assertTrue(ids.contains(XenoRushTechniques.RUSH_BREAKER));
        assertTrue(ids.contains(XenoRushTechniques.RUSH_FINISHER));
        assertTrue(ids.contains(XenoComboStrikes.RUSH_COMBO));
        assertTrue(ids.contains(XenoComboStrikes.LIFT_COMBO));
    }

    @Test
    void appendDoesNotDuplicateExistingIds() {
        List<String> existing = new ArrayList<>();
        existing.add("meteor");
        existing.add(XenoRushTechniques.RUSH_LEFT);
        List<String> merged = XenoStrikeSkills.append(existing);
        assertTrue(merged.contains("meteor"));
        assertEquals(1, merged.stream().filter(XenoRushTechniques.RUSH_LEFT::equals).count());
        assertTrue(merged.contains(XenoComboStrikes.RUSH_COMBO));
        assertTrue(merged.contains(XenoComboStrikes.LIFT_COMBO));
    }

    @Test
    void installIntoWritesLiftComboOntoTheLiveList() {
        List<String> live = new ArrayList<>();
        live.add("meteor");
        XenoStrikeSkills.installInto(live);
        assertTrue(live.contains(XenoComboStrikes.LIFT_COMBO));
        assertTrue(live.contains("xenopixelsmod:lift_combo"));
    }
}
