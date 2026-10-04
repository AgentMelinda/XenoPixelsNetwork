package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/** Selects the MyNPC-style resistance channel for a received damage source. */
public final class NpcDamageCategory {
    private NpcDamageCategory() {}

    public static int resistanceFor(DamageSource source, NpcCombatProfile profile) {
        if (source == null) return 0;
        return resistanceFor(source.is(DamageTypeTags.IS_EXPLOSION),
                source.is(DamageTypeTags.IS_PROJECTILE),
                source.getDirectEntity() instanceof net.minecraft.world.entity.LivingEntity,
                profile);
    }

    static int resistanceFor(boolean explosion, boolean projectile, boolean livingDirect,
                             NpcCombatProfile profile) {
        if (profile == null) return 0;
        if (explosion) {
            return NpcCombatProfile.clampNpcResistance(profile.npcExplosionResistance);
        }
        if (projectile) {
            return NpcCombatProfile.clampNpcResistance(profile.npcArrowResistance);
        }
        if (livingDirect) {
            return NpcCombatProfile.clampNpcResistance(profile.npcMeleeResistance);
        }
        return 0;
    }
}
