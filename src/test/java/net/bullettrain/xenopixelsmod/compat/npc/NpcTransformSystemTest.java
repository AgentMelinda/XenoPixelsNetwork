package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.config.FormConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcTransformSystemTest {
    @Test
    void targetFormAuraOverridesTheNpcAura() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.auraColor = 0xABCDEF;
        FormConfig.FormData target = new FormConfig.FormData();
        target.setAuraColor("#123456");

        assertEquals(0x123456, NpcTransformSystem.resolveTransformAuraColor(profile, target));
    }

    @Test
    void configuredNpcAuraIsUsedWhenTheFormHasNoAuraColor() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.auraColor = 0xABCDEF;

        assertEquals(0xABCDEF, NpcTransformSystem.resolveTransformAuraColor(
                profile, new FormConfig.FormData()));
    }

    @Test
    void unsetTransformationAuraFallsBackToTheDmzRaceAura() {
        NpcCombatProfile profile = new NpcCombatProfile();
        assertEquals(NpcAuraResolver.baseRgb(profile), NpcTransformSystem.resolveTransformAuraColor(
                profile, new FormConfig.FormData()));
    }

    @Test
    void explicitlyBlackFormAuraRemainsBlack() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.auraColor = 0xFFFFFF;
        FormConfig.FormData target = new FormConfig.FormData();
        target.setAuraColor("#000000");

        assertEquals(0x000000, NpcTransformSystem.resolveTransformAuraColor(profile, target));
    }
}
