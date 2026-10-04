package net.bullettrain.xenopixelsmod.npc.brain;

import net.bullettrain.xenopixelsmod.compat.npc.brain.v4.NpcBrainBudget;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRoleBehaviour;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRoleDefinition;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRoleDefinitions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class XenoNpcBrainV5 {
    private static final NpcBrainBudget BUDGET = new NpcBrainBudget(32);

    private XenoNpcBrainV5() {}

    public static void tick(XenoNpcEntity npc) {
        if (net.bullettrain.xenopixelsmod.compat.npc.NpcFlightOwnership.brainDirectsFlight(npc.getUUID())) {
            npc.getNavigation().stop();
            npc.setNoGravity(true);
            return;
        }
        int tick = npc.tickCount;
        LivingEntity target = npc.getTarget();
        if (target != null && !net.bullettrain.xenopixelsmod.compat.npc.NpcTargetKeeper.isCombatTarget(target)) {
            net.bullettrain.xenopixelsmod.compat.npc.NpcTargetKeeper.dropInvalidTarget(npc);
            target = null;
        }
        XenoNpcRoleDefinition definition = XenoNpcRoleDefinitions.get(npc.role());
        int interval = target != null
                ? definition.combatDecisionTicks()
                : npc.getNavigation().isDone() ? definition.idleDecisionTicks() : definition.activeDecisionTicks();
        if (tick % interval != Math.floorMod(npc.getId(), interval) || !BUDGET.tryAcquire(tick)) return;

        XenoNpcRole role = npc.role();
        if (target == null || !target.isAlive()) {
            // Out of a fight: no sprint boost, no run pose.
            net.bullettrain.xenopixelsmod.compat.npc.NpcSprintSkill.apply(npc, null, false);
        }

        // A trader or a quest giver never chases. They exist to be talked to, and one that wandered
        // off after a zombie is not where the player left it.
        if (target != null && target.isAlive() && XenoNpcRoleBehaviour.fights(role)) {
            // Walk in until the target is inside melee reach. This used a fixed 3-block cutoff,
            // but reach defaults to about one block (npcAttackStartRadius), so a target standing
            // 1.5-3 blocks away was out of reach and never approached: the NPC hit once, the
            // player stepped back, and it stood still.
            boolean chasing = !net.bullettrain.xenopixelsmod.compat.npc.NpcCombatRanges.withinMelee(npc, target);
            net.bullettrain.xenopixelsmod.compat.npc.NpcSprintSkill.apply(npc,
                    net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.readCached(npc), chasing);
            if (chasing) {
                // Use the same height-gap/ledge decision as V9. Direct moveTo re-centres a
                // path in the player's column even when walking cannot reach that floor.
                net.bullettrain.xenopixelsmod.compat.npc.NpcLedgeApproach.apply(npc, target, 1.05);
            } else {
                npc.getNavigation().stop();
            }
            return;
        }
        if (target != null && !XenoNpcRoleBehaviour.fights(role)) {
            // Drop it rather than stand facing something it will never attack.
            npc.setTarget(null);
        }

        if (XenoNpcRoleBehaviour.followsOwner(role)
                && !net.bullettrain.xenopixelsmod.npc.job.NpcFollowerJob.active(
                        net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.read(npc))
                && npc.npcData().owner() != null
                && npc.level().getPlayerByUUID(npc.npcData().owner()) instanceof Player owner
                && npc.distanceToSqr(owner) > 36.0) {
            npc.getNavigation().moveTo(owner, 1.0);
            return;
        }

        // Going home is XenoNpcBehaviour.tickLeash's job alone now - see the note below.
    }

    /**
     * The home pull that used to live here, and why it does not any more.
     *
     * <p>This walked a strayed NPC back to its spawn point against the radius its <em>role</em>
     * implies. {@code XenoNpcBehaviour.tickLeash} did the same job against the radius the
     * <em>editor</em> sets, on a different schedule. Two pulls to the same spot, disagreeing about
     * when to stop, is what made NPCs shudder: whenever the two radii differed, one said "close
     * enough" on every check while the other said "go home", and neither ever won. It showed up
     * standing idle, wedged, mid-fight and on patrol because the disagreement did not care what the
     * NPC was doing - and this one never checked {@code NpcPathWalker.patrolling}, so it fought
     * authored routes as well.
     *
     * <p>The role's leash still means what it meant. {@code XenoNpcBehaviour.leashRadiusFor} falls
     * back to it for an NPC that has never had a radius set, so a guard is still anchored to its
     * post - by one system rather than two.
     */
}
