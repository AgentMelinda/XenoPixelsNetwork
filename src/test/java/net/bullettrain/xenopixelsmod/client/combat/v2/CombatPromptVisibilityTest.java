package net.bullettrain.xenopixelsmod.client.combat.v2;

import com.google.gson.Gson;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatPromptVisibilityTest {
    @Test
    void lockStatesCanBeDisabledIndependentlyAndSurviveSerialization() {
        var saved = XenoClientConfig.snapshot();
        try {
            Gson gson = new Gson();
            for (boolean locked : new boolean[]{false, true}) {
                for (boolean unlocked : new boolean[]{false, true}) {
                    var data = new XenoClientConfig.Data();
                    data.combatPromptsWithLockOn = locked;
                    data.combatPromptsWithoutLockOn = unlocked;
                    XenoClientConfig.apply(data);
                    var restored = gson.fromJson(gson.toJson(XenoClientConfig.snapshot()), XenoClientConfig.Data.class);
                    XenoClientConfig.apply(restored);
                    assertEquals(locked, XenoClientConfig.showCombatPrompts(true));
                    assertEquals(unlocked, XenoClientConfig.showCombatPrompts(false));
                }
            }
        } finally {
            XenoClientConfig.apply(saved);
        }
    }

    @Test
    void olderConfigKeepsBothPromptStatesVisible() {
        var data = new Gson().fromJson("{\"configVersion\":6}", XenoClientConfig.Data.class);
        assertTrue(data.combatPromptsWithLockOn);
        assertTrue(data.combatPromptsWithoutLockOn);
    }
}
