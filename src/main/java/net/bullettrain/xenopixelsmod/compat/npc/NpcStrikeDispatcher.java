package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cast-and-hit bridge for DMZ predefined strike techniques on non-player NPCs. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcStrikeDispatcher {
    private static final double MAX_RANGE_SQ = 8.0 * 8.0;
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(UUID target, String technique, long fireAt) {}

    private NpcStrikeDispatcher() {}

    public static boolean fire(String id, LivingEntity caster, NpcCombatProfile profile,
                               LivingEntity target) {
        StrikeAttackData data = strikeData(caster, id);
        if (data == null || caster == null || profile == null || !valid(caster, target)
                || !NpcKiCooldowns.ready(caster, data.getId())) {
            return false;
        }
        double cost = caster instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity clone
                ? net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge.strikeCost(clone, data)
                : NpcTechniqueMath.strikeCost(profile, data);
        if (!NpcResources.spend(caster, profile, cost,
                Math.max(1.0, profile.strikeDamage() * 0.1))) {
            return false;
        }
        NpcKiAim.hold(caster, target, Math.max(8, data.getActualCastTime()));
        String animation = data.getAnimationId();
        if (animation != null && !animation.isBlank()) {
            NpcGeckoAnim.play(caster, animation);
        }
        caster.swing(InteractionHand.MAIN_HAND, true);
        long now = caster.level().getGameTime();
        PENDING.put(caster.getUUID(), new Pending(target.getUUID(), data.getId(),
                now + Math.max(1, data.getActualCastTime())));
        NpcKiCooldowns.consume(caster, data);
        return true;
    }

    public static void cancel(UUID id) {
        if (id != null) PENDING.remove(id);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> entry = it.next();
            LivingEntity caster = find(event, entry.getKey());
            if (caster == null || !caster.isAlive()) {
                it.remove();
                continue;
            }
            Pending pending = entry.getValue();
            if (caster.level().getGameTime() < pending.fireAt()) continue;
            it.remove();
            Entity raw = caster.level() instanceof ServerLevel level
                    ? level.getEntity(pending.target()) : null;
            if (!(raw instanceof LivingEntity target) || !valid(caster, target)) continue;
            StrikeAttackData data = strikeData(caster, pending.technique());
            if (data == null) continue;
            NpcCombatProfile profile = NpcCombatProfile.read(caster);
            double configDamage = Math.max(0.0, ConfigManager.getTechniqueConfig()
                    .getStrikeConfig(data.getId()).getDamageMultiplier());
            float damage = (float) (profile.strikeDamage()
                    * data.getActualDamageMultiplier() * configDamage);
            caster.swing(InteractionHand.MAIN_HAND, true);
            target.invulnerableTime = 0;
            target.hurt(MainDamageTypes.strikeAttack(caster.level(), caster, data.getId()), damage);
        }
    }

    private static StrikeAttackData strikeData(LivingEntity caster, String id) {
        return caster instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity clone
                ? net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge.strike(clone, id)
                : PredefinedTechniqueLookup.findStrike(id);
    }

    private static boolean valid(LivingEntity caster, LivingEntity target) {
        if (caster instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity clone
                && (!net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge.active(clone)
                || !net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge.validTarget(
                        net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge.owner(clone), target)
                || net.bullettrain.xenopixelsmod.combat.clone.XenoCloneSystem.lockedTarget(
                        net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge.owner(clone)) != target)) return false;
        return target != null && target != caster && target.isAlive()
                && target.level() == caster.level()
                && caster.distanceToSqr(target) <= MAX_RANGE_SQ
                && caster.hasLineOfSight(target);
    }

    private static LivingEntity find(ServerTickEvent.Post event, UUID id) {
        return NpcEntityLookup.findLiving(event.getServer(), id);
    }
}
