package net.bullettrain.xenopixelsmod.npc.job;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcJob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The Guard job: walk a route, and answer what wanders into it.
 *
 * <p>The second job with anything behind it, after the Bard, and the one that gives
 * {@link net.bullettrain.xenopixelsmod.npc.path.NpcPath} a reader — the route and the thing that
 * walks it ship together rather than leaving a list nothing consumes.
 *
 * <p><b>Distinct from the GUARD role.</b> The role already fights; this is a <em>job</em>, so it
 * can sit on any role — a Trader that patrols its stall and objects to zombies. That separation is
 * the point of the job axis existing at all.
 *
 * <p><b>Scoped deliberately.</b> The reference also has an Available / Current Targets move-list,
 * a per-entity-type allow-list. That is a larger surface and is not built; the editor says so in
 * plain text rather than showing a disabled control.
 */
public final class NpcGuardJob {
    /** Only clear a target that this job acquired; retaliation belongs to the combat brain. */
    private static final Map<XenoNpcEntity, UUID> JOB_TARGETS = new WeakHashMap<>();

    /** How often it looks around. A guard does not need to re-scan twenty times a second. */
    public static final int CHECK_STRIDE = 20;

    /** How far it notices something. Matches the reach the combat brain already fights at. */
    public static final double SIGHT = 16.0;

    private NpcGuardJob() {
    }

    /**
     * One NPC's turn.
     *
     * <p>Called every tick from {@code aiStep} and returns immediately for the overwhelming
     * majority of NPCs, which have no job.
     */
    public static void tick(XenoNpcEntity npc, NpcCombatProfile profile) {
        if (npc == null || profile == null) {
            return;
        }
        if (!profile.jobEnabled || XenoNpcJob.byId(profile.job) != XenoNpcJob.GUARD) {
            UUID acquired = JOB_TARGETS.remove(npc);
            if (acquired != null && npc.getTarget() != null
                    && acquired.equals(npc.getTarget().getUUID())) {
                npc.setTarget(null);
            }
            return;
        }
        // Patrolling is not this method's job - NpcPathWalker already runs for every NPC with a
        // route, whatever its job. A Guard with no route simply stands its post.
        if (npc.getTarget() != null && stillValid(npc.getTarget(), profile)) {
            return;
        }
        if ((npc.tickCount + npc.getId()) % CHECK_STRIDE != 0) {
            return;
        }
        LivingEntity found = search(npc, profile);
        if (found != null) {
            npc.setTarget(found);
            JOB_TARGETS.put(npc, found.getUUID());
        }
    }

    /**
     * Whether the thing this NPC is already fighting is still something it should fight.
     *
     * <p>Re-checked because the toggles can change mid-fight from the editor, and because a target
     * can die or leave. Without it a guard would keep chasing a cow after Attack Animals was
     * turned off.
     */
    static boolean stillValid(LivingEntity target, NpcCombatProfile profile) {
        return target.isAlive() && wants(target, profile);
    }

    private static LivingEntity search(XenoNpcEntity npc, NpcCombatProfile profile) {
        if (!profile.guardAnimals && !profile.guardMonsters && !profile.guardCreepers) {
            // Every toggle off is a guard that guards nothing. Skipping the scan entirely keeps
            // that free rather than walking the entity list to reject everything in it.
            return null;
        }
        AABB box = npc.getBoundingBox().inflate(SIGHT);
        List<LivingEntity> nearby = npc.level().getEntitiesOfClass(LivingEntity.class, box,
                candidate -> candidate != npc && candidate.isAlive() && wants(candidate, profile));
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : nearby) {
            double distance = npc.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Whether this guard answers that kind of thing.
     *
     * <p>Creepers are checked before the general monster test, because they are the one a player
     * most often wants handled separately — a guard that ignores skeletons but blows the creeper
     * away from the door is a real configuration, and the reference gives it its own toggle.
     *
     * <p>Players are never targeted here. A guard that attacked its owner on sight would be a
     * surprising thing for a toggle called "Attack Monsters" to do; hostility toward players is
     * the faction system's decision, not this one's.
     */
    static boolean wants(LivingEntity candidate, NpcCombatProfile profile) {
        if (candidate instanceof Player || candidate instanceof XenoNpcEntity) {
            return false;
        }
        if (candidate instanceof Creeper) {
            return profile.guardCreepers;
        }
        if (candidate instanceof Enemy) {
            return profile.guardMonsters;
        }
        if (candidate instanceof Animal) {
            return profile.guardAnimals;
        }
        return false;
    }
}
