package net.bullettrain.xenopixelsmod.npc.job;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcJob;
import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.bullettrain.xenopixelsmod.npc.faction.XenoFactions;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Applies the Healer job's configured effects to nearby living entities on a bounded timer. */
public final class NpcHealerJob {
    private static final int DURATION_TICKS = 100;

    private NpcHealerJob() {
    }

    public static void tick(XenoNpcEntity npc, NpcCombatProfile profile) {
        if (npc.level().isClientSide || profile == null || !profile.jobEnabled
                || XenoNpcJob.byId(profile.job) != XenoNpcJob.HEALER
                || profile.healerRange <= 0 || profile.healerEffects.isEmpty()) {
            return;
        }
        int speed = Math.max(10, Math.min(1200, profile.healerSpeed));
        if ((npc.tickCount + npc.getId()) % speed != 0) return;

        // Resolve once per scan, not once per target. Missing modded effects are simply skipped.
        List<Effect> effects = new ArrayList<>();
        for (Map.Entry<String, Integer> configured : profile.healerEffects.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(configured.getKey());
            if (id != null) BuiltInRegistries.MOB_EFFECT.getHolder(id).ifPresent(holder ->
                    effects.add(new Effect(holder, Math.max(0, Math.min(9,
                            configured.getValue() == null ? 0 : configured.getValue())))));
        }
        if (effects.isEmpty()) return;

        int range = Math.max(0, Math.min(64, profile.healerRange));
        for (LivingEntity target : npc.level().getEntitiesOfClass(LivingEntity.class,
                npc.getBoundingBox().inflate(range, range / 2.0, range),
                target -> target != npc && target.isAlive())) {
            if (!affects(profile.healerType, hostileTo(npc, target))) continue;
            for (Effect effect : effects) {
                target.addEffect(new MobEffectInstance(effect.holder(), DURATION_TICKS,
                        effect.amplifier()));
            }
        }
    }

    /** MyNPCs' selector: friendly, hostile, or everyone. */
    public static boolean affects(int type, boolean hostile) {
        return type == 2 || (type == 0 && !hostile) || (type == 1 && hostile);
    }

    private static boolean hostileTo(XenoNpcEntity npc, LivingEntity target) {
        String factionId = npc.npcData().faction();
        if (target instanceof Player player) {
            XenoFaction faction = XenoFactions.get(factionId);
            if (faction == null) return false;
            int standing = XenoCapabilities.get(player)
                    .map(data -> data.getFactionStanding(factionId))
                    .orElse(faction.defaultStanding());
            return XenoFaction.attitudeAt(standing) == XenoFaction.Attitude.HOSTILE;
        }
        if (target instanceof XenoNpcEntity other) {
            return XenoFactions.hostile(factionId, other.npcData().faction());
        }
        // The pinned MyNPCs implementation classifies other Mob entities as aggressive here.
        return target instanceof Mob;
    }

    private record Effect(Holder<MobEffect> holder, int amplifier) {
    }
}
