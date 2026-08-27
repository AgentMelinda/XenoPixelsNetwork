package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Fires a real DragonMineZ ki-attack projectile on behalf of a non-player NPC (My NPCs /
 * CustomNPCs entity), scaled from its {@link NpcCombatProfile} instead of a real DMZ
 * {@code StatsData} (which cannot be constructed for a non-{@code Player}).
 *
 * <p>{@link KiBlastEntity}/{@link KiWaveEntity} take any {@link LivingEntity} caster — verified
 * by decompiling DMZ's own {@code SkillManager} (which drives ki attacks for its non-player
 * {@code DBSagasEntity} boss mobs) — so no DMZ capability is needed to spawn one. Every
 * {@code setup*} call below internally aims the projectile from the caster's eye line and spawns
 * it via {@code level().addFreshEntity(this)} (verified in {@code KiBlastEntity
 * #finalizeSetupAndShoot}) — callers must not spawn it again.
 *
 * <p>The {@code setup*} parameter order used here was recovered by decompiling every
 * {@code SkillManager} lambda that calls the same method (not guessed): the incoming damage
 * float is always first, then speed, then an optional color int, then size, then a trailing
 * cast-time int — cross-checked against {@code SkillManager.getCalculatedDamage}/
 * {@code getCastDuration}, which feed exactly those two values into the lambdas that call these
 * setup methods.
 */
public final class NpcKiAttackDispatcher {
    private NpcKiAttackDispatcher() {}

    private static final int DEFAULT_COLOR = 0xFFFFFF;
    private static final int DEFAULT_CAST_TIME = 20;

    /** Fires DMZ's basic ki blast ball, scaled from the NPC's profile. */
    public static void fireKiBlast(LivingEntity caster, NpcCombatProfile profile) {
        Level level = caster.level();
        float damage = 2.0f + profile.strikePower * 0.5f;
        float speed = 1.2f + profile.kiPower * 0.02f;
        float size = 0.5f + profile.kiPower * 0.01f;
        new KiBlastEntity(level, caster)
                .setupKiBlast(caster, damage, speed, DEFAULT_COLOR, size, DEFAULT_CAST_TIME);
    }

    /** Fires DMZ's beam-style wave attack (Kamehameha-style), scaled from the NPC's profile. */
    public static void fireKiWave(LivingEntity caster, NpcCombatProfile profile) {
        Level level = caster.level();
        float damage = 3.0f + profile.strikePower * 0.75f;
        float speed = 1.5f + profile.kiPower * 0.02f;
        float size = 0.6f + profile.kiPower * 0.015f;
        new KiWaveEntity(level, caster)
                .setupKiHame(caster, damage, speed, size, DEFAULT_CAST_TIME);
    }

    /** Fires the attack named by a script/command, e.g. {@code "kiblast"} or {@code "kiwave"}. */
    public static boolean fire(String blastType, LivingEntity caster, NpcCombatProfile profile) {
        switch (blastType.toLowerCase(java.util.Locale.ROOT)) {
            case "kiblast" -> {
                fireKiBlast(caster, profile);
                return true;
            }
            case "kiwave", "kihame", "kamehame" -> {
                fireKiWave(caster, profile);
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
