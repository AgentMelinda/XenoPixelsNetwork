package net.bullettrain.xenopixelsmod.client.compat.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcFullDmzRendererTest {
    @Test
    void visualProfileCarriesAuthoritativeRaceAndActiveFormIntoAuraResolution() {
        NpcCombatProfile options = new NpcCombatProfile();
        options.stackGroup = "kaioken";
        options.stackId = "kaioken3";
        NpcAppearanceClient.State state = new NpcAppearanceClient.State(
                "saiyan", "supersaiyan", "supersaiyan2",
                true, "", "#112233", 1, 2, 3, 4, 5, 6,
                0x445566, 1.25f, new NpcDmzAppearance(), options.visualOptionsTag());

        NpcCombatProfile result = NpcFullDmzRenderer.visualProfile(state);

        assertEquals("saiyan", result.raceId);
        assertEquals("supersaiyan", result.formGroup);
        assertEquals("supersaiyan2", result.formId);
        assertEquals("kaioken", result.stackGroup);
        assertEquals("kaioken3", result.stackId);
        assertEquals("#445566", result.auraColorHex);
        assertEquals(1.25f, result.auraScale);
    }
}
