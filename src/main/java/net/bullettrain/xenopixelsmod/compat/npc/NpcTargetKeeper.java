package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

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
 * <p>A hard lock ({@link NpcKiAim#hardLock}) always wins over a remembered victim.
 */
public final class NpcTargetKeeper {
    /** Ticks a remembered victim survives with no contact before it is forgotten. */
    private static final int MEMORY_TICKS = 200;

    private record Memory(UUID victim, int lastSeenTick) {}

    private static final Map<UUID, Memory> MEMORIES = new ConcurrentHashMap<>();

    private NpcTargetKeeper() {}

    /** Records who this NPC is fighting, refreshing the memory window. */
    public static void remember(LivingEntity npc, LivingEntity victim, int serverTick) {
        if (npc == null || victim == null || npc == victim) {
            return;
        }
        MEMORIES.put(npc.getUUID(), new Memory(victim.getUUID(), serverTick));
    }

    public static void forget(UUID npcId) {
        if (npcId != null) {
            MEMORIES.remove(npcId);
        }
    }

    /** The victim this NPC should be fighting, or null. Prefers a hard lock over memory. */
    public static LivingEntity victimOf(MinecraftServer server, LivingEntity npc, int serverTick) {
        if (npc == null) {
            return null;
        }
        LivingEntity locked = NpcKiAim.hardLock(server, npc);
        if (locked != null) {
            remember(npc, locked, serverTick);
            return locked;
        }
        Memory memory = MEMORIES.get(npc.getUUID());
        if (memory == null || serverTick - memory.lastSeenTick() > MEMORY_TICKS) {
            return null;
        }
        LivingEntity victim = NpcEntityLookup.findAlive(server, memory.victim());
        if (victim == null || victim == npc) {
            return null;
        }
        return victim;
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
        LivingEntity current = mob.getTarget();
        if (current != null && current.isAlive()) {
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
        return mob.getTarget();
    }
}
