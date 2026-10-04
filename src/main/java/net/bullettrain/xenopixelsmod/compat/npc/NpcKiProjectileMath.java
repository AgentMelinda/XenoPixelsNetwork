package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.stats.techniques.KiAttackData;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.util.Mth;

/**
 * Player-parity geometry for generic NPC kiblast/kiwave. Damage still scales with kiPower;
 * size and speed never do — linear {@code kiPower * 0.01} at Integer.MAX_VALUE spawned a
 * tens-of-millions-of-blocks projectile and crushed the client.
 */
public final class NpcKiProjectileMath {
    private NpcKiProjectileMath() {}

    public static float blastSize(float charge) {
        return size(KiAttackData.KiType.SMALL_BALL, charge);
    }

    public static float blastSpeed(float charge) {
        return speed(KiAttackData.KiType.SMALL_BALL, charge);
    }

    public static float waveSize(float charge) {
        return size(KiAttackData.KiType.WAVE, charge);
    }

    public static float waveSpeed(float charge) {
        return speed(KiAttackData.KiType.WAVE, charge);
    }

    public static float size(KiAttackData.KiType type, float charge) {
        float raw = KiAttackData.getDefaultSizeForType(type) * safeCharge(charge);
        return clampSize(raw);
    }

    public static float speed(KiAttackData.KiType type, float charge) {
        float raw = KiAttackData.getDefaultSpeedForType(type) * Math.min(2.0f, safeCharge(charge));
        return clampSpeed(raw);
    }

    public static float clampSize(float size) {
        return clampPositive(size, XenoServerConfig.kiProjectileMaxSize, 320.0f);
    }

    public static float clampSpeed(float speed) {
        return clampPositive(speed, XenoServerConfig.kiProjectileMaxSpeed, 32.0f);
    }

    private static float safeCharge(float charge) {
        if (!Float.isFinite(charge) || charge <= 0.0f) {
            return 1.0f;
        }
        return charge;
    }

    private static float clampPositive(float value, float configured, float fallback) {
        float max = configured > 0.0f && Float.isFinite(configured) ? configured : fallback;
        if (!Float.isFinite(value) || value <= 0.0f) {
            return Math.min(1.0f, max);
        }
        return Mth.clamp(value, 0.05f, max);
    }
}
