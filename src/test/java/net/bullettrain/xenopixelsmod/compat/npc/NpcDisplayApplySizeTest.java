package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scale rule behind "the NPC, its aura and its transform particles all render tiny".
 *
 * <p>{@code getSize} reads a CustomNPCs Display object by reflection, so a native Xeno NPC answers
 * 0. Four separate call sites treated that 0 as a real size and computed {@code max(0.05, 0/5)} - a
 * twentieth of normal. These tests pin the rule that 0 means "unset".
 *
 * <p>They exercise the profile arm only: a real {@code LivingEntity} cannot be constructed without a
 * running client, so the entity arm is covered by the in-game checks in the handoff.
 */
class NpcDisplayApplySizeTest {

    @Test
    void anUnsetSizeRendersAtNormalScale() {
        NpcCombatProfile profile = new NpcCombatProfile();
        assertEquals(0, profile.baseSize, "the shipped default is unset");
        assertEquals(1.0f, NpcDisplayApply.sizeScale(null, profile), 1.0e-4f);
    }

    @Test
    void aNullProfileAndNoEntityStillRendersAtNormalScale() {
        // Nothing should ever be able to produce the 0.05x shrink by accident.
        assertEquals(1.0f, NpcDisplayApply.sizeScale(null, null), 1.0e-4f);
    }

    @Test
    void theProfileSizeDrivesTheScaleWhenThereIsNoDisplay() {
        NpcCombatProfile profile = new NpcCombatProfile();

        // DragonMineZ's default size is 5, which is 1.0x.
        profile.baseSize = 5;
        assertEquals(1.0f, NpcDisplayApply.sizeScale(null, profile), 1.0e-4f);

        profile.baseSize = 10;
        assertEquals(2.0f, NpcDisplayApply.sizeScale(null, profile), 1.0e-4f);

        profile.baseSize = 1;
        assertEquals(0.2f, NpcDisplayApply.sizeScale(null, profile), 1.0e-4f);
    }

    @Test
    void theScaleNeverFallsToTheOldShrunkenValue() {
        // The 0.05 floor is still there for a genuinely tiny configured size, but it must never be
        // reached by an *unset* one, which is what the bug was.
        NpcCombatProfile unset = new NpcCombatProfile();
        assertTrue(NpcDisplayApply.sizeScale(null, unset) > 0.9f);
    }

    @Test
    void theDefaultSizeConstantMatchesWhatTheScaleDividesBy() {
        assertEquals(5.0f, NpcDisplayApply.DEFAULT_SIZE, 1.0e-4f);
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.baseSize = (int) NpcDisplayApply.DEFAULT_SIZE;
        assertEquals(1.0f, NpcDisplayApply.sizeScale(null, profile), 1.0e-4f);
    }
}
