package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge;
import net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops profiled NPCs from forgetting who they were fighting.
 *
 * <p>CustomNPCs drops a target from {@code EntityAIAttackTarget.canContinueToUse} for three
 * reasons that all fire during an ordinary fight:
 *
 * <ul>
 *   <li>the target left {@code stats.aggroRange} for even one tick — including a vanish, a dash,
 *       or a knockback;</li>
 *   <li>{@code isWithinRestriction} failed, i.e. the target strayed more than
 *       {@code max(aggroRange * 2, 64)} blocks from wherever the NPC happened to be standing when
 *       the goal started, so a running fight eventually "leashes" the NPC home;</li>
 *   <li>the target briefly stopped being reachable.</li>
 * </ul>
 *
 * <p>{@link NpcAggroBridge} widens the ranges, but a drop that happens inside those ranges still
 * needs undoing — this remembers the victim and re-issues {@code setTarget} while it is still in
 * effective range and alive. {@code setTarget} fires CustomNPCs' own {@code TargetEvent}, so
 * scripts keep their veto.
 *
 * <p>A hard lock ({@link NpcKiAim#hardLock}) always wins. The last living attacker is preferred
 * over a stale chase target so ki aims at who just hit. A Zanzoken afterimage is kept only
 * while this NPC is currently fooled by that ring (Brain page chance); otherwise memory
 * follows the fighter who made it.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcTargetKeeper {
    /** Ticks a remembered victim survives with no contact before it is forgotten. */
    private static final int MEMORY_TICKS = 200;

    private record Memory(UUID victim, int lastSeenTick) {}

    private static final Map<UUID, Memory> MEMORIES = new ConcurrentHashMap<>();
    static final Map<UUID, Memory> LAST_ATTACKERS = new ConcurrentHashMap<>();
    /** A deliberate Ki Sense acquisition, separate from ordinary ten-second retaliation memory. */
    private static final Map<UUID, UUID> KI_SENSE_LOCKS = new ConcurrentHashMap<>();

    private NpcTargetKeeper() {}

    /** Creative and spectator players must stop being combat targets immediately. */
    public static boolean isCombatTarget(LivingEntity target) {
        return target != null && target.isAlive()
                && (!(target instanceof Player player)
                || (!player.isCreative() && !player.isSpectator()));
    }

    /** Called each tick so a mode switch cannot survive until the next staggered target pass. */
    public static void dropInvalidTarget(LivingEntity npc) {
        if (!(npc instanceof Mob mob)) return;
        LivingEntity target = mob.getTarget();
        if (target != null && !isCombatTarget(target)) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            forget(npc.getUUID());
            NpcKiAim.clearHardLock(npc);
        }
    }

    /** Records who this NPC is fighting, refreshing the memory window. */
    public static void remember(LivingEntity npc, LivingEntity victim, int serverTick) {
        LivingEntity fighter = realFighter(victim);
        if (npc == null || !isCombatTarget(fighter) || npc == fighter) {
            return;
        }
        MEMORIES.put(npc.getUUID(), new Memory(fighter.getUUID(), serverTick));
    }

    public static void forget(UUID npcId) {
        if (npcId != null) {
            MEMORIES.remove(npcId);
            LAST_ATTACKERS.remove(npcId);
            KI_SENSE_LOCKS.remove(npcId);
        }
    }

    /** True when {@code target} is the living attacker this NPC is currently retaliating against. */
    public static boolean isRetaliating(LivingEntity npc, LivingEntity target) {
        if (npc == null || target == null) {
            return false;
        }
        Memory memory = LAST_ATTACKERS.get(npc.getUUID());
        return memory != null
                && fresh(memory, serverTick(npc))
                && target.getUUID().equals(memory.victim());
    }

    static boolean preferLastAttacker(boolean lastAlive, boolean lastInRange, boolean memoryFresh) {
        return lastAlive && lastInRange && memoryFresh;
    }

    static boolean shouldUseNativeKiSenseLock(boolean nativeXeno, boolean retaliates,
                                               boolean kiSenseActive, boolean lockEnabled) {
        return nativeXeno && retaliates && kiSenseActive && lockEnabled;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !NpcCombatProfile.hasProfile(victim)) {
            return;
        }
        if (victim instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity
                && NpcCombatProfile.readCached(victim).aiOnFoundEnemy
                        != net.bullettrain.xenopixelsmod.npc.NpcOnFoundEnemy.RETALIATE) {
            forget(victim.getUUID());
            if (victim instanceof Mob mob) mob.setTarget(null);
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof LivingEntity attacker) || attacker == victim || !attacker.isAlive()) {
            return;
        }
        attacker = realFighter(attacker);
        if (attacker == null || attacker == victim || !isCombatTarget(attacker)) {
            return;
        }
        int tick = serverTick(victim);
        remember(victim, attacker, tick);
        LAST_ATTACKERS.put(victim.getUUID(), new Memory(attacker.getUUID(), tick));
        NpcCombatProfile profile = NpcCombatProfile.readCached(victim);
        if (kiSenseCanAcquire(victim, attacker, profile)) {
            // DMZ's Z key first raycasts from the player's view, then remembers that entity.
            // The NPC already knows the actual attacker from the damage event: face them and
            // acquire that entity, without pretending a server NPC can press a client key.
            NpcKiAim.applyLook(victim, NpcBrainKiRotation.targetYaw(
                    attacker.getX() - victim.getX(), attacker.getZ() - victim.getZ()), 0.0f);
            KI_SENSE_LOCKS.put(victim.getUUID(), attacker.getUUID());
        } else {
            KI_SENSE_LOCKS.remove(victim.getUUID());
        }
        if (victim instanceof Mob mob && NpcAggroBridge.inEffectiveRange(victim, attacker)) {
            mob.setTarget(attacker);
        }
    }

    /**
     * Makes {@code target} this NPC's retaliation target as if it had just been hit by it, for
     * systems that start a fight on purpose (a quest whose kill target hunts the claimant). Same
     * memory and targeting as the damage path; no Ki Sense lock, since nothing struck it.
     */
    public static void provoke(LivingEntity npc, LivingEntity target) {
        if (npc == null || target == null || npc.level().isClientSide() || npc == target
                || !npc.isAlive() || !isCombatTarget(target)) {
            return;
        }
        int tick = serverTick(npc);
        remember(npc, target, tick);
        LAST_ATTACKERS.put(npc.getUUID(), new Memory(target.getUUID(), tick));
        if (npc instanceof Mob mob) {
            mob.setTarget(target);
        }
    }

    /** Ends a provoked fight with {@code target}, leaving any other memory alone. */
    public static void calmIfTargeting(LivingEntity npc, LivingEntity target) {
        if (npc == null || target == null) {
            return;
        }
        Memory memory = LAST_ATTACKERS.get(npc.getUUID());
        if (memory != null && target.getUUID().equals(memory.victim())) {
            forget(npc.getUUID());
        }
        if (npc instanceof Mob mob && mob.getTarget() == target) {
            mob.setTarget(null);
        }
    }

    /** The victim this NPC should be fighting, or null. Prefers a hard lock over memory. */
    public static LivingEntity victimOf(MinecraftServer server, LivingEntity npc, int serverTick) {
        if (npc == null) {
            return null;
        }
        LivingEntity locked = NpcKiAim.hardLock(server, npc);
        if (isCombatTarget(locked)) {
            locked = fightTarget(npc, locked);
            remember(npc, locked, serverTick);
            return locked;
        }
        locked = kiSenseLockedTarget(server, npc);
        if (isCombatTarget(locked)) {
            remember(npc, locked, serverTick);
            return locked;
        }
        LivingEntity retaliator = lastAttacker(server, npc, serverTick);
        if (isCombatTarget(retaliator)) {
            remember(npc, retaliator, serverTick);
            return retaliator;
        }
        Memory memory = MEMORIES.get(npc.getUUID());
        if (memory == null || !fresh(memory, serverTick)) {
            return null;
        }
        LivingEntity victim = NpcEntityLookup.findAlive(server, memory.victim());
        if (!isCombatTarget(victim) || victim == npc) {
            return null;
        }
        return fightTarget(npc, victim);
    }

    /**
     * Keeps this NPC on its target. Called on the staggered profile tick, not every tick.
     *
     * @return the target the NPC should now be fighting, or null when it has none
     */
    public static LivingEntity tick(MinecraftServer server, LivingEntity npc, int serverTick) {
        if (!(npc instanceof Mob mob)) {
            return null;
        }
        dropInvalidTarget(npc);
        LivingEntity scriptLocked = NpcKiAim.hardLock(server, npc);
        if (isCombatTarget(scriptLocked)) {
            if (mob.getTarget() != scriptLocked) mob.setTarget(scriptLocked);
            return scriptLocked;
        }
        LivingEntity senseLocked = kiSenseLockedTarget(server, npc);
        if (isCombatTarget(senseLocked)) {
            if (mob.getTarget() != senseLocked) mob.setTarget(senseLocked);
            return senseLocked;
        }
        LivingEntity retaliator = lastAttacker(server, npc, serverTick);
        if (preferLastAttacker(isCombatTarget(retaliator),
                retaliator != null && NpcAggroBridge.inEffectiveRange(npc, retaliator),
                retaliator != null)) {
            remember(npc, retaliator, serverTick);
            if (mob.getTarget() != retaliator) {
                mob.setTarget(retaliator);
            }
            LivingEntity stuck = fightTarget(npc, mob.getTarget());
            if (stuck != null && stuck != mob.getTarget() && stuck.isAlive()) {
                mob.setTarget(stuck);
            }
            return stuck != null && stuck.isAlive() ? stuck : retaliator;
        }
        LivingEntity current = fightTarget(npc, mob.getTarget());
        if (current != null && current.isAlive()) {
            if (current != mob.getTarget()) {
                mob.setTarget(current);
            }
            remember(npc, current, serverTick);
            return current;
        }
        LivingEntity victim = victimOf(server, npc, serverTick);
        if (victim == null) {
            return null;
        }
        // Only re-target while the victim is genuinely still in reach; otherwise a dropped
        // target would be re-latched forever and the NPC would never go home.
        if (!NpcAggroBridge.inEffectiveRange(npc, victim)) {
            return null;
        }
        mob.setTarget(victim);
        // setTarget can be vetoed by CustomNPCs' TargetEvent, so report what actually stuck.
        LivingEntity stuck = fightTarget(npc, mob.getTarget());
        return stuck != null && stuck.isAlive() ? stuck : victim;
    }

    private static LivingEntity lastAttacker(MinecraftServer server, LivingEntity npc, int serverTick) {
        if (npc instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity) {
            NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
            boolean retaliates = profile.aiOnFoundEnemy
                    == net.bullettrain.xenopixelsmod.npc.NpcOnFoundEnemy.RETALIATE;
            if (!retaliates) {
                return null;
            }
        }
        Memory memory = LAST_ATTACKERS.get(npc.getUUID());
        if (memory == null || !fresh(memory, serverTick)) {
            return null;
        }
        LivingEntity attacker = NpcEntityLookup.findAlive(server, memory.victim());
        if (!isCombatTarget(attacker) || attacker == npc) {
            return null;
        }
        attacker = realFighter(attacker);
        return !isCombatTarget(attacker) || attacker == npc ? null : attacker;
    }

    /** The live Ki Sense target, cleared when the skill, range, sight, or retaliation mode fails. */
    public static LivingEntity kiSenseLockedTarget(MinecraftServer server, LivingEntity npc) {
        if (server == null || npc == null) return null;
        UUID id = KI_SENSE_LOCKS.get(npc.getUUID());
        if (id == null) return null;
        LivingEntity target = NpcEntityLookup.findAlive(server, id);
        if (!kiSenseCanAcquire(npc, target, NpcCombatProfile.readCached(npc))) {
            KI_SENSE_LOCKS.remove(npc.getUUID(), id);
            return null;
        }
        return target;
    }

    /** True only for the current valid native Ki Sense lock, never an ordinary retaliator. */
    public static boolean isKiSenseLockedOn(LivingEntity npc, LivingEntity target) {
        if (npc == null || target == null
                || !target.getUUID().equals(KI_SENSE_LOCKS.get(npc.getUUID()))) return false;
        UUID hardLock = NpcKiAim.hardLockId(npc);
        return (hardLock == null || hardLock.equals(target.getUUID()))
                && kiSenseCanAcquire(npc, target, NpcCombatProfile.readCached(npc));
    }

    private static boolean kiSenseCanAcquire(LivingEntity npc, LivingEntity target,
                                             NpcCombatProfile profile) {
        if (npc == null || !isCombatTarget(target) || target == npc
                || npc.level() != target.level() || target.isInvisible() || !target.isPickable()
                || profile == null) return false;
        boolean enabled = shouldUseNativeKiSenseLock(
                npc instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity,
                profile.aiOnFoundEnemy == net.bullettrain.xenopixelsmod.npc.NpcOnFoundEnemy.RETALIATE,
                profile.skills.isActive(NpcSkillSet.KI_SENSE), profile.kiSenseLockOnRetaliator);
        int level = profile.skills.level(NpcSkillSet.KI_SENSE);
        return enabled && level > 0 && npc.distanceTo(target) <= 15.0 + 5.0 * level
                && npc.hasLineOfSight(target);
    }

    /**
     * The body this NPC should actually fight.
     *
     * <p>A Zanzoken afterimage is a decoy unless this NPC failed the Brain-page roll and is
     * currently fooled by that ring. Multi-Form copies, training dummies, and ordinary entities
     * pass through.
     */
    public static LivingEntity fightTarget(LivingEntity npc, LivingEntity target) {
        if (!isCombatTarget(target)) return null;
        if (!(target instanceof XenoCloneEntity clone)
                || clone.slot() != XenoCloneEntity.SLOT_STATIONARY) {
            return target;
        }
        if (net.bullettrain.xenopixelsmod.combat.ZanzokenConfusion.isFooledBy(npc, clone)) {
            return target;
        }
        LivingEntity fighter = realFighter(target);
        return isCombatTarget(fighter) ? fighter : null;
    }

    /**
     * The fighter who made a Zanzoken afterimage, or {@code target} itself.
     */
    public static LivingEntity realFighter(LivingEntity target) {
        if (!(target instanceof XenoCloneEntity clone)
                || clone.slot() != XenoCloneEntity.SLOT_STATIONARY) {
            return target;
        }
        LivingEntity owner = CloneCombatBridge.owner(clone);
        return owner != null && owner.isAlive() && owner != clone ? owner : target;
    }

    /** True when {@code target} is a standing Zanzoken afterimage rather than a real fighter. */
    static boolean isZanzokenDecoy(boolean clone, int slot) {
        return clone && slot == XenoCloneEntity.SLOT_STATIONARY;
    }

    private static boolean fresh(Memory memory, int serverTick) {
        return memory != null && serverTick - memory.lastSeenTick() <= MEMORY_TICKS;
    }

    private static int serverTick(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level && level.getServer() != null) {
            return level.getServer().getTickCount();
        }
        return entity.tickCount;
    }
}
