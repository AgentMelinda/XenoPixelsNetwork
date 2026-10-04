package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Turns a profile's behaviour flags into real entity behaviour.
 *
 * <p>The MyNPCs reference offers these as ordinary rows on its Stats and Display tabs, so an NPC
 * that cannot drown or does not burn in daylight is expected to be a checkbox rather than a code
 * change. Each flag maps onto a vanilla hook, all of them read out of the pinned 1.21.1 source
 * before being used here:
 *
 * <ul>
 *   <li>{@code fireImmune()} and {@code causeFallDamage()} are overridable on {@code Entity} and
 *       {@code LivingEntity}, so those are answered directly in {@link XenoNpcEntity}.</li>
 *   <li>{@code canBeAffected()} is likewise overridable, which is how potion immunity works.</li>
 *   <li>{@code setInvisible} and {@code setGlowingTag} are plain setters - {@code setGlowingTag} is
 *       {@code final}, so it is called rather than overridden.</li>
 *   <li>Drowning is <em>not</em> a simple flag: {@code canBreatheUnderwater()} is {@code final} and
 *       decided by the {@code CAN_BREATHE_UNDER_WATER} entity-type tag, so an NPC that should not
 *       drown has its air topped back up in {@code decreaseAirSupply} instead.</li>
 *   <li>Sun burning copies what {@code Zombie} does: {@code Mob.isSunBurnTick()} plus
 *       {@code igniteForSeconds}.</li>
 * </ul>
 *
 * <p>This class holds the parts that are a per-tick or on-load action. The parts that are answers to
 * a vanilla question live as overrides on the entity, because that is the only place they can.
 */
public final class XenoNpcBehaviour {

    /** How long a sun-burning NPC catches for, matching {@code Zombie}. */
    private static final float SUN_BURN_SECONDS = 8.0f;

    private XenoNpcBehaviour() {
    }

    /**
     * Applies the flags that are set rather than asked.
     *
     * <p>Called when an NPC loads and after a profile save, so an edit takes effect without a
     * reload.
     */
    public static void apply(LivingEntity npc, NpcCombatProfile profile) {
        if (npc == null || profile == null || npc.level().isClientSide()) {
            return;
        }
        npc.setInvisible(!profile.visible);
        npc.setGlowingTag(profile.modelGlowing);

        // A changed hitbox scale has to be recomputed, or the old box stays until something else
        // happens to refresh it.
        if (npc instanceof XenoNpcEntity nativeNpc) syncHitbox(nativeNpc, profile);
    }

    /** Also called during ticks so a live /xenoset change updates already loaded NPCs. */
    public static void syncHitbox(XenoNpcEntity npc, NpcCombatProfile profile) {
        if (npc == null || profile == null || npc.level().isClientSide()) return;
        npc.setHitboxScale(NpcCombatProfile.effectiveHitboxScale(profile.hitboxScale,
                profile.baseSize, XenoServerConfig.xenoNpcSizeScalesHitbox));
    }

    /**
     * Per-tick behaviour: daylight burning.
     *
     * @param sunBurnTick what {@code Mob.isSunBurnTick()} answered this tick
     */
    public static void tick(LivingEntity npc, NpcCombatProfile profile, boolean sunBurnTick) {
        if (npc == null || profile == null || npc.level().isClientSide()) {
            return;
        }
        if (profile.burnsInSun && !profile.fireImmune && sunBurnTick) {
            npc.igniteForSeconds(SUN_BURN_SECONDS);
        }
    }

    /**
     * Air supply for an NPC that should not drown.
     *
     * @param decreased what the superclass would have left
     * @return the air supply to actually use
     */
    public static int airSupply(NpcCombatProfile profile, int current, int decreased) {
        if (profile != null && !profile.canDrown) {
            // Keeping it where it was, rather than forcing the maximum, avoids fighting anything
            // else that legitimately drains air.
            return current;
        }
        return decreased;
    }

    /**
     * How often regeneration is applied.
     *
     * <p>Once a second, which is also the unit the editor's fields are in - so the number an
     * operator types is the number healed each time this runs, with no conversion to get wrong.
     */
    private static final int REGEN_INTERVAL_TICKS = 20;

    /**
     * How long after being hurt an NPC still counts as fighting.
     *
     * <p>Five seconds. Without it an NPC that had just been hit but had no target - because it was
     * struck from behind, or by something it will not retaliate against - would flip to the
     * out-of-combat rate immediately, which is exactly when the difference matters most.
     */
    private static final int COMBAT_MEMORY_TICKS = 100;

    /**
     * Heals an NPC at whichever of its two rates applies.
     *
     * <p>Both are zero by default, so this returns on the first read for every NPC that has not
     * been given one - which is every NPC that existed before the fields did.
     */
    public static void tickRegen(LivingEntity npc, NpcCombatProfile profile) {
        if (npc == null || profile == null || npc.level().isClientSide() || !npc.isAlive()) {
            return;
        }
        if (profile.healthRegen <= 0.0f && profile.combatRegen <= 0.0f) {
            return;
        }
        // Staggered by entity id, like the bard and the path walker, so a crowd of NPCs does not
        // all heal on the same tick.
        if ((npc.tickCount + npc.getId()) % REGEN_INTERVAL_TICKS != 0) {
            return;
        }
        if (npc.getHealth() >= npc.getMaxHealth()) {
            return;
        }
        boolean fighting = fighting(npc);
        float rate = fighting ? profile.combatRegen : profile.healthRegen;
        if (rate > 0.0f) {
            npc.heal(rate);
        }
    }

    /** Whether this NPC counts as in a fight right now. */
    private static boolean fighting(LivingEntity npc) {
        if (npc instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null) {
            return true;
        }
        // Recently hurt counts too: an NPC being shot at from a distance has no target of its own
        // but is plainly in a fight, and it is the one case where the in-combat rate matters most.
        return npc.getLastHurtByMob() != null
                && npc.tickCount - npc.getLastHurtByMobTimestamp() < COMBAT_MEMORY_TICKS;
    }

    /** How often the leash is checked. Every tick would be wasteful for a slow-moving concern. */
    private static final int LEASH_CHECK_TICKS = 20;

    /** Beyond this far from home, walking back is hopeless and it is placed instead. */
    private static final double LEASH_TELEPORT_FACTOR = 3.0;

    /**
     * How far past the radius an NPC must get before the leash grabs it again.
     *
     * <p>A dead band, and the reason is the bug it fixes. The leash released at exactly the radius
     * and re-acquired at exactly the radius, so an NPC standing on that boundary - which is where
     * being walked home leaves it - flipped between "home" and "go home" on every check. One and a
     * half blocks is wider than a walk step, so an arrival settles instead of oscillating.
     */
    private static final double LEASH_HYSTERESIS = 1.5;

    /** How close to home counts as arrived, for an NPC the leash is walking back. */
    private static final double LEASH_ARRIVAL = 1.5;

    /**
     * Whether an NPC must refuse a new target: the leash is walking it home. As CustomNPCs' Return
     * To Start, it is disengaged until it arrives - otherwise its target goal re-acquired the player
     * the tick after the leash dropped it, and the two turned it back and forth every second
     * (measured 2026-09-30; owner: "when he is returining to respawn location by foot he is
     * flickering"). Arbitration off keeps the old free-for-all for comparison.
     */
    static boolean refusesNewTarget(boolean arbitration,
                                    net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim current,
                                    boolean newTargetNonNull) {
        return arbitration && newTargetNonNull
                && current == net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.LEASH;
    }

    /**
     * Whether the combat brain sits out this tick: the leash is walking the NPC home. The brain
     * steers at more than the target - a ki hard lock or ki-sense lock kept pointing it at the
     * player it had fought - so refusing the target alone left it turning the NPC every tick
     * against the leash's path (measured: yaw flipping 0/180 at ~0.002 blocks/tick; with the brain
     * sitting out, a steady walk home).
     */
    static boolean brainYieldsToLeash(boolean arbitration,
                                      net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim current) {
        return arbitration && current == net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.LEASH;
    }

    /** {@link #brainYieldsToLeash} for a live NPC. */
    public static boolean brainYieldsToLeash(net.minecraft.world.entity.LivingEntity npc) {
        return npc != null && brainYieldsToLeash(XenoServerConfig.npcMovementArbitration,
                net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.current(npc));
    }

    /** Whether the leash lets go: when actually home, not merely back inside the radius. */
    static boolean leashReleases(boolean arbitration, boolean holding, double distanceSq, double radius) {
        double release = arbitration && holding ? LEASH_ARRIVAL : radius;
        return distanceSq <= release * release;
    }

    /** Whether a stray not already held is grabbed: past the radius and its dead band. */
    static boolean leashGrabs(double distanceSq, double radius) {
        double grab = radius + LEASH_HYSTERESIS;
        return distanceSq > grab * grab;
    }

    /** {@link #refusesNewTarget} for a live NPC. */
    public static boolean refusesNewTarget(net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc,
                                           net.minecraft.world.entity.LivingEntity target) {
        return npc != null && !npc.level().isClientSide()
                && refusesNewTarget(XenoServerConfig.npcMovementArbitration,
                net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.current(npc), target != null);
    }

    /**
     * Returns a strayed NPC to the spot it was created on.
     *
     * <p>CustomNPCs and MyNPCs both anchor an NPC to its spawn point, so one that chased something
     * over a hill finds its way back rather than being lost. Inside the radius nothing happens at
     * all, so an NPC fighting near home is left alone.
     *
     * <p>It walks back where it can, because a teleporting NPC reads as a glitch. Only when it is
     * absurdly far - three times the radius, so realistically another biome - is it placed
     * directly, since pathfinding that distance will not succeed.
     *
     * <p><b>This is now the only home leash.</b> {@code XenoNpcBrainV5.returnHome} used to pull an
     * NPC home as well, on a different schedule and against a different radius taken from its role.
     * Two pulls to the same spot that disagreed about when to stop is what made NPCs shudder in
     * place: one said "close enough" while the other said "go home", forever. The role's leash did
     * not go away - it supplies the default radius for an NPC that has never had one set - but only
     * this method steers.
     */
    public static void tickLeash(net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc) {
        if (npc == null || npc.level().isClientSide() || npc.tickCount % LEASH_CHECK_TICKS != 0) {
            return;
        }
        if (net.bullettrain.xenopixelsmod.compat.npc.NpcFlightOwnership.brainDirectsFlight(npc.getUUID())) {
            return;
        }
        if (net.bullettrain.xenopixelsmod.npc.job.NpcFollowerJob.active(
                NpcCombatProfile.readCached(npc))) {
            net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.release(npc,
                    net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.LEASH);
            return;
        }
        // A patrolling NPC is left alone. The leash hauls an NPC back when it leaves its home
        // radius, and a route that goes further than that would be fought every twenty ticks -
        // which in play reads as an NPC shuddering in place rather than walking its beat.
        if (net.bullettrain.xenopixelsmod.npc.path.NpcPathWalker.patrolling(npc)) {
            return;
        }

        // Return To Start, off the AI page. Off means an NPC stays wherever it ends up, which is
        // what a follower wants and what anything driven by command or scene wants.
        if (!NpcCombatProfile.readCached(npc).aiReturnToStart) {
            net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.release(npc,
                    net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.LEASH);
            return;
        }

        var data = npc.npcData();
        double radius = leashRadiusFor(npc);
        if (!data.hasHome() || radius <= 0.0) {
            return;
        }

        double dx = npc.getX() - data.homeX();
        double dy = npc.getY() - data.homeY();
        double dz = npc.getZ() - data.homeZ();
        double distanceSq = dx * dx + dy * dy + dz * dz;

        // Already holding it: keep walking until actually home, rather than letting go the instant
        // it crosses back over the line it was dragged across.
        boolean holding = net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.current(npc)
                == net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.LEASH;
        if (holding ? leashReleases(XenoServerConfig.npcMovementArbitration, true, distanceSq, radius)
                : !leashGrabs(distanceSq, radius)) {
            if (holding) {
                net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.release(npc,
                        net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.LEASH);
            }
            return;
        }

        if (!claimMovement(npc,
                net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.LEASH)) {
            // Something more urgent is steering - a scene, a fight, a patrol. It will finish, and
            // the NPC will still be too far away on the next check.
            return;
        }

        double limit = radius * LEASH_TELEPORT_FACTOR;
        // Placed directly when it is hopelessly far, and also when walking has demonstrably failed:
        // an NPC wedged behind a fence post used to be told to walk home every twenty ticks
        // forever, lurching at the obstacle each time and never arriving.
        boolean giveUpWalking = distanceSq > limit * limit
                || (XenoServerConfig.npcMovementArbitration
                    && net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.stuck(npc));
        if (giveUpWalking) {
            npc.teleportTo(data.homeX(), data.homeY(), data.homeZ());
            npc.getNavigation().stop();
            net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.clearProgress(npc);
            net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.release(npc,
                    net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.LEASH);
            return;
        }

        // Stop re-issuing once it is plainly not working. The teleport above takes over on the next
        // check; until then the NPC stands still, which looks far better than juddering.
        if (XenoServerConfig.npcMovementArbitration
                && !net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.progressing(npc)) {
            return;
        }

        // Drop the target first, or the combat goal simply re-paths back to whatever it chased.
        npc.setTarget(null);
        npc.getNavigation().moveTo(data.homeX(), data.homeY(), data.homeZ(), 1.0);
    }

    /**
     * How far this NPC may stray.
     *
     * <p>Its own {@code leashRadius} when one has been set, and otherwise the radius its role
     * implies. That fallback is what {@code XenoNpcBrainV5.returnHome} used to enforce with a
     * second pull of its own; folding it in here keeps a guard anchored to its post without two
     * systems arguing about where the post is.
     */
    public static double leashRadiusFor(net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc) {
        if (npc == null) {
            return 0.0;
        }
        double own = npc.npcData().leashRadius();
        if (own > 0.0) {
            return own;
        }
        return net.bullettrain.xenopixelsmod.npc.XenoNpcRoleBehaviour.homeLeash(npc.role());
    }

    /**
     * Asks the movement arbiter, or waves the caller through when it is switched off.
     *
     * <p>The switch is what keeps the previous free-for-all reachable: with arbitration off every
     * mover behaves exactly as it did before, which is the only way to compare the two in play.
     */
    public static boolean claimMovement(net.minecraft.world.entity.LivingEntity npc,
                                        net.bullettrain.xenopixelsmod.npc.movement
                                                .NpcMovementOwner.Claim claim) {
        if (!XenoServerConfig.npcMovementArbitration) {
            return true;
        }
        return net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.claim(npc, claim);
    }

    /** The boss bar colour, falling back to purple when the stored name is not one. */
    public static BossEvent.BossBarColor bossBarColor(NpcCombatProfile profile) {
        if (profile == null || profile.bossBarColor == null) {
            return BossEvent.BossBarColor.PURPLE;
        }
        try {
            return BossEvent.BossBarColor.valueOf(profile.bossBarColor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossEvent.BossBarColor.PURPLE;
        }
    }

    /** Every boss bar colour, for the editor's cycle. */
    public static java.util.List<String> bossBarColors() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (BossEvent.BossBarColor color : BossEvent.BossBarColor.values()) {
            names.add(color.name());
        }
        return java.util.List.copyOf(names);
    }

    /**
     * Creature families the editor offers.
     *
     * <p>1.21 replaced {@code MobType} with entity-type tags, so this is a label the mod interprets
     * rather than a vanilla enum. It is listed here so the editor cycles real values instead of
     * free text.
     */
    public static java.util.List<String> creatureTypes() {
        return java.util.List.of("normal", "undead", "arthropod", "illager", "aquatic");
    }
}
