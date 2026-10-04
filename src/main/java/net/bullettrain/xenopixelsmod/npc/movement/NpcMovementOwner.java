package net.bullettrain.xenopixelsmod.npc.movement;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is allowed to move this NPC right now.
 *
 * <p>Nothing here moves anything. It answers one question — may I steer this NPC? — and the callers
 * that already steer keep doing the steering. That is deliberate: rewriting five movement systems
 * into one would be a far larger change than the bug warrants, and the bug is not <em>how</em> any
 * of them moves an NPC. It is that they all do it at once.
 *
 * <p><b>The bug this exists for.</b> Five places write an NPC's position with no coordination:
 *
 * <ul>
 *   <li>{@code XenoNpcBehaviour.tickLeash} — {@code moveTo(home)} outside {@code leashRadius}
 *   <li>{@code XenoNpcBrainV5.returnHome} — {@code moveTo(home)} outside the <em>role</em> leash
 *   <li>{@code NpcPathWalker} — {@code moveTo(next point)}
 *   <li>{@code NpcCombatMoves} — {@code teleportTo} for chase, vanish, backstep, teleportAbove
 *   <li>{@code NpcSagaCombatBrain} — {@code setDeltaMovement} while flying
 * </ul>
 *
 * <p>Two of those were home leashes with <em>different radii pulling to the same spot</em>. One said
 * "close enough" while the other said "go home", every check, forever, and neither ever won. That
 * alone is a shudder, and it explains why the same symptom appeared standing idle, wedged in a
 * corner, mid-fight and on a patrol route: the disagreement did not care what the NPC was doing.
 * The second of those two has since been retired outright; this arbitrates the rest.
 *
 * <p><b>State is transient, and must be.</b> "Who is steering right now" is meaningless across a
 * restart — the same mistake {@code XenoNpcRespawnData} exists to remember about tick counts. None
 * of it is written to disk.
 *
 * <p>The decisions live in {@link NpcMovementLedger}, which knows nothing about entities. This class
 * is the entity-facing skin over it: it exists so the priority rules can be tested without a level,
 * a navigation and a loaded world.
 */
public final class NpcMovementOwner {

    /**
     * Who may steer, most urgent first.
     *
     * <p>Ordinal <em>is</em> the priority, so the order of these constants is the behaviour. It
     * never crosses the wire and is never saved, so unlike the store categories this one may be
     * reordered — but only deliberately, because reordering changes which system wins.
     */
    public enum Claim {
        /** An authored scene. An operator said where the NPC goes; nothing overrides that. */
        SCENE,
        /** A fight. Chases, vanishes and backsteps must land where the brain decided. */
        COMBAT,
        /** Following a named NPC yields to combat and scenes, but outranks idle patrols. */
        FOLLOW,
        /** A patrol route. An authored path outranks an automatic pull home. */
        PATROL,
        /** The leash home. The lowest thing that actively moves an NPC. */
        LEASH,
        /** Idle wandering. Yields to everything, including the leash. */
        STROLL,
    }

    private static final Map<UUID, NpcMovementLedger> LEDGERS = new ConcurrentHashMap<>();

    private NpcMovementOwner() {
    }

    /**
     * Asks to steer this NPC, and takes the claim when allowed.
     *
     * <p>Granted when the NPC is unclaimed, when the holder's claim has expired, when the caller
     * already holds it, or when the caller outranks the holder. Refused only while something more
     * urgent is actively steering — which is the point: a leash that cannot interrupt a chase cannot
     * drag an NPC out of the middle of a fight.
     *
     * <p>Answers true on the client without recording anything. The client has no authority over an
     * NPC's position and never legitimately asks; answering true keeps a caller that forgot its side
     * check from silently doing nothing on the server, where it matters.
     */
    public static boolean claim(LivingEntity npc, Claim claim) {
        if (npc == null || claim == null) {
            return false;
        }
        if (npc.level().isClientSide()) {
            return true;
        }
        return ledger(npc).claim(claim, npc.level().getGameTime());
    }

    /**
     * Gives up the claim, if this caller holds it.
     *
     * <p>Ignores a release from somebody who does not hold it: a system that finishes long after
     * being outranked must not free the claim out from under whoever took it.
     */
    public static void release(LivingEntity npc, Claim claim) {
        if (npc == null || claim == null || npc.level().isClientSide()) {
            return;
        }
        NpcMovementLedger ledger = LEDGERS.get(npc.getUUID());
        if (ledger != null) {
            ledger.release(claim);
        }
    }

    /** Who is steering, or null. For tests, and for the report the wand prints. */
    public static Claim current(LivingEntity npc) {
        if (npc == null || npc.level().isClientSide()) {
            return null;
        }
        NpcMovementLedger ledger = LEDGERS.get(npc.getUUID());
        return ledger == null ? null : ledger.current(npc.level().getGameTime());
    }

    /**
     * Whether this NPC is getting anywhere, called once per re-issue.
     *
     * <p>The second half of the fix. A claim stops two systems fighting each other; this stops one
     * system fighting a wall. {@code tickLeash} re-issued {@code moveTo(home)} every twenty ticks
     * with nothing checking whether the previous one had achieved anything, so an NPC wedged behind
     * a fence post lurched at it forever.
     *
     * @return true while the NPC is still making progress; false once it has failed
     *         {@link NpcMovementLedger#STUCK_STRIKES} times running, meaning the caller should stop
     *         re-issuing
     */
    public static boolean progressing(LivingEntity npc) {
        if (npc == null || npc.level().isClientSide()) {
            return true;
        }
        return ledger(npc).progressing(npc.getX(), npc.getY(), npc.getZ());
    }

    /** Whether this NPC has given up on where it was going. */
    public static boolean stuck(LivingEntity npc) {
        if (npc == null || npc.level().isClientSide()) {
            return false;
        }
        NpcMovementLedger ledger = LEDGERS.get(npc.getUUID());
        return ledger != null && ledger.stuck();
    }

    /**
     * Forgets that an NPC was stuck.
     *
     * <p>Called when where it is going changes — a teleport home, a new patrol point, a fight
     * starting. Without it an NPC that got wedged once would refuse to re-path for the rest of its
     * life.
     */
    public static void clearProgress(LivingEntity npc) {
        if (npc == null || npc.level().isClientSide()) {
            return;
        }
        NpcMovementLedger ledger = LEDGERS.get(npc.getUUID());
        if (ledger != null) {
            ledger.clearProgress();
        }
    }

    /**
     * Drops everything remembered about an NPC.
     *
     * <p>Called when one is removed — the same housekeeping {@code XenoNpcSpeech.forget} and
     * {@code NpcSocialBehaviour.forget} do, so a long-running server does not accumulate an entry
     * per NPC that ever existed.
     */
    public static void forget(UUID npcId) {
        if (npcId != null) {
            LEDGERS.remove(npcId);
        }
    }

    /** Clears everything. For a world unload, and for tests. */
    public static void clearAll() {
        LEDGERS.clear();
    }

    private static NpcMovementLedger ledger(LivingEntity npc) {
        return LEDGERS.computeIfAbsent(npc.getUUID(), id -> new NpcMovementLedger());
    }
}
