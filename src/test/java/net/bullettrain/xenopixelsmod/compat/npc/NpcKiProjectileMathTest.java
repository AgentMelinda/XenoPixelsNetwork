package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.stats.techniques.KiAttackData;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcKiProjectileMathTest {
    @Test
    void defaultKiblastSizeIsDmzSmallBallTimesCharge() {
        assertEquals(KiAttackData.getDefaultSizeForType(KiAttackData.KiType.SMALL_BALL),
                NpcKiProjectileMath.blastSize(1.0f), 1.0e-4f);
        assertEquals(3.0f, NpcKiProjectileMath.blastSize(1.0f), 1.0e-4f);
        assertEquals(6.0f, NpcKiProjectileMath.blastSize(2.0f), 1.0e-4f);
    }

    @Test
    void maxKiPowerCannotInflateGeometry() {
        // The old formula was 0.5 + kiPower * 0.01. Integer.MAX_VALUE made a 21-million-block ball.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.kiPower = Integer.MAX_VALUE;
        float charge = profile.chargeFactor();
        assertTrue(NpcKiProjectileMath.blastSize(charge) <= XenoServerConfig.kiProjectileMaxSize);
        assertTrue(NpcKiProjectileMath.blastSpeed(charge) <= XenoServerConfig.kiProjectileMaxSpeed);
        assertTrue(NpcKiProjectileMath.waveSize(charge) <= XenoServerConfig.kiProjectileMaxSize);
        assertTrue(NpcKiProjectileMath.waveSpeed(charge) <= XenoServerConfig.kiProjectileMaxSpeed);
        assertTrue(NpcKiProjectileMath.blastSize(charge) < 20.0f);
        assertTrue(NpcKiProjectileMath.blastSpeed(charge) <= 2.0f);
    }

    @Test
    void clampHonoursConfiguredCeilings() {
        assertEquals(XenoServerConfig.kiProjectileMaxSize,
                NpcKiProjectileMath.clampSize(Float.MAX_VALUE), 1.0e-3f);
        assertEquals(XenoServerConfig.kiProjectileMaxSpeed,
                NpcKiProjectileMath.clampSpeed(1.0e9f), 1.0e-3f);
        assertTrue(NpcKiProjectileMath.clampSize(-10.0f) > 0.0f);
        assertTrue(NpcKiProjectileMath.clampSpeed(Float.NaN) > 0.0f);
    }
}
