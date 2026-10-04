package net.bullettrain.xenopixelsmod.combat.fx;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.fx.effek.EffectSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-29 owner: "its effects are now different from before". The constant-sum aura looked
 * washed out, so the approved classic look is the default again; the smooth one is a switch.
 */
class SparkingAuraStyleTest {
    @Test
    void theClassicAuraIsTheDefault() {
        assertFalse(new XenoServerConfig.Data().effekseerSparkingSmooth);
        assertEquals(EffectSlot.SPARKING_AURA, SparkingAuraFx.groundAura(false));
        assertEquals(EffectSlot.SPARKING_AURA_SMOOTH, SparkingAuraFx.groundAura(true));
        assertTrue(EffectSlot.SPARKING_AURA_SMOOTH.upright());
    }

    /**
     * 2026-09-29 owner: the flight aura did not switch back when DMZ fast flight was off. DMZ's own
     * rule for the lying-flat pose (FlySkillEvent.isFlyingFast, DragonMineZ 2.1.3): fly skill active,
     * flight mode not 1, and moving faster than 0.55 blocks a tick.
     */
    @Test
    void theFlightAuraFollowsDmzsFastFlightRule() {
        assertTrue(SparkingAuraFx.flyingFast(true, 0, 0.36));
        assertFalse(SparkingAuraFx.flyingFast(true, 1, 0.36), "flight mode 1: no fast flight pose");
        assertFalse(SparkingAuraFx.flyingFast(true, 0, 0.2), "hovering or drifting: upright");
        assertFalse(SparkingAuraFx.flyingFast(false, 0, 2.0), "fly skill off");
    }
}
