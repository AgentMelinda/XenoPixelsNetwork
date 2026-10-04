package net.bullettrain.xenopixelsmod.compat.npc;

import java.util.UUID;

/**
 * Whether either combat brain currently owns an NPC's flight.
 *
 * <p>The entity-side systems (V5 role brain, leash, follower job, leap goal, aiStep) used to ask
 * only the saga brain. The legacy {@link NpcCombatBrain} claims flight too, so for NPCs on that
 * brain the navigator kept being handed ground paths mid-flight: MoveControl turned the body toward
 * a path node for a tick while the brain's velocity pointed at the target, which read as flying
 * sideways and as the body twitching between two headings.
 */
public final class NpcFlightOwnership {
    private NpcFlightOwnership() {}

    public static boolean brainDirectsFlight(UUID npcId) {
        return NpcCombatBrain.directsFlight(npcId)
                || net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatBrain
                        .directsFlight(npcId);
    }
}
