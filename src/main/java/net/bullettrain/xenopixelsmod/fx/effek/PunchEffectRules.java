package net.bullettrain.xenopixelsmod.fx.effek;

import net.minecraft.world.phys.Vec3;

import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Which hits get a punch effect, and which one. */
public final class PunchEffectRules {
    private PunchEffectRules() {}

    /** Plain melee damage: a player's or a mob's own attack (not arrows, blasts, magic). */
    public static boolean isMeleeType(ResourceKey<DamageType> type) {
        return DamageTypes.PLAYER_ATTACK.equals(type) || DamageTypes.MOB_ATTACK.equals(type);
    }

    /** A DMZ punch: direct melee from a player or a DMZ-profile NPC (native or MyNPCs/CustomNPCs). */
    public static boolean isDmzMelee(DamageSource source, LivingEntity attacker) {
        if (source == null || attacker == null || source.getDirectEntity() != attacker) return false;
        if (!source.typeHolder().unwrapKey().map(PunchEffectRules::isMeleeType).orElse(false)) return false;
        return attacker instanceof Player || NpcCombatProfile.hasProfile(attacker);
    }

    public static EffectSlot slotFor(CombatFx.Weight weight) {
        return switch (weight) {
            case LIGHT -> EffectSlot.PUNCH_IMPACT;
            case HEAVY, ULTIMATE -> EffectSlot.PUNCH_HEAVY;
            case GUARD -> EffectSlot.PUNCH_GUARD;
        };
    }

    public static float scaleFor(CombatFx.Weight weight) {
        return weight == CombatFx.Weight.ULTIMATE ? 1.6f : 1.0f;
    }

    /**
     * The height the attacker's crosshair meets the target at: the eye ray followed out to the
     * target's horizontal distance, kept between the target's feet and head (2026-09-29 owner:
     * punch effects at crosshair level). A ray with no horizontal reach (aiming straight up or
     * down) uses eye height.
     */
    public static double crosshairY(Vec3 eye, Vec3 look, Vec3 targetCenter, double minY, double maxY) {
        double reach = Math.hypot(targetCenter.x - eye.x, targetCenter.z - eye.z);
        double horizontal = Math.hypot(look.x, look.z);
        double y = horizontal < 1.0e-4 ? eye.y : eye.y + look.y * (reach / horizontal);
        return Math.max(minY, Math.min(maxY, y));
    }
}
