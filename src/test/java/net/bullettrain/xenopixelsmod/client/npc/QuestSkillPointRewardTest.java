package net.bullettrain.xenopixelsmod.client.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-30 owner: "remove give xenoskillpoints". XenoSkill points are XenoPixels progression;
 * XenoNPCs is exported from the same source with mod id "xenonpcs" and has no use for them, so its
 * quest editor neither offers nor pays them. XenoPixels keeps the reward.
 */
class QuestSkillPointRewardTest {
    @Test
    void onlyXenoPixelsOffersXenoSkillPoints() {
        // Not the literal XenoPixels id: the exporter renames that string to "xenonpcs" in XenoNPCs.
        assertTrue(XenoNpcEditorScreen.xenoSkillPointsOffered("anyothermod"));
        assertFalse(XenoNpcEditorScreen.xenoSkillPointsOffered("xenonpcs"));
    }
}
