package net.bullettrain.xenopixelsmod.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzSkillsHitboxesTest {

    @Test
    void themedRowsReachTheVisibleLevelColumn() {
        assertEquals(132, DmzSkillsHitboxes.rowRightOffset(true, 100));
        assertTrue(DmzSkillsHitboxes.insideRowX(131, 0));
    }

    @Test
    void rowStopsBeforeTheScrollbar() {
        assertFalse(DmzSkillsHitboxes.insideRowX(132, 0));
        assertFalse(DmzSkillsHitboxes.insideRowX(135, 0));
    }

    @Test
    void hiddenTabOverlapIsBlockedOnlyInsideTheThemedPanel() {
        assertTrue(DmzSkillsHitboxes.insidePanel(true, 130, 100, 0, 0));
        assertFalse(DmzSkillsHitboxes.insidePanel(true, 141, 100, 0, 0));
        assertFalse(DmzSkillsHitboxes.insidePanel(false, 130, 100, 0, 0));
    }

    @Test
    void stockModeKeepsDragonMineZsOriginalRowWidth() {
        assertEquals(100, DmzSkillsHitboxes.rowRightOffset(false, 100));
    }
}
