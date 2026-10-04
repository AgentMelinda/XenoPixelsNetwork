package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The AI page's switches, and the vanilla behaviour behind each one.
 *
 * <p><b>Gates, not conditional registration.</b> {@code registerGoals} runs once when the entity is
 * constructed, so a goal left out there could never come back when its switch is turned on. Every
 * goal below is always added and refuses to start while its switch is off — the shape
 * {@link StayHomeStrollGoal} established for exactly this reason.
 *
 * <p>Each reads the profile on every check rather than caching, so the editor takes effect on the
 * next attempt instead of on the next reload.
 *
 * <p>All rows on the AI page have a runtime consumer. Profile values are read at decision time so
 * editing a setting takes effect without recreating the NPC.
 */
public final class NpcAiGoals {

    /** How hard a water tile is to path through when the NPC is told to avoid it. */
    private static final float WATER_AVOID_MALUS = 8.0f;
    private static final int RESPONSE_TICKS = 100;
    private static final Map<UUID, Integer> EVADING_UNTIL = new ConcurrentHashMap<>();

    private NpcAiGoals() {
    }

    /** Floats rather than sinking, unless told otherwise. */
    public static final class SwimGoal extends FloatGoal {
        private final Mob mob;

        public SwimGoal(Mob mob) {
            super(mob);
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return NpcCombatProfile.readCached(mob).aiCanSwim && super.canUse();
        }
    }

    /** Opens a door and walks through, the way a villager does. */
    public static final class DoorOpenGoal extends OpenDoorGoal {
        private final Mob mob;

        public DoorOpenGoal(Mob mob) {
            // Closes it again behind itself, which is what makes this read as "opens doors" rather
            // than "leaves every door in the village standing open".
            super(mob, true);
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return NpcCombatProfile.readCached(mob).aiDoorInteract == NpcDoorInteract.OPEN
                    && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return NpcCombatProfile.readCached(mob).aiDoorInteract == NpcDoorInteract.OPEN
                    && super.canContinueToUse();
        }
    }

    /** Smashes a door, the way a zombie does. */
    public static final class DoorBreakGoal extends BreakDoorGoal {
        private final Mob mob;

        public DoorBreakGoal(Mob mob) {
            // Vanilla's own difficulty rule: breaking doors is a Hard-only behaviour, and taking
            // that from BreakDoorGoal's own predicate rather than inventing one keeps an NPC
            // consistent with every other door-breaking mob in the world.
            super(mob, difficulty -> difficulty == net.minecraft.world.Difficulty.HARD);
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return NpcCombatProfile.readCached(mob).aiDoorInteract == NpcDoorInteract.BREAK
                    && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return NpcCombatProfile.readCached(mob).aiDoorInteract == NpcDoorInteract.BREAK
                    && super.canContinueToUse();
        }
    }

    /** Pounces the last stretch at a target, the way a wolf does. */
    public static final class LeapGoal extends LeapAtTargetGoal {
        private final Mob mob;

        public LeapGoal(Mob mob) {
            super(mob, 0.4f);
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return NpcCombatProfile.readCached(mob).aiLeapAtTarget
                    && !net.bullettrain.xenopixelsmod.compat.npc.NpcFlightOwnership.brainDirectsFlight(mob.getUUID())
                    && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return NpcCombatProfile.readCached(mob).aiLeapAtTarget
                    && !net.bullettrain.xenopixelsmod.compat.npc.NpcFlightOwnership.brainDirectsFlight(mob.getUUID())
                    && super.canContinueToUse();
        }
    }

    /** The shelter search used by both Darkness and Sunlight modes. */
    public static final class ShelterGoal extends Goal {
        private final PathfinderMob mob;
        private BlockPos destination;
        private int nextSearchTick;

        public ShelterGoal(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            NpcShelterFrom mode = NpcCombatProfile.readCached(mob).aiShelterFrom;
            if (mode == null || mode == NpcShelterFrom.DISABLED || !needsShelter(mode)) {
                return false;
            }
            if (mob.tickCount < nextSearchTick) return false;
            nextSearchTick = mob.tickCount + 40;
            destination = findShelter();
            return destination != null;
        }

        @Override
        public boolean canContinueToUse() {
            NpcShelterFrom mode = NpcCombatProfile.readCached(mob).aiShelterFrom;
            return mode != null && mode != NpcShelterFrom.DISABLED
                    && needsShelter(mode) && !mob.getNavigation().isDone();
        }

        @Override
        public void start() {
            moveToShelter();
        }

        @Override
        public void tick() {
            if (mob.getNavigation().isDone()) {
                destination = findShelter();
                moveToShelter();
                nextSearchTick = mob.tickCount + 40;
            }
        }

        private boolean needsShelter(NpcShelterFrom mode) {
            boolean exposed = mob.level().canSeeSky(mob.blockPosition());
            return exposed && (mode == NpcShelterFrom.DARKNESS
                    ? !mob.level().isDay() : mob.level().isDay());
        }

        private BlockPos findShelter() {
            BlockPos origin = mob.blockPosition();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int x = -10; x <= 10; x++) {
                for (int z = -10; z <= 10; z++) {
                    for (int y = -3; y <= 2; y++) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if (mob.level().canSeeSky(candidate)
                                || !mob.level().getBlockState(candidate).getCollisionShape(
                                        mob.level(), candidate).isEmpty()
                                || !mob.level().getBlockState(candidate.above()).getCollisionShape(
                                        mob.level(), candidate.above()).isEmpty()
                                || mob.level().getBlockState(candidate.below()).getCollisionShape(
                                        mob.level(), candidate.below()).isEmpty()) {
                            continue;
                        }
                        double distance = mob.distanceToSqr(Vec3.atBottomCenterOf(candidate));
                        if (distance < bestDistance) {
                            best = candidate;
                            bestDistance = distance;
                        }
                    }
                }
            }
            return best;
        }

        private void moveToShelter() {
            if (destination != null) {
                mob.getNavigation().moveTo(destination.getX() + 0.5, destination.getY(),
                        destination.getZ() + 0.5, 1.0);
            }
        }
    }

    /** Uses the MyNPCs modes to respond to a new hit without making the attacker a combat target. */
    public static final class DamageResponseGoal extends Goal {
        private final PathfinderMob mob;
        private int handledTimestamp;
        private int responseUntil;
        private Vec3 destination;

        public DamageResponseGoal(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            NpcOnFoundEnemy mode = NpcCombatProfile.readCached(mob).aiOnFoundEnemy;
            if ((mode != NpcOnFoundEnemy.PANIC && mode != NpcOnFoundEnemy.RETREAT)
                    || mob.getLastHurtByMob() == null
                    || mob.getLastHurtByMobTimestamp() == handledTimestamp) {
                return false;
            }
            destination = fleePosition(mode, mob.getLastHurtByMob());
            return destination != null;
        }

        @Override
        public boolean canContinueToUse() {
            NpcOnFoundEnemy mode = NpcCombatProfile.readCached(mob).aiOnFoundEnemy;
            return (mode == NpcOnFoundEnemy.PANIC || mode == NpcOnFoundEnemy.RETREAT)
                    && mob.tickCount < responseUntil;
        }

        @Override
        public void start() {
            handledTimestamp = mob.getLastHurtByMobTimestamp();
            responseUntil = mob.tickCount + RESPONSE_TICKS;
            mob.setTarget(null);
            EVADING_UNTIL.put(mob.getUUID(), responseUntil);
            moveAway();
        }

        @Override
        public void tick() {
            if (mob.getNavigation().isDone() && mob.tickCount < responseUntil) {
                destination = fleePosition(NpcCombatProfile.readCached(mob).aiOnFoundEnemy,
                        mob.getLastHurtByMob());
                moveAway();
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
            EVADING_UNTIL.remove(mob.getUUID());
        }

        private Vec3 fleePosition(NpcOnFoundEnemy mode, LivingEntity attacker) {
            if (mode == NpcOnFoundEnemy.RETREAT && attacker != null && attacker.isAlive()) {
                return DefaultRandomPos.getPosAway(mob, 16, 7, attacker.position());
            }
            return DefaultRandomPos.getPos(mob, 16, 7);
        }

        private void moveAway() {
            if (destination != null) {
                double speed = NpcCombatProfile.readCached(mob).aiOnFoundEnemy
                        == NpcOnFoundEnemy.PANIC ? 1.2 : 1.0;
                mob.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
            }
        }
    }

    /** Target goal whose sight and invisibility rules follow the NPC's live profile. */
    public static class ProfiledTargetGoal<T extends LivingEntity>
            extends NearestAttackableTargetGoal<T> {
        private final Mob owner;
        private final Predicate<LivingEntity> selector;

        public ProfiledTargetGoal(Mob mob, Class<T> targetType, int randomInterval,
                                  Predicate<LivingEntity> selector) {
            super(mob, targetType, randomInterval, true, false, selector);
            this.owner = mob;
            this.selector = selector;
        }

        @Override
        public boolean canUse() {
            return !isEvading(owner) && super.canUse();
        }

        @Override
        protected void findTarget() {
            NpcCombatProfile profile = NpcCombatProfile.readCached(owner);
            TargetingConditions conditions = TargetingConditions.forCombat()
                    .range(getFollowDistance()).selector(selector);
            if (!profile.aiMustSeeTarget) {
                conditions.ignoreLineOfSight();
            }
            if (profile.aiAttackInvisible) {
                conditions.ignoreInvisibilityTesting();
            }
            targetConditions = conditions;
            super.findTarget();
        }
    }

    public static boolean isEvading(Mob mob) {
        if (mob == null) return false;
        Integer until = EVADING_UNTIL.get(mob.getUUID());
        if (until == null) return false;
        if (mob.tickCount < until) return true;
        EVADING_UNTIL.remove(mob.getUUID(), until);
        return false;
    }

    public static void forget(UUID id) {
        if (id != null) EVADING_UNTIL.remove(id);
    }

    /**
     * Applies the settings that are not goals.
     *
     * <p>Called from {@code NpcCombatProfile.write}, so a save takes effect at once. Both of these
     * are entity state rather than per-tick decisions: a navigation is asked whether it may open
     * doors when it builds a path, and the water malus is read while that path is being costed.
     */
    public static void apply(Mob mob, NpcCombatProfile profile) {
        if (mob == null || profile == null) {
            return;
        }
        // Written every time, including back to the default, so turning a switch off undoes it
        // rather than leaving the NPC with whatever it was last given.
        mob.setPathfindingMalus(PathType.WATER,
                profile.aiAvoidsWater ? WATER_AVOID_MALUS : 0.0f);

        if (mob.getNavigation() instanceof GroundPathNavigation ground) {
            boolean opens = profile.aiDoorInteract == NpcDoorInteract.OPEN;
            // canPassDoors covers both modes: a door it intends to break still has to be on the
            // route for it to ever stand in front of one.
            ground.setCanPassDoors(profile.aiDoorInteract != NpcDoorInteract.DISABLED);
            ground.setCanOpenDoors(opens);
        }
        if (mob instanceof XenoNpcEntity xenoNpc) {
            xenoNpc.setMountControlEnabled(profile.aiMountControl);
        }
    }

    /** Adds every gated goal. Called once, from {@code registerGoals}. */
    public static void register(PathfinderMob mob,
                                java.util.function.ObjIntConsumer<net.minecraft.world.entity.ai.goal.Goal> sink) {
        sink.accept(new SwimGoal(mob), 0);
        sink.accept(new DoorBreakGoal(mob), 1);
        sink.accept(new DoorOpenGoal(mob), 2);
        sink.accept(new LeapGoal(mob), 3);
        sink.accept(new DamageResponseGoal(mob), 4);
        sink.accept(new ShelterGoal(mob), 5);
    }
}
