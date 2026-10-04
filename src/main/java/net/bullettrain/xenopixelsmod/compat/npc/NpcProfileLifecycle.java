package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repairs only CustomNPCs entities that explicitly carry an Xeno NPC profile.
 * Ordinary CustomNPC statues keep their configured NoAI setting untouched.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcProfileLifecycle {
    private static final int RESPAWN_GRACE_TICKS = 40;
    private static final Map<UUID, Integer> RESPAWN_DEADLINES = new ConcurrentHashMap<>();
    private static final Map<UUID, LivingEntity> PROFILED = new ConcurrentHashMap<>();

    private NpcProfileLifecycle() {}

    public static void track(Entity entity) {
        if (entity instanceof LivingEntity living && !entity.level().isClientSide()
                && NpcCombatProfile.hasProfile(entity)) {
            PROFILED.put(entity.getUUID(), living);
            NpcEntityLookup.remember(entity);
        }
    }

    /** Called after profile writes and entity loads to prevent stale NoAI NBT freezing profiled NPCs. */
    public static void repairAi(Entity entity) {
        if (!(entity instanceof Mob mob)
                || entity.level().isClientSide()
                || !NpcCombatProfile.hasProfile(entity)
                || !isCustomNpc(entity)) {
            return;
        }
        if (entity instanceof LivingEntity living && NpcKiAttackDispatcher.isOwnerClashing(living)) {
            return;
        }
        if (mob.isNoAi()) {
            mob.setNoAi(false);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            Entity entity = event.getEntity();
            if (entity instanceof net.minecraft.world.entity.player.Player) {
                NpcNegativeEffectPersistence.discard(entity);
            } else {
                NpcNegativeEffectPersistence.restore(entity);
            }
            repairAi(entity);
            if (NpcCombatProfile.hasProfile(entity)) {
                track(entity);
                NpcCombatProfile profile = NpcCombatProfile.read(entity);
                NpcCounterpartSync.forceLifecycle(entity, profile);
                if (entity instanceof LivingEntity living) NpcFormAttributeSync.apply(living, profile);
            }

        }
    }

    @SubscribeEvent
    public static void onPlayerClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        NpcNegativeEffectPersistence.copyOnClone(event.getOriginal(), event.getEntity());
    }

    /**
     * A Xeno NPC says its KILL line when something dies to it.
     *
     * <p>Separate from {@link #onDeath} because that one is about the entity that <em>died</em> and
     * bails early for anything that is not a CustomNPC - this is about its killer. The KILL
     * category has been loadable from role definitions since lines existed and had no trigger, so
     * a line written for it was never said.
     */
    @SubscribeEvent
    public static void onKillSpeech(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getSource().getEntity()
                instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity killer
                && killer != event.getEntity()) {
            net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(killer, "kill",
                    event.getEntity() instanceof net.minecraft.world.entity.player.Player
                            ? event.getEntity() : null, null, event.getEntity(), 0.0f,
                    n -> new xenoapi.npcs.api.event.NpcEvent.KilledEntityEvent(n, event.getEntity()));
            net.bullettrain.xenopixelsmod.npc.lines.XenoNpcSpeech.speak(killer,
                    net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.KILL,
                    event.getEntity() instanceof net.minecraft.world.entity.player.Player victim
                            ? victim : null);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity npc = event.getEntity();
        if (!npc.level().isClientSide()) NpcNegativeEffectPersistence.capture(npc);
        if (npc.level().isClientSide() || !NpcCombatProfile.hasProfile(npc) || !isCustomNpc(npc)) {
            return;
        }
        NpcKiAim.cancel(npc);
        NpcKiCooldowns.clear(npc.getUUID());
        NpcChargeMoves.cancel(npc);
        NpcHakai.cancel(npc);
        NpcCombatBrain.disengage(npc);
        net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatBrain.disengage(npc);
        NpcTransformSystem.cancel(npc);
        NpcStrikeDispatcher.cancel(npc.getUUID());
        NpcAuraFx.hide(npc);

        if (npc.level() instanceof ServerLevel level && respawnType(npc) == 0) {
            int respawnTicks = Math.max(1, respawnSeconds(npc)) * 20;
            RESPAWN_DEADLINES.put(npc.getUUID(),
                    level.getServer().getTickCount() + respawnTicks + RESPAWN_GRACE_TICKS);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        net.bullettrain.xenopixelsmod.npc.NpcAiGoals.forget(entity.getUUID());
        if (entity instanceof LivingEntity living) {
            NpcNegativeEffectPersistence.beforeSave(living);
            NpcKiAim.cancel(living);
            NpcChargeMoves.cancel(living);
            NpcHakai.cancel(living);
            NpcCombatBrain.disengage(living);
            net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatBrain.disengage(living);
        } else {
            NpcKiAim.cancel(entity.getUUID());
            NpcChargeMoves.forget(entity.getUUID());
            NpcHakai.forget(entity.getUUID());
        }
        NpcTransformSystem.cancel(entity.getUUID());
        NpcStrikeDispatcher.cancel(entity.getUUID());
        NpcFlightBridge.forget(entity.getUUID());
        NpcAggroBridge.forget(entity.getUUID());
        NpcTargetKeeper.forget(entity.getUUID());
        NpcCombatMoves.forget(entity.getUUID());
        NpcCombatBrain.forget(entity.getUUID());
        net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatBrain.forget(entity.getUUID());
        NpcMeleeDamage.clearAnimation(entity.getUUID());
        NpcKiAim.clearHardLock(entity.getUUID());
        NpcEntityLookup.forget(entity.getUUID());
        PROFILED.remove(entity.getUUID());
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason != Entity.RemovalReason.UNLOADED_TO_CHUNK
                && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER
                && reason != Entity.RemovalReason.CHANGED_DIMENSION) {
            RESPAWN_DEADLINES.remove(entity.getUUID());
            NpcKiCooldowns.clear(entity.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        NpcKiCooldowns.tick();
        int serverTick = event.getServer().getTickCount();
        PROFILED.entrySet().removeIf(entry -> {
            LivingEntity npc = entry.getValue();
            if (npc == null || npc.isRemoved()) return true;
            if (npc.isAlive()) {
                // readCached, not read: none of these three mutate the profile, and read()
                // rebuilt every mastery/aura-style/technique/hair structure twice per NPC per
                // tick. The cache reparses whenever the stored tag is replaced, so the
                // second call still sees a descend triggered by the drain tick.
                NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
                NpcTargetKeeper.dropInvalidTarget(npc);
                NpcFormDrainSystem.tick(npc, profile, serverTick);
                profile = NpcCombatProfile.readCached(npc);
                // Both of these already no-op or only matter over many ticks, so stagger them
                // by entity id the way the aura sync below already is.
                if (serverTick % 4 == Math.floorMod(npc.getId(), 4)) {
                    NpcCounterpartSync.apply(npc, profile, false, false);
                }
                NpcResources.tick(npc, profile);
                NpcNegativeEffectPersistence.capture(npc);
                boolean aiEvading = npc instanceof Mob mob
                        && net.bullettrain.xenopixelsmod.npc.NpcAiGoals.isEvading(mob);
                if (profile.combatBrain && !aiEvading
                        && !net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour.brainYieldsToLeash(npc)) {
                    LivingEntity steerAt = NpcKiAim.hardLock(event.getServer(), npc);
                    LivingEntity senseLocked = null;
                    if (steerAt == null) {
                        senseLocked = NpcTargetKeeper.kiSenseLockedTarget(event.getServer(), npc);
                        steerAt = senseLocked;
                    }
                    if (steerAt == null) steerAt = npc instanceof Mob mob && mob.getTarget() != null
                            && mob.getTarget().isAlive()
                            ? mob.getTarget()
                            : NpcTargetKeeper.victimOf(event.getServer(), npc, serverTick);
                    steerAt = NpcTargetKeeper.fightTarget(npc, steerAt);
                    if (steerAt != null && npc instanceof Mob mob && mob.getTarget() != steerAt) {
                        mob.setTarget(steerAt);
                    }
                    if (profile.brainVersion != null && profile.brainVersion.usesSagaTree()) {
                        net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatBrain
                                .steer(npc, profile, steerAt);
                        net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatBrain
                                .tick(event.getServer(), npc, profile, steerAt, serverTick);
                    } else {
                        NpcCombatBrain.advance(npc, profile, serverTick);
                        NpcCombatBrain.steer(npc, profile, steerAt);
                    }
                    if (senseLocked != null && steerAt == senseLocked) {
                        NpcKiAim.trackKiSenseTarget(npc, senseLocked);
                    }
                } else if (net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatBrain
                        .directsFlight(npc.getUUID())) {
                    // Combat can be disabled or interrupted while airborne. Restore gravity and
                    // release the movement claim instead of leaving the NPC in permanent hover.
                    net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatBrain
                            .disengage(npc);
                }
                // Target retention and autonomous combat are the two most expensive things
                // here, so they run on their own staggered slot rather than every tick.
                if (!aiEvading && serverTick % 10 == Math.floorMod(npc.getId(), 10)) {
                    LivingEntity victim = NpcTargetKeeper.tick(
                            event.getServer(), npc, serverTick);
                    if (profile.combatBrain && (profile.brainVersion == null
                            || !profile.brainVersion.usesSagaTree())) {
                        NpcCombatBrain.tick(event.getServer(), npc, profile, victim, serverTick);
                    }
                }
                if (serverTick % 40 == Math.floorMod(npc.getId(), 40)) {
                    NpcAuraFx.sync(npc);
                }
            }
            return false;
        });
        if (RESPAWN_DEADLINES.isEmpty()) {
            return;
        }
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, Integer>> it = RESPAWN_DEADLINES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            LivingEntity npc = find(event.getServer(), entry.getKey());
            if (npc == null) {
                continue; // Chunk may be unloaded; retain the deadline until it is loaded again.
            }
            if (npc.isAlive()) {
                repairAi(npc);
                NpcAuraFx.sync(npc);
                it.remove();
                continue;
            }
            if (now < entry.getValue()) {
                continue; // Let CustomNPCs' native timer run first.
            }
            if (respawnType(npc) != 0 || !invokeReset(npc)) {
                it.remove();
                continue;
            }
            repairAi(npc);
            NpcNegativeEffectPersistence.restore(npc);
            NpcCounterpartSync.forceLifecycle(npc, NpcCombatProfile.read(npc));
            NpcAuraFx.sync(npc);
            it.remove();
        }
    }

    private static LivingEntity find(MinecraftServer server, UUID id) {
        return NpcEntityLookup.findLiving(server, id);
    }

    private static boolean isCustomNpc(Entity entity) {
        return NpcCounterpartSync.isCustomNpc(entity);
    }

    private static Object stats(LivingEntity npc) {
        for (String className : new String[] {
                "espi.mynpcs.entity.EntityNPCInterface",
                "noppes.npcs.entity.EntityNPCInterface"}) {
            try {
                Class<?> npcClass = Class.forName(className);
                if (npcClass.isInstance(npc)) {
                    return npcClass.getField("stats").get(npc);
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the other supported namespace.
            }
        }
        return null;
    }

    private static int respawnType(LivingEntity npc) {
        Object stats = stats(npc);
        if (stats == null) {
            return -1;
        }
        try {
            return (Integer) stats.getClass().getMethod("getRespawnType").invoke(stats);
        } catch (ReflectiveOperationException ignored) {
            return -1;
        }
    }

    private static int respawnSeconds(LivingEntity npc) {
        Object stats = stats(npc);
        if (stats == null) {
            return 1;
        }
        try {
            return (Integer) stats.getClass().getMethod("getRespawnTime").invoke(stats);
        } catch (ReflectiveOperationException ignored) {
            return 1;
        }
    }

    private static boolean invokeReset(LivingEntity npc) {
        for (String className : new String[] {
                "espi.mynpcs.entity.EntityNPCInterface",
                "noppes.npcs.entity.EntityNPCInterface"}) {
            try {
                Class<?> npcClass = Class.forName(className);
                if (npcClass.isInstance(npc)) {
                    npcClass.getMethod("reset").invoke(npc);
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the other supported namespace.
            }
        }
        return false;
    }
}
