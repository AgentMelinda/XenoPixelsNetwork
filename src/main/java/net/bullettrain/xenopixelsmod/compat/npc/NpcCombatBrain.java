package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi;
import net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.KiDeflect;
import net.bullettrain.xenopixelsmod.combat.clone.CloneCombatPolicy;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatContext;
import net.bullettrain.xenopixelsmod.compat.npc.brain.v4.NpcBrainBudget;
import net.bullettrain.xenopixelsmod.compat.npc.brain.v4.NpcCombatBrainV4;
import net.bullettrain.xenopixelsmod.compat.npc.brain.v4.NpcCombatContextV4;
import net.bullettrain.xenopixelsmod.compat.npc.brain.v4.NpcCombatIntentV4;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Autonomous combat for profiled NPCs.
 *
 * <p>Opt-in per NPC through {@link NpcCombatProfile#combatBrain}, which defaults to off — every
 * existing scripted NPC therefore behaves exactly as its script says unless an author turns this
 * on. Close range prefers melee; named specials interrupt on cooldown. Every move is one a script
 * could equally have made through {@link NpcCombatMoves} or {@link NpcKiAttackDispatcher}.
 *
 * <p><b>Cost.</b> {@link NpcProfileLifecycle} calls {@link #advance} every tick while a combo is
 * running, and {@link #tick} on a staggered slot (one NPC per id per ten ticks).
 */
public final class NpcCombatBrain {
    /**
     * Inside this, prefer strikes and melee pressure.
     *
     * <p>Was 4.0, a number of this class's own. It is now DragonMineZ's {@code MELEE_RANGE}, shared
     * with the v2 brain through {@link NpcCombatRanges} so the two cannot drift apart again.
     */
    static final double MELEE_BAND = NpcCombatRanges.MELEE;
    /** Between melee and this, blasts and close deflection. DMZ's {@code MID_RANGE}. */
    static final double MID_BAND = NpcCombatRanges.MID;
    /**
     * Out to this, waves and big blasts. Beyond it, close the distance first.
     *
     * <p>Was 32.0; DMZ's {@code OUT_RANGE} is 28.0.
     */
    static final double KI_BAND = NpcCombatRanges.OUT;
    static final double BONE_CRUSHER_RANGE = 6.0;

    /** Below this fraction of max energy the NPC disengages rather than spending more. */
    private static final double LOW_ENERGY = 0.2;
    /** Below this fraction of max health the NPC will try to ascend. */
    private static final double ASCEND_HEALTH = 0.5;

    /** Ticks between two decisions for one NPC, on top of the caller's own stagger. */
    private static final int DECISION_INTERVAL = 20;
    /** 0 of 0..3 → special; the other three rolls stay on melee. */
    private static final int SPECIAL_ROLL_SIDES = 4;
    private static final int FLYING_FIST_HITS = 4;
    private static final int FLYING_FIST_GAP = 5;
    private static final float HEAVY_HIT_SCALE = 1.75f;

    /**
     * How long an NPC stops fighting after it has whiffed its way to the abandon threshold.
     *
     * <p>Same window the multi-form clones use, for the same reason: without one, {@code disengage}
     * clears {@link #NEXT_DECISION} and the very next tick decides again, so the NPC resumes
     * swinging immediately and the loop this is meant to break carries straight on.
     */
    private static final int ABANDON_TICKS = 60;

    private static final Map<UUID, Integer> NEXT_DECISION = new ConcurrentHashMap<>();
    /** Consecutive melee attempts that connected with nothing; see {@link #noteMelee}. */
    private static final Map<UUID, Integer> MELEE_WHIFFS = new ConcurrentHashMap<>();
    /** Server tick an abandoned NPC may fight again. */
    private static final Map<UUID, Integer> ABANDON_UNTIL = new ConcurrentHashMap<>();
    private static final NpcBrainBudget V4_BUDGET = new NpcBrainBudget(32);
    private static final Map<UUID, Integer> KI_CURSOR = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SPECIAL_UNTIL = new ConcurrentHashMap<>();
    static final Map<UUID, Combo> COMBOS = new ConcurrentHashMap<>();
    private static final Set<UUID> FLIGHT = ConcurrentHashMap.newKeySet();

    enum Special {
        FLYING_FIST,
        HEAVY_HIT,
        BONE_CRUSHER,
        KIAI,
        RANDOM_KI
    }

    record Combo(UUID victim, int remainingHits, int nextHitTick, int step) {}

    private NpcCombatBrain() {}

    public static void forget(UUID npcId) {
        if (npcId != null) {
            NEXT_DECISION.remove(npcId);
            KI_CURSOR.remove(npcId);
            SPECIAL_UNTIL.remove(npcId);
            COMBOS.remove(npcId);
            FLIGHT.remove(npcId);
            MELEE_WHIFFS.remove(npcId);
            ABANDON_UNTIL.remove(npcId);
        }
    }

    static void clearTransient(UUID npcId) {
        forget(npcId);
    }

    public static boolean directsFlight(UUID npcId) {
        return npcId != null && FLIGHT.contains(npcId);
    }

    public static boolean isBusy(LivingEntity npc) {
        if (npc == null) {
            return false;
        }
        return NpcTransformSystem.isHolding(npc.getUUID())
                || NpcChargeMoves.isCharging(npc)
                || NpcHakai.isChanneling(npc)
                || NpcKiAim.lockedTarget(npc) != null
                || COMBOS.containsKey(npc.getUUID())
                || NpcKiAttackDispatcher.isOwnerClashing(npc);
    }

    public static boolean isActing(LivingEntity npc) {
        if (npc == null) {
            return false;
        }
        return NpcTransformSystem.isHolding(npc.getUUID())
                || NpcChargeMoves.isCharging(npc)
                || NpcHakai.isChanneling(npc)
                || COMBOS.containsKey(npc.getUUID())
                || NpcKiAttackDispatcher.isOwnerClashing(npc)
                || NpcKiAttackDispatcher.ownsLiveProjectile(npc);
    }

    /** After knockback grace ends: face the current threat again so aggression resumes without another hit. */
    public static void resumeAfterHit(LivingEntity npc) {
        if (npc == null || !(npc instanceof net.minecraft.world.entity.Mob mob)) return;
        LivingEntity threat = mob.getTarget();
        if (threat == null || !threat.isAlive() || threat == npc) return;
        mob.getLookControl().setLookAt(threat, 180f, 180f);
        double dx = threat.getX() - npc.getX();
        double dz = threat.getZ() - npc.getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        npc.setYRot(yaw);
        npc.yBodyRot = yaw;
        npc.yHeadRot = yaw;
    }

    /**
     * Drops brain-owned combat state. Does not clear a script hard-lock.
     */
    public static void disengage(LivingEntity npc) {
        if (npc == null) {
            return;
        }
        NpcChargeMoves.cancel(npc);
        NpcKiAim.cancel(npc);
        NpcCombatMoves.guard(npc, false);
        COMBOS.remove(npc.getUUID());
        SPECIAL_UNTIL.remove(npc.getUUID());
        NEXT_DECISION.remove(npc.getUUID());
        MELEE_WHIFFS.remove(npc.getUUID());
        land(npc, NpcCombatProfile.readCached(npc));
        XenoAnimApi.stopClip(npc);
    }

    /** Interrupt offense after a hit without landing or overwriting the received impulse. */
    static void interruptForHit(LivingEntity npc) {
        NpcChargeMoves.cancel(npc);
        NpcHakai.cancel(npc);
        NpcKiAim.cancel(npc);
        COMBOS.remove(npc.getUUID());
        MELEE_WHIFFS.remove(npc.getUUID());
    }

    /**
     * Search-fly. Safe to call every server tick. Short ki-hold is not busy for this.
     */
    public static void steer(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim) {
        if (npc == null || profile == null || !profile.combatBrain) {
            return;
        }
        if (net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.isControlledVictim(npc.getUUID())) return;
        // Just knocked back: let the push play out before steering again (NpcKnockbackGrace).
        if (NpcKnockbackGrace.active(npc)) return;
        // Ki projectile still in the world: stand still until it despawns.
        if (NpcKiAttackDispatcher.ownsLiveProjectile(npc)) {
            if (npc instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().stop();
            return;
        }
        if (!mayStartCharge(profile) && NpcChargeMoves.isCharging(npc)) {
            NpcChargeMoves.cancel(npc);
        }
        if (!mayStartFlyingFist(profile)) {
            COMBOS.remove(npc.getUUID());
        }
        boolean blast = profile.allowBrainAction("deflectBlast", npc.getRandom());
        boolean wave = profile.allowBrainAction("deflectWave", npc.getRandom());
        double deflectDist = victim != null && victim.isAlive() && victim != npc
                ? npc.distanceTo(victim) : Double.POSITIVE_INFINITY;
        if ((blast || wave) && NpcKiDeflectPolicy.allow(deflectDist,
                net.bullettrain.xenopixelsmod.config.XenoServerConfig.clampedBrainDeflectMinDistance())) {
            String deflectKey = blast ? "deflectBlast" : "deflectWave";
            if (KiDeflect.tryNpcDeflect(npc, profile.brainModifier(deflectKey), blast, wave)) {
                playActionAnim(npc);
            }
        }
        boolean targetOnGround = NpcFlightPolicy.targetGrounded(victim);
        if (targetOnGround) {
            land(npc, profile);
        }
        if (victim == null || !victim.isAlive() || victim == npc) {
            land(npc, profile);
            if (isActing(npc) || COMBOS.containsKey(npc.getUUID())
                    || NpcKiAim.lockedTarget(npc) != null) {
                disengage(npc);
            }
            return;
        }
        if (NpcKiAttackDispatcher.isOwnerClashing(npc) || isActing(npc)) {
            return;
        }
        double dx = victim.getX() - npc.getX();
        double dy = victim.getY() - npc.getY();
        double dz = victim.getZ() - npc.getZ();
        boolean climbing = NpcBrainKiRotation.needsClimb(dy);
        boolean wantFly = !targetOnGround && NpcBrainKiRotation.shouldFly(profile.brainFly, targetOnGround,
                climbing) && profile.brainChance("fly") > 0
                && NpcFlightPolicy.canCombatFly(profile);
        double flySpeed = NpcBrainKiRotation.FLY_SPEED * profile.brainModifier("fly");

        if (wantFly) {
            claimFlight(npc, profile);
            // DMZ saga NPCs: fly at the target, yaw at the target. Sideways standoff plus
            // leftover 1.25 b/t in the melee band is what read as left-right crazy.
            double[] fly = npc instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity
                    ? NpcBrainKiRotation.airChaseVelocity(dx, dy, dz, flySpeed,
                            NpcBrainKiRotation.airHoverDistance(npc.getBbWidth(), victim.getBbWidth()))
                    : NpcBrainKiRotation.airChaseVelocity(dx, dy, dz, flySpeed);
            applyVelocity(npc, fly[0], fly[1], fly[2]);
            if (!NpcTargetKeeper.isKiSenseLockedOn(npc, victim)) {
                NpcKiAim.applyLook(npc, NpcBrainKiRotation.targetYaw(dx, dz), 0.0f);
            }
            return;
        }
        if (profile.brainLand || !profile.brainFly) {
            land(npc, profile);
        }
    }

    private static void playActionAnim(LivingEntity npc) {
        if (!NpcDmzAnim.canAnimate(npc)) {
            NpcKiAim.playMelee(npc);
        }
    }

    /**
     * The arm swing only, for beats that go on to call {@link NpcMeleeDamage#hit}. That call plays
     * the configured or GeckoLib attack clip itself (via {@code onMeleeAttempt}), so going through
     * {@link #playActionAnim} as well started the same clip twice in one tick and restarted it
     * mid-stroke.
     */
    private static void swingBeforeHit(LivingEntity npc) {
        if (!NpcDmzAnim.canAnimate(npc)) {
            npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        }
    }

    /** Combo / Flying Fist beats. Safe to call every server tick. */
    public static void advance(LivingEntity npc, NpcCombatProfile profile, int serverTick) {
        if (npc == null || profile == null || !profile.combatBrain) {
            return;
        }
        if (net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.isControlledVictim(npc.getUUID())) return;
        if (NpcKnockbackGrace.active(npc)) return;
        if (!mayStartFlyingFist(profile)) {
            COMBOS.remove(npc.getUUID());
            return;
        }
        Combo combo = COMBOS.get(npc.getUUID());
        if (combo == null) {
            return;
        }
        if (serverTick < combo.nextHitTick()) {
            return;
        }
        MinecraftServer server = npc.getServer();
        LivingEntity victim = server == null ? null : NpcEntityLookup.findAlive(server, combo.victim());
        if (victim == null || !victim.isAlive() || npc.distanceTo(victim) > MELEE_BAND + 2.0) {
            COMBOS.remove(npc.getUUID());
            return;
        }
        NpcKiAim.applyPose(npc, victim);
        // Animate only if the punch can actually land. The combo is kept alive out to
        // MELEE_BAND + 2 so a target that steps back briefly does not cancel it, but the swing
        // itself only reaches MELEE_BAND - so between those two distances this used to play a full
        // punch into empty air, once per combo gap, all the way in. That is the "attacking the air
        // on the way to its target" behaviour.
        //
        // Not a whiff and not a dropped combo: the step is rescheduled, so the flurry picks up
        // where it left off the moment the NPC is close enough, and the distance guard above still
        // ends it if the target really is leaving.
        if (!NpcCombatRanges.withinMelee(npc, victim)) {
            COMBOS.put(npc.getUUID(), new Combo(combo.victim(), combo.remainingHits(),
                    serverTick + FLYING_FIST_GAP, combo.step()));
            return;
        }
        // The BT3 choreography is this mod's own, so the V7 brain - the ported DMZ tree and
        // nothing of ours - swings without it. The hit still lands; only the pose is skipped, which
        // is the right shape: dropping the damage would make V7 a weaker brain rather than a
        // different one.
        if (profile.allowXenoSpecial()) {
            NpcDmzAnim.play(npc, Bt3ComboChoreography.resolve(0, combo.step(), false, false));
        }
        swingBeforeHit(npc);
        NpcMeleeDamage.hit(npc, victim, profile.brainModifier("flyingFist"));
        int left = combo.remainingHits() - 1;
        if (left <= 0) {
            COMBOS.remove(npc.getUUID());
            markSpecial(npc, profile, serverTick);
            return;
        }
        COMBOS.put(npc.getUUID(), new Combo(combo.victim(), left, serverTick + FLYING_FIST_GAP,
                combo.step() + 1));
    }

    /**
     * One decision for one NPC.
     *
     * @param victim the target {@link NpcTargetKeeper} resolved this tick, or null
     */
    public static void tick(MinecraftServer server, LivingEntity npc, NpcCombatProfile profile,
                            LivingEntity victim, int serverTick) {
        if (npc == null || profile == null || !profile.combatBrain) {
            return;
        }
        if (net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.isControlledVictim(npc.getUUID())) return;
        if (NpcKnockbackGrace.active(npc)) return;
        if (victim == null || !victim.isAlive() || victim == npc) {
            if (isActing(npc) || COMBOS.containsKey(npc.getUUID())
                    || NpcKiAim.lockedTarget(npc) != null) {
                disengage(npc);
            }
            return;
        }
        if (abandoned(npc, serverTick)) {
            return;
        }
        advance(npc, profile, serverTick);
        if (NpcKiAttackDispatcher.isOwnerClashing(npc) || isBusy(npc)) {
            return;
        }
        Integer next = NEXT_DECISION.get(npc.getUUID());
        if (next != null && serverTick < next) {
            return;
        }
        int decisionGap = Math.max(2, Math.round(DECISION_INTERVAL
                / NpcCombatProfile.clampNpcMeleeSpeed(profile.npcMeleeSpeed)));
        NEXT_DECISION.put(npc.getUUID(), serverTick + decisionGap);

        NpcResources.Snapshot resources = NpcResources.get(npc, profile);
        double energyFraction = resources.maxEnergy() <= 0.0
                ? 0.0 : resources.energy() / resources.maxEnergy();
        double healthFraction = npc.getMaxHealth() <= 0.0f
                ? 1.0 : npc.getHealth() / (double) npc.getMaxHealth();
        double dx = victim.getX() - npc.getX();
        double dy = victim.getY() - npc.getY();
        double dz = victim.getZ() - npc.getZ();
        double horiz = NpcBrainKiRotation.horizontal(dx, dz);
        double distance = npc.distanceTo(victim);
        boolean stacked = NpcBrainKiRotation.stackedForAim(horiz, dy);
        boolean climbing = profile.brainFly && NpcBrainKiRotation.needsClimb(dy);
        boolean suppressTp = NpcBrainKiRotation.suppressTeleports(profile.brainFly,
                directsFlight(npc.getUUID()), stacked, climbing);

        if (profile.brainVersion != null && profile.brainVersion.isV4()) {
            if (!V4_BUDGET.tryAcquire(serverTick)) return;
            runV4(npc, profile, victim, serverTick, energyFraction, healthFraction, distance,
                    suppressTp);
            return;
        }

        if (stacked) {
            NpcKiAim.applyHorizontalPose(npc, victim);
        } else {
            NpcKiAim.applyPose(npc, victim);
        }

        if (profile.brainDisengage && profile.allowBrainAction("disengage", npc.getRandom())
                && tryDisengage(npc, victim, energyFraction, healthFraction,
                profile.brainModifier("disengage"))) {
            return;
        }
        if (profile.brainAscend && profile.allowBrainAction("ascend", npc.getRandom())
                && tryAscend(npc, profile, healthFraction, energyFraction,
                profile.brainModifier("ascend"))) {
            return;
        }
        if (NpcBrainKiRotation.shouldAnswerBeam(false, profile.brainKiWave,
                NpcKiAttackDispatcher.victimOwnsClashableBeam(victim))) {
            if (!stacked && NpcKiAttackDispatcher.fireClashWave(npc, profile, victim)) {
                return;
            }
            return;
        }
        if (stacked || climbing) {
            return;
        }
        if (distance <= NpcCombatRanges.meleeReach(npc, victim)) {
            decideClose(npc, profile, victim, serverTick, distance);
            return;
        }
        if (profile.npcRangedMinRange > 0.0f && distance < profile.npcRangedMinRange) {
            Vec3 away = npc.position().subtract(victim.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 1.0e-6) away = new Vec3(1.0, 0.0, 0.0);
            Vec3 retreat = npc.position().add(away.normalize().scale(
                    Math.min(8.0, profile.npcRangedMinRange - distance + 1.0)));
            if (npc instanceof net.minecraft.world.entity.Mob mob) {
                mob.getNavigation().moveTo(retreat.x, npc.getY(), retreat.z, 1.1);
            }
            return;
        }
        double rangedMax = profile.npcRangedRange > 0.0f
                ? profile.npcRangedRange : KI_BAND;
        if (distance <= rangedMax) {
            if (tryReadySpecial(npc, profile, victim, serverTick, distance)) {
                return;
            }
            if (!tryKi(npc, profile, victim)) {
                if (profile.allowBrainAction("vanish", npc.getRandom()) && !suppressTp) {
                    NpcCombatMoves.vanish(npc, victim, 0);
                }
            }
            return;
        }
        if (profile.allowBrainAction("chase", npc.getRandom()) && !suppressTp) {
            NpcCombatMoves.chase(npc, victim);
        }
    }

    private static void runV4(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim,
                              int serverTick, double energyFraction, double healthFraction,
                              double distance, boolean suppressTp) {
        NpcSagaCombatContext saga = NpcSagaCombatContext.snapshot(npc, victim, profile);
        NpcCombatContextV4 context = new NpcCombatContextV4();
        context.distance = distance;
        context.energyFraction = energyFraction;
        context.healthFraction = healthFraction;
        context.targetApproaching = saga.targetApproaching;
        context.targetBlocking = saga.targetBlocking;
        context.targetHelpless = saga.targetHelpless;
        context.targetCasting = saga.targetCasting;
        context.targetFiring = saga.targetFiring;
        context.canRecover = profile.brainDisengage;
        context.canCombo = saga.comboReady;
        context.canGuardBreak = saga.hasGuardBreak;
        context.canVanish = saga.vanishReady && !suppressTp;
        context.canDash = saga.dashReady && !suppressTp;
        context.canHitscan = saga.hasHitscan;
        context.canTravel = saga.hasTravel;
        context.canZone = saga.hasZoning;
        context.canMelee = saga.allowMelee;
        NpcCombatIntentV4 intent = NpcCombatBrainV4.decide(context);
        switch (intent) {
            case RECOVER -> {
                if (!tryDisengage(npc, victim, energyFraction, healthFraction,
                        profile.brainModifier("disengage"))) disengage(npc);
            }
            case COMBO, GUARD_BREAK -> {
                if (!tryReadySpecial(npc, profile, victim, serverTick, distance)) {
                    decideClose(npc, profile, victim, serverTick, distance);
                }
            }
            case HITSCAN, TRAVEL, ZONING -> {
                if (!tryKi(npc, profile, victim) && !suppressTp) NpcCombatMoves.chase(npc, victim);
            }
            case VANISH -> {
                // Checked here, not only at line 338. The Brain tab's Vanish toggle reads as dead
                // otherwise: the scheduler can pick VANISH directly, and this branch performed it
                // whatever the flag said.
                if (profile.allowBrainAction("vanish", npc.getRandom())) {
                    NpcCombatMoves.vanish(npc, victim, 0);
                }
            }
            case DASH, APPROACH -> {
                if (!suppressTp) NpcCombatMoves.chase(npc, victim);
            }
            case MELEE -> decideClose(npc, profile, victim, serverTick, distance);
            case REPOSITION -> {
                // A reposition is a vanish, so it honours the same toggle.
                if (!suppressTp && profile.allowBrainAction("vanish", npc.getRandom())) {
                    NpcCombatMoves.vanish(npc, victim, 0);
                }
            }
            case HOLD -> { }
        }
    }

    private static void decideClose(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim,
                                    int serverTick, double distance) {
        if (tryReadySpecial(npc, profile, victim, serverTick, distance)) {
            return;
        }
        if (profile.allowBrainAction("strike", npc.getRandom()) && tryStrike(npc, profile, victim)) {
            return;
        }
        if (profile.allowBrainAction("charge", npc.getRandom()) && tryCharge(npc, profile, victim)) {
            playActionAnim(npc);
            return;
        }
        // Out of reach, or nothing but wall between the two: a punch here connects with air. Take
        // the ranged option instead of swinging.
        //
        // Deliberately *not* routed through noteMelee. That counter exists to make an NPC give up
        // on a target it cannot damage - one whose damage is being cancelled, say - and being too
        // far away to touch is not that. Counting distance as a whiff would have an NPC abandon a
        // fight it had simply not closed yet.
        if (!NpcCombatRanges.withinMelee(npc, victim)) {
            tryKi(npc, profile, victim);
            return;
        }
        noteMelee(npc, serverTick,
                NpcMeleeDamage.hit(npc, victim, profile.brainModifier("strike")));
    }

    /**
     * Which ki bands suit this distance.
     *
     * <p>Close in, small fast things - blasts and disks. Far out, the big committed ones - waves and
     * named attacks. This is the shape DragonMineZ's own AI fights in, and the reason an NPC now
     * looks different at 8 blocks than at 25.
     */
    private static java.util.Set<NpcBrainKiRotation.Band> preferredBands(double distance) {
        return switch (NpcCombatRanges.bandOf(distance)) {
            case MELEE, MID -> java.util.EnumSet.of(NpcBrainKiRotation.Band.BLAST,
                    NpcBrainKiRotation.Band.DISK);
            case OUT, BEYOND -> java.util.EnumSet.of(NpcBrainKiRotation.Band.WAVE,
                    NpcBrainKiRotation.Band.NAMED);
        };
    }

    /**
     * Record whether a melee attempt connected, and stop the NPC if enough of them have not.
     *
     * <p>This fallback is the brain's last resort in the melee band and it used to be unconditional:
     * the result of {@link NpcMeleeDamage#hit} was discarded, so an NPC swinging at something it can
     * never damage — a target whose damage is being cancelled, or one that keeps out-healing its
     * invulnerability window — kept swinging once per decision forever. The clone AI has had a rule
     * for exactly this since it shipped; this is that rule, on the other path.
     *
     * <p>Reuses {@link CloneCombatPolicy} rather than restating the thresholds. Those are pure
     * functions with no clone in them, already covered by {@code CloneWhiffPolicyTest}, and having
     * one definition is what keeps the two AI modes behaving the same way when a fight goes nowhere.
     */
    private static void noteMelee(LivingEntity npc, int serverTick, boolean connected) {
        UUID id = npc.getUUID();
        int streak = CloneCombatPolicy.noteSwing(MELEE_WHIFFS.getOrDefault(id, 0), connected);
        if (!CloneCombatPolicy.shouldAbandon(streak)) {
            MELEE_WHIFFS.put(id, streak);
            return;
        }
        ABANDON_UNTIL.put(id, serverTick + ABANDON_TICKS);
        disengage(npc);
    }

    /**
     * True while an NPC is sitting out its abandon window.
     *
     * <p>Also self-heals a stale entry: server ticks come from {@code MinecraftServer#getTickCount},
     * which restarts at zero on a server restart, and an entry written before one would otherwise
     * hold the NPC out of combat until the counter climbed all the way back.
     */
    private static boolean abandoned(LivingEntity npc, int serverTick) {
        Integer until = ABANDON_UNTIL.get(npc.getUUID());
        if (until == null) {
            return false;
        }
        if (!withinAbandonWindow(serverTick, until, ABANDON_TICKS)) {
            ABANDON_UNTIL.remove(npc.getUUID());
            return false;
        }
        return true;
    }

    /**
     * Whether {@code serverTick} is still inside an abandon window ending at {@code until}.
     *
     * <p>The second half of the test is what makes this safe across a restart.
     * {@code MinecraftServer#getTickCount} counts from zero each time the server starts, so a
     * deadline written before a restart sits far in the future afterwards and would hold the NPC out
     * of combat until the counter climbed all the way back to it — minutes, or longer. A tick that
     * is implausibly far below the deadline is treated as a stale entry rather than as a very long
     * wait.
     */
    static boolean withinAbandonWindow(int serverTick, int until, int window) {
        if (serverTick >= until) {
            return false;
        }
        return serverTick >= until - Math.max(1, window) * 4;
    }

    private static boolean tryReadySpecial(LivingEntity npc, NpcCombatProfile profile,
                                           LivingEntity victim, int serverTick, double distance) {
        Integer until = SPECIAL_UNTIL.get(npc.getUUID());
        if (until != null && serverTick < until) {
            return false;
        }
        List<Special> ready = readySpecials(profile, distance);
        if (!NpcBrainKiRotation.useSpecialThisTick(ready.size(),
                npc.getRandom().nextInt(SPECIAL_ROLL_SIDES))) {
            return false;
        }
        Special special = ready.get(npc.getRandom().nextInt(ready.size()));
        return fireSpecial(npc, profile, victim, special, serverTick);
    }

    static List<Special> readySpecials(NpcCombatProfile profile, double distance) {
        List<Special> ready = new ArrayList<>();
        if (mayStartFlyingFist(profile) && distance <= MELEE_BAND) {
            ready.add(Special.FLYING_FIST);
        }
        if (profile.brainHeavyHit && profile.brainChance("heavyHit") > 0
                && distance <= XenoServerConfig.vanishMaxRange * profile.brainModifier("vanish")) {
            ready.add(Special.HEAVY_HIT);
        }
        if (profile.brainBoneCrusher && profile.brainChance("boneCrusher") > 0
                && distance <= BONE_CRUSHER_RANGE * profile.brainModifier("boneCrusher")) {
            ready.add(Special.BONE_CRUSHER);
        }
        if (profile.brainKiai && profile.brainChance("kiai") > 0
                && distance <= XenoServerConfig.zBurstRange * profile.brainModifier("kiai")) {
            ready.add(Special.KIAI);
        }
        if (profile.brainRandomKi && profile.brainChance("randomKi") > 0 && distance <= KI_BAND) {
            ready.add(Special.RANDOM_KI);
        }
        return ready;
    }

    private static boolean fireSpecial(LivingEntity npc, NpcCombatProfile profile,
                                       LivingEntity victim, Special special, int serverTick) {
        return switch (special) {
            case FLYING_FIST -> startFlyingFist(npc, profile, victim, serverTick);
            case HEAVY_HIT -> {
                if (directsFlight(npc.getUUID())) {
                    yield false;
                }
                yield heavyHit(npc, profile, victim, serverTick);
            }
            case BONE_CRUSHER -> {
                if (directsFlight(npc.getUUID())) {
                    yield false;
                }
                yield boneCrusher(npc, profile, victim, serverTick);
            }
            case KIAI -> {
                boolean ok = NpcCombatMoves.kiai(npc, victim);
                if (ok) {
                    markSpecial(npc, profile, serverTick);
                }
                yield ok;
            }
            case RANDOM_KI -> {
                boolean ok = tryKi(npc, profile, victim);
                if (ok) {
                    markSpecial(npc, profile, serverTick);
                }
                yield ok;
            }
        };
    }

    private static boolean startFlyingFist(LivingEntity npc, NpcCombatProfile profile,
                                           LivingEntity victim, int serverTick) {
        if (!mayStartFlyingFist(profile) || victim == null) {
            return false;
        }
        COMBOS.put(npc.getUUID(), new Combo(victim.getUUID(), FLYING_FIST_HITS, serverTick, 1));
        advance(npc, profile, serverTick);
        return true;
    }

    private static boolean heavyHit(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim,
                                    int serverTick) {
        // Heavy Hit opens by vanishing behind the target, so an NPC with Vanish off must not use
        // it as a back door to the same teleport.
        if (!profile.allowBrainAction("vanish", npc.getRandom())) {
            return false;
        }
        if (!NpcCombatMoves.vanish(npc, victim, 0)) {
            return false;
        }
        swingBeforeHit(npc);
        NpcMeleeDamage.hit(npc, victim, HEAVY_HIT_SCALE * profile.brainModifier("heavyHit"));
        markSpecial(npc, profile, serverTick);
        return true;
    }

    private static boolean boneCrusher(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim,
                                       int serverTick) {
        if (!NpcCombatMoves.teleportAbove(npc, victim, 1.25)) {
            return false;
        }
        CombatKnockback.add(victim, NpcCombatMoves.boneCrusherImpulse());
        swingBeforeHit(npc);
        NpcMeleeDamage.hit(npc, victim, 1.35f * profile.brainModifier("boneCrusher"));
        markSpecial(npc, profile, serverTick);
        return true;
    }

    private static void claimFlight(LivingEntity npc, NpcCombatProfile profile) {
        FLIGHT.add(npc.getUUID());
        npc.setNoGravity(true);
        NpcFlightBridge.setFlying(npc, true);
        if (npc instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    private static void land(LivingEntity npc, NpcCombatProfile profile) {
        if (!FLIGHT.remove(npc.getUUID())) {
            return;
        }
        npc.setNoGravity(false);
        // Kill the air-chase momentum on touchdown. Without this the NPC kept gliding
        // sideways across the ground for a second after the navigator flipped back,
        // which read as "flying low" instead of landing. Mirrors NpcSagaCombatBrain.land.
        var drift = npc.getDeltaMovement();
        npc.setDeltaMovement(drift.x * 0.15, Math.min(drift.y, 0.0), drift.z * 0.15);
        NpcFlightBridge.setFlying(npc, false);
    }

    private static void applyVelocity(LivingEntity npc, double vx, double vy, double vz) {
        npc.setDeltaMovement(new Vec3(vx, vy, vz));
        npc.hurtMarked = true;
        npc.hasImpulse = true;
        if (npc instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    private static void markSpecial(LivingEntity npc, NpcCombatProfile profile, int serverTick) {
        SPECIAL_UNTIL.put(npc.getUUID(),
                serverTick + NpcCombatProfile.clampSpecialCooldown(profile.brainSpecialCooldown));
    }

    static boolean mayStartCharge(NpcCombatProfile profile) {
        return profile != null && profile.brainCharge && profile.brainChance("charge") > 0;
    }

    static boolean mayStartFlyingFist(NpcCombatProfile profile) {
        return profile != null && profile.brainFlyingFist && profile.brainChance("flyingFist") > 0;
    }

    private static boolean tryCharge(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim) {
        if (!mayStartCharge(profile) || NpcChargeMoves.isCharging(npc) || victim == null) {
            return false;
        }
        int max = Math.max(2, XenoServerConfig.chargeMaxTicks);
        int duration = max / 2 + npc.getRandom().nextInt(max - max / 2 + 1);
        boolean kick = npc.getRandom().nextBoolean();
        int bias = 0;
        if (kick && npc.getRandom().nextInt(4) == 0) {
            bias = npc.getRandom().nextBoolean() ? 1 : -1;
        }
        return kick
                ? NpcChargeMoves.startKick(npc, duration, bias)
                : NpcChargeMoves.startPunch(npc, duration);
    }

    private static boolean tryDisengage(LivingEntity npc, LivingEntity victim,
                                        double energyFraction, double healthFraction,
                                        float energyScale) {
        double lowEnergy = LOW_ENERGY * Math.max(0.05f, energyScale);
        if (energyFraction > lowEnergy || healthFraction > ASCEND_HEALTH) {
            NpcCombatMoves.guard(npc, false);
            return false;
        }
        NpcCombatMoves.guard(npc, true);
        return NpcCombatMoves.backstep(npc, victim);
    }

    private static boolean tryAscend(LivingEntity npc, NpcCombatProfile profile,
                                     double healthFraction, double energyFraction,
                                     float healthScale) {
        double threshold = ASCEND_HEALTH * Math.max(0.05f, Math.min(1.5f, healthScale));
        if (healthFraction > threshold || energyFraction < LOW_ENERGY) {
            return false;
        }
        if (profile.selectedFormGroup.isBlank() || profile.selectedFormId.isBlank()) {
            return false;
        }
        if (profile.selectedFormGroup.equalsIgnoreCase(profile.formGroup)
                && profile.selectedFormId.equalsIgnoreCase(profile.formId)) {
            return false;
        }
        return NpcTransformSystem.start(npc, profile.selectedFormGroup, profile.selectedFormId,
                NpcTransformSystem.DEFAULT_TICKS);
    }

    private static boolean tryStrike(LivingEntity npc, NpcCombatProfile profile,
                                     LivingEntity victim) {
        for (String technique : profile.techniques) {
            if (PredefinedTechniqueLookup.findStrike(technique) == null) {
                continue;
            }
            if (NpcStrikeDispatcher.fire(technique, npc, profile, victim)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryKi(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim) {
        double horiz = NpcBrainKiRotation.horizontal(victim.getX() - npc.getX(),
                victim.getZ() - npc.getZ());
        double dy = victim.getY() - npc.getY();
        if (!NpcBrainKiRotation.mayFireKi(profile.brainFly && NpcBrainKiRotation.needsClimb(dy),
                NpcBrainKiRotation.stackedForAim(horiz, dy))) {
            return false;
        }
        int cursor = KI_CURSOR.getOrDefault(npc.getUUID(), 0);
        String id = NpcBrainKiRotation.pick(profile, cursor, technique -> kiReady(npc, technique),
                preferredBands(npc.distanceTo(victim)));
        if (id == null) {
            return false;
        }
        String flag = switch (NpcBrainKiRotation.bandOf(id)) {
            case WAVE -> "kiWave";
            case BLAST -> "kiBlast";
            case DISK -> "kiDisk";
            case NAMED -> "kiNamed";
        };
        if (!profile.allowBrainAction(flag, npc.getRandom())) {
            return false;
        }
        boolean fired = fireKi(npc, profile, victim, id);
        if (fired) {
            KI_CURSOR.put(npc.getUUID(), cursor + 1);
        }
        return fired;
    }

    private static boolean kiReady(LivingEntity npc, String technique) {
        var data = PredefinedTechniqueLookup.find(technique);
        if (data == null) {
            return NpcKiCooldowns.ready(npc, technique);
        }
        return NpcKiCooldowns.ready(npc, data.getId());
    }

    private static boolean fireKi(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim,
                                  String id) {
        if ("kiblast".equals(id)) {
            if (!NpcKiCooldowns.ready(npc, id) || !NpcResources.spendEnergy(npc, profile, 2.0)) {
                return false;
            }
            NpcKiAttackDispatcher.fireKiBlast(npc, profile,
                    NpcKiAttackDispatcher.NO_DURATION_OVERRIDE, victim, 0);
            int cooldown = rangedCooldown(npc, profile);
            NpcKiCooldowns.consume(npc, "kiblast", cooldown);
            NpcKiCooldowns.consume(npc, "kiwave", cooldown);
            return true;
        }
        if ("kiwave".equals(id) || "kihame".equals(id) || "kamehame".equals(id)) {
            if (!NpcKiCooldowns.ready(npc, "kiwave") || !NpcResources.spendEnergy(npc, profile, 2.0)) {
                return false;
            }
            NpcKiAttackDispatcher.fireKiWave(npc, profile,
                    NpcKiAttackDispatcher.NO_DURATION_OVERRIDE, victim, 0);
            int cooldown = rangedCooldown(npc, profile);
            NpcKiCooldowns.consume(npc, "kiwave", cooldown);
            NpcKiCooldowns.consume(npc, "kiblast", cooldown);
            return true;
        }
        return NpcKiAttackDispatcher.fire(id, npc, profile,
                NpcKiAttackDispatcher.NO_DURATION_OVERRIDE, victim);
    }

    private static int rangedCooldown(LivingEntity npc, NpcCombatProfile profile) {
        int min = NpcCombatProfile.clampNpcRangedDelay(profile.npcRangedMinDelay);
        int max = Math.max(min, NpcCombatProfile.clampNpcRangedDelay(profile.npcRangedMaxDelay));
        return min == max ? min : npc.getRandom().nextInt(min, max + 1);
    }
}
