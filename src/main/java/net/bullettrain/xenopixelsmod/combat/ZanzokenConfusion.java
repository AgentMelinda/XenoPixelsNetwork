package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge;
import net.bullettrain.xenopixelsmod.combat.clone.CloneDetectRange;
import net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes AI lose a fighter while their Zanzoken images are standing in for them.
 *
 * <p>Two halves, because a fight contains both kinds of attacker:
 *
 * <ul>
 *   <li>{@link #onChangeTarget} stops anything <b>acquiring</b> the fighter while the images stand.
 *   <li>{@link #scatter} moves the attackers that <b>already had</b> the fighter onto one of the
 *       images, at the moment the ring goes up.
 * </ul>
 *
 * <p>Combat-brain NPCs use the per-NPC Brain page {@code zanzoken} flag and chance: a failed roll
 * leaves them on the real body for the rest of that ring. Vanilla and saga mobs still always
 * follow {@link XenoServerConfig#zanzokenConfusesAi}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ZanzokenConfusion {

    private static double detectRange() {
        return XenoServerConfig.clampedZanzokenDetectRange();
    }
    /** NPC id → player who currently has them on an afterimage. */
    private static final Map<UUID, UUID> FOOLED_BY = new ConcurrentHashMap<>();

    private ZanzokenConfusion() {
    }

    /**
     * Whether this attacker should be redirected onto a Zanzoken image.
     *
     * @param brainAllowsFool the Brain-page roll for a combat-brain NPC; ignored when
     *                        {@code combatBrain} is false
     */
    static boolean confuse(boolean zanzokenEnabled, boolean confusesAi,
                           boolean combatBrain, boolean brainAllowsFool) {
        if (!zanzokenEnabled || !confusesAi) {
            return false;
        }
        return !combatBrain || brainAllowsFool;
    }

    /** True when this NPC is currently swinging at this fighter's afterimage. */
    public static boolean isFooledBy(LivingEntity npc, LivingEntity target) {
        if (npc == null || target == null) {
            return false;
        }
        UUID owner = FOOLED_BY.get(npc.getUUID());
        if (owner == null) {
            return false;
        }
        if (target instanceof ServerPlayer player) {
            return owner.equals(player.getUUID());
        }
        if (target instanceof XenoCloneEntity clone) {
            ServerPlayer fighter = CloneCombatBridge.owner(clone);
            return fighter != null && owner.equals(fighter.getUUID());
        }
        return false;
    }

    public static void clearFooledBy(UUID playerId) {
        if (playerId == null) {
            return;
        }
        FOOLED_BY.entrySet().removeIf(entry -> playerId.equals(entry.getValue()));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FOOLED_BY.clear();
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide) return;
        if (!XenoServerConfig.zanzokenEnabled || !XenoServerConfig.zanzokenConfusesAi) return;
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)) return;
        if (event.getOriginalAboutToBeSetTarget() == player) return;
        if (!Bt3CombatEvents.afterimagesActive(player)) return;
        LivingEntity attacker = event.getEntity();
        if (!CloneDetectRange.withinSqr(attacker.distanceToSqr(player),
                XenoServerConfig.zanzokenDetectRange)) {
            return;
        }
        if (combatBrain(attacker) && !isFooledBy(attacker, player)) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * Moves whoever was hunting a lost image onto one of the images still standing.
     */
    public static void rehome(XenoCloneEntity lost, List<XenoCloneEntity> survivors) {
        if (lost == null || survivors == null || survivors.isEmpty()) return;
        if (!(lost.level() instanceof ServerLevel level)) return;

        AABB range = lost.getBoundingBox().inflate(detectRange());
        for (Mob mob : level.getEntitiesOfClass(Mob.class, range, mob -> mob.getTarget() == lost)) {
            if (!isFooledBy(mob, lost) && combatBrain(mob)) {
                continue;
            }
            try {
                mob.setTarget(survivors.get(lost.getRandom().nextInt(survivors.size())));
            } catch (RuntimeException exception) {
                XenoPixelsMod.LOGGER.debug("Could not retarget confused mob {}", mob.getUUID(), exception);
            }
        }
    }

    public static void scatter(ServerPlayer player, List<XenoCloneEntity> images) {
        if (player == null || images == null || images.isEmpty()) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        AABB range = player.getBoundingBox().inflate(detectRange());
        for (Mob mob : level.getEntitiesOfClass(Mob.class, range, mob -> mob.getTarget() == player)) {
            if (!shouldScatter(mob)) continue;
            try {
                XenoCloneEntity image = images.get(player.getRandom().nextInt(images.size()));
                FOOLED_BY.put(mob.getUUID(), player.getUUID());
                mob.setTarget(image);
            } catch (RuntimeException exception) {
                XenoPixelsMod.LOGGER.debug("Could not scatter mob {} to an afterimage", mob.getUUID(), exception);
            }
        }
    }

    private static boolean shouldScatter(LivingEntity attacker) {
        boolean brain = combatBrain(attacker);
        boolean allows = !brain || NpcCombatProfile.readCached(attacker)
                .allowBrainAction("zanzoken", attacker.getRandom());
        return confuse(XenoServerConfig.zanzokenEnabled, XenoServerConfig.zanzokenConfusesAi,
                brain, allows);
    }

    private static boolean combatBrain(LivingEntity attacker) {
        return attacker != null
                && NpcCombatProfile.hasProfile(attacker)
                && NpcCombatProfile.readCached(attacker).combatBrain;
    }
}
