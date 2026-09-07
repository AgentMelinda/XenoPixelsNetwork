package net.bullettrain.xenopixelsmod.client.compat.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcFullDmzRendererTest {
    @Test
    void copyRenderStackPreservesTransformsAndCannotCorruptCallerOnFailure() {
        var original = new com.mojang.blaze3d.vertex.PoseStack();
        original.translate(2, 3, 4);
        original.scale(2, 3, 4);
        var before = original.last().copy();
        var isolated = NpcFullDmzRenderer.copyRenderPose(original);
        assertEquals(before.pose(), isolated.last().pose());
        assertEquals(before.normal(), isolated.last().normal());
        isolated.pushPose();
        isolated.translate(100, 100, 100);
        assertEquals(before.pose(), original.last().pose());
        assertEquals(before.normal(), original.last().normal());
        assertTrue(original.clear());
    }
    @Test
    void copyRefreshUsesElapsedTicksInsteadOfRenderFrameModulo() {
        assertTrue(NpcFullDmzRenderer.copyStatsRefreshDue(3, Integer.MIN_VALUE));
        org.junit.jupiter.api.Assertions.assertFalse(NpcFullDmzRenderer.copyStatsRefreshDue(20, 20));
        org.junit.jupiter.api.Assertions.assertFalse(NpcFullDmzRenderer.copyStatsRefreshDue(39, 20));
        assertTrue(NpcFullDmzRenderer.copyStatsRefreshDue(41, 20));
        assertTrue(NpcFullDmzRenderer.copyStatsRefreshDue(1, 100));
    }

    @Test
    void visualProfileCarriesAuthoritativeRaceAndActiveFormIntoAuraResolution() {
        NpcCombatProfile options = new NpcCombatProfile();
        options.stackGroup = "kaioken";
        options.stackId = "kaioken3";
        options.kiWeaponOn = true;
        options.kiWeaponType = "scythe";
        NpcAppearanceClient.State state = new NpcAppearanceClient.State(
                "saiyan", "supersaiyan", "supersaiyan2",
                true, "", "#112233", 1, 2, 3, 4, 5, 6,
                0x445566, 1.25f, new NpcDmzAppearance(), options.visualOptionsTag(),
                "", "", "");

        NpcCombatProfile result = NpcFullDmzRenderer.visualProfile(state);

        assertEquals("saiyan", result.raceId);
        assertEquals("supersaiyan", result.formGroup);
        assertEquals("supersaiyan2", result.formId);
        assertEquals("kaioken", result.stackGroup);
        assertEquals("kaioken3", result.stackId);
        assertTrue(result.kiWeaponOn);
        assertEquals("scythe", result.kiWeaponType);
        assertEquals("#445566", result.auraColorHex);
        assertEquals(1.25f, result.auraScale);
    }
}
