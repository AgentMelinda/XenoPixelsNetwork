package net.bullettrain.xenopixelsmod.compat.npc.brain.v2;

import net.bullettrain.xenopixelsmod.combat.KiDeflect;
import net.bullettrain.xenopixelsmod.compat.npc.NpcBrainKiRotation;
import net.bullettrain.xenopixelsmod.compat.npc.NpcChargeMoves;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatMoves;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFlightBridge;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFlightPolicy;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAim;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAttackDispatcher;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiDeflectPolicy;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiCooldowns;
import net.bullettrain.xenopixelsmod.compat.npc.NpcMeleeDamage;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTransformSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saga-style Combat Brain v2. Decision tree is a port of DMZ {@code SagasCombatBrain.decide}.
 * Movement is the saga air-chase already in {@link NpcBrainKiRotation}.
 */
public final class NpcSagaCombatBrain {
    static final int DECISION_INTERVAL = 6;
    private static final Map<UUID, Integer> NEXT_DECISION = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> KI_CURSOR = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> WAS_CASTING = new ConcurrentHashMap<>();
    private static final Set<UUID> FLIGHT = ConcurrentHashMap.newKeySet();

    public enum Type {
        MELEE,
        APPROACH,
        TELEPORT,
        CAST,
        COMBO,
        HOLD
    }

    public enum Locomotion {
        WALK,
        WALK_SLOW,
        RUN,
        DASH
    }

    public enum ComboRole {
        PRESSURE,
        STUN,
        HEAVY,
        RECOVERY
    }

    public record Intent(Type type, Locomotion locomotion, ComboRole combo,
                         NpcBrainKiRotation.Band kiBand) {
        public static Intent melee() {
            return new Intent(Type.MELEE, Locomotion.WALK, null, null);
        }

        public static Intent approach(Locomotion mode) {
            return new Intent(Type.APPROACH, mode == null ? Locomotion.RUN : mode, null, null);
        }

        public static Intent teleport() {
            return new Intent(Type.TELEPORT, Locomotion.RUN, null, null);
        }

        public static Intent hold() {
            return new Intent(Type.HOLD, Locomotion.WALK, null, null);
        }

        public static Intent cast(NpcBrainKiRotation.Band band) {
            return new Intent(Type.CAST, Locomotion.WALK, null, band);
        }

        public static Intent combo(ComboRole role) {
            return new Intent(Type.COMBO, Locomotion.WALK, role, null);
        }
    }

    @FunctionalInterface
    public interface Chance {
        boolean roll(float probability);
    }

    private NpcSagaCombatBrain() {}

    public static void forget(UUID npcId) {
        if (npcId == null) {
            return;
        }
        NEXT_DECISION.remove(npcId);
        KI_CURSOR.remove(npcId);
        WAS_CASTING.remove(npcId);
        FLIGHT.remove(npcId);
        NpcSagaCombos.forget(npcId);
    }

    public static void disengage(LivingEntity npc) {
        if (npc == null) {
            return;
        }
        NpcChargeMoves.cancel(npc);
        NpcCombatMoves.guard(npc, false);
        NpcSagaCombos.forget(npc.getUUID());
        NEXT_DECISION.remove(npc.getUUID());
        land(npc, NpcCombatProfile.readCached(npc));
    }

    public static Intent decide(NpcSagaCombatContext ctx, Chance chance) {
        Chance roll = chance == null ? p -> false : chance;
        if (ctx == null) {
            return Intent.hold();
        }
        double d = ctx.dist3D;
        if (ctx.selfHpPct < 0.25f && ctx.comboReady && !ctx.targetApproaching() && ctx.allowRecovery) {
            return Intent.combo(ComboRole.RECOVERY);
        }
        if (ctx.advanced && ctx.targetCasting) {
            if (!(d <= NpcSagaCombatContext.MELEE)) {
                return Intent.approach(Locomotion.WALK_SLOW);
            }
            return ctx.comboReady ? Intent.combo(ComboRole.STUN) : Intent.melee();
        }
        if (ctx.advanced && (ctx.targetHelpless || ctx.targetTransforming)) {
            if (d <= NpcSagaCombatContext.MELEE && ctx.comboReady) {
                return Intent.combo(ComboRole.HEAVY);
            }
            if (ctx.hasHitscan || ctx.hasGuardBreak) {
                return Intent.cast(ctx.hasHitscan
                        ? NpcBrainKiRotation.Band.BLAST : NpcBrainKiRotation.Band.WAVE);
            }
        }
        if (ctx.targetBlocking) {
            if (ctx.hasGuardBreak && d <= NpcSagaCombatContext.OUT) {
                return Intent.cast(NpcBrainKiRotation.Band.WAVE);
            }
            return d <= NpcSagaCombatContext.MELEE && ctx.comboReady
                    ? Intent.combo(ComboRole.PRESSURE) : Intent.melee();
        }
        if (d > NpcSagaCombatContext.OUT) {
            return reposition(ctx, roll);
        }
        if (d > NpcSagaCombatContext.MID) {
            if (ctx.targetApproaching()) {
                if (!ctx.hasHitscan && ctx.hasTravel && roll.roll(0.6f)) {
                    return Intent.cast(NpcBrainKiRotation.Band.WAVE);
                }
                return Intent.approach(Locomotion.RUN);
            }
            if (ctx.hasTravel || ctx.hasZoning) {
                return Intent.cast(ctx.hasTravel
                        ? NpcBrainKiRotation.Band.WAVE : NpcBrainKiRotation.Band.DISK);
            }
            return reposition(ctx, roll);
        }
        if (d > NpcSagaCombatContext.MELEE) {
            float castChance = ctx.advanced ? 0.55f : 0.65f;
            if (ctx.hasMidKi && roll.roll(castChance)) {
                return Intent.cast(NpcBrainKiRotation.Band.BLAST);
            }
            return Intent.approach(Locomotion.RUN);
        }
        if (!ctx.allowMelee) {
            return Intent.hold();
        }
        return ctx.comboReady && ctx.allowCombo && roll.roll(0.6f)
                ? Intent.combo(ComboRole.PRESSURE) : Intent.melee();
    }

    private static Intent reposition(NpcSagaCombatContext ctx, Chance roll) {
        if (ctx.vanishReady && roll.roll(0.4f)) {
            return Intent.teleport();
        }
        if (ctx.dashReady && roll.roll(0.5f)) {
            return Intent.approach(Locomotion.DASH);
        }
        return Intent.approach(Locomotion.RUN);
    }

    public static void steer(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim) {
        if (npc == null || profile == null) {
            return;
        }
        // Just knocked back: let the push play out before steering again (net.bullettrain.xenopixelsmod.compat.npc.NpcKnockbackGrace).
        if (net.bullettrain.xenopixelsmod.compat.npc.NpcKnockbackGrace.active(npc)) return;
        if (!profile.combatBrain || !NpcFlightPolicy.canCombatFly(profile)) {
            land(npc, profile);
        }
        if (!profile.combatBrain) return;
        if ((!profile.brainCharge || profile.brainChance("charge") <= 0)
                && NpcChargeMoves.isCharging(npc)) {
            NpcChargeMoves.cancel(npc);
        }
        dodgeReact(npc, profile, victim);
        boolean blast = profile.allowBrainAction("deflectBlast", npc.getRandom());
        boolean wave = profile.allowBrainAction("deflectWave", npc.getRandom());
        double deflectDist = victim != null && victim.isAlive() && victim != npc
                ? npc.distanceTo(victim) : Double.POSITIVE_INFINITY;
        if ((blast || wave) && NpcKiDeflectPolicy.allow(deflectDist,
                net.bullettrain.xenopixelsmod.config.XenoServerConfig.clampedBrainDeflectMinDistance())) {
            String key = blast ? "deflectBlast" : "deflectWave";
            KiDeflect.tryNpcDeflect(npc, profile.brainModifier(key), blast, wave);
        }
        if (victim == null || !victim.isAlive() || victim == npc) {
            land(npc, profile);
            stopFighting(npc);
            return;
        }
        // Grounding has priority over an active combo/charge. Otherwise holdFlight() keeps
        // gravity off until that action ends, even after the target has landed.
        boolean targetOnGround = NpcFlightPolicy.targetGrounded(victim);
        if (targetOnGround) {
            land(npc, profile);
        }
        boolean senseLocked = net.bullettrain.xenopixelsmod.compat.npc.NpcTargetKeeper
                .isKiSenseLockedOn(npc, victim);
        if (NpcKiAttackDispatcher.isOwnerClashing(npc) || NpcSagaCombos.active(npc.getUUID())
                || NpcChargeMoves.isCharging(npc) || NpcTransformSystem.isHolding(npc.getUUID())) {
            holdFlight(npc);
            if (!senseLocked) {
                NpcKiAim.applyLook(npc, NpcBrainKiRotation.targetYaw(
                        victim.getX() - npc.getX(), victim.getZ() - npc.getZ(), npc.getYRot()), 0.0f);
            }
            return;
        }
        double dx = victim.getX() - npc.getX();
        double dy = victim.getY() - npc.getY();
        double dz = victim.getZ() - npc.getZ();
        boolean climbing = NpcBrainKiRotation.needsClimb(dy);
        // V6 and V7 fly the DMZ way too. That movement model is the closer match to how a saga
        // mob actually moves, and the two DMZ-driven brains are the last place that should be
        // using the older ground-biased chase.
        boolean v3 = profile.brainVersion != null
                && (profile.brainVersion.isV3() || profile.brainVersion.isDmzPort());
        boolean flyActionEnabled = profile.brainVersion != null
                && !profile.brainVersion.honoursToggles() || profile.brainFly;
        boolean wantFly = !targetOnGround && (v3
                ? NpcBrainKiRotation.shouldFlyV3(flyActionEnabled, dy,
                        directsFlight(npc.getUUID()), targetOnGround)
                : NpcBrainKiRotation.shouldFly(flyActionEnabled, targetOnGround, climbing))
                && NpcFlightPolicy.canCombatFly(profile);
        if (wantFly) {
            claimFlight(npc, profile);
            double flySpeed = NpcBrainKiRotation.FLY_SPEED * profile.brainModifier("fly");
            double[] fly = npc instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity
                    ? NpcBrainKiRotation.airChaseVelocity(dx, dy, dz, flySpeed,
                            NpcBrainKiRotation.airHoverDistance(npc.getBbWidth(), victim.getBbWidth()))
                    : NpcBrainKiRotation.airChaseVelocity(dx, dy, dz, flySpeed);
            npc.setDeltaMovement(new Vec3(fly[0], fly[1], fly[2]));
            npc.hurtMarked = true;
            npc.hasImpulse = true;
            if (npc instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            if (!senseLocked) {
                NpcKiAim.applyLook(npc, NpcBrainKiRotation.targetYaw(dx, dz, npc.getYRot()), 0.0f);
            }
            return;
        }
        if (targetOnGround || (v3 && NpcBrainKiRotation.shouldLandV3(npc.onGround(), dy))) {
            land(npc, profile);
        } else if (profile.brainLand || !profile.brainFly) {
            land(npc, profile);
        }
        if (!senseLocked) {
            NpcKiAim.applyLook(npc, NpcBrainKiRotation.targetYaw(dx, dz, npc.getYRot()), 0.0f);
        }
    }

    public static void tick(MinecraftServer server, LivingEntity npc, NpcCombatProfile profile,
                            LivingEntity victim, int serverTick) {
        if (npc == null || profile == null || !profile.combatBrain) {
            return;
        }
        NpcSagaCombos.advance(npc, profile, serverTick);
        if (victim == null || !victim.isAlive() || victim == npc) {
            stopFighting(npc);
            return;
        }
        if (NpcKiAttackDispatcher.isOwnerClashing(npc) || NpcSagaCombos.active(npc.getUUID())
                || NpcChargeMoves.isCharging(npc) || NpcTransformSystem.isHolding(npc.getUUID())) {
            return;
        }
        Integer next = NEXT_DECISION.get(npc.getUUID());
        if (next != null && serverTick < next) {
            return;
        }
        NEXT_DECISION.put(npc.getUUID(), serverTick + DECISION_INTERVAL);
        NpcSagaCombatContext ctx = NpcSagaCombatContext.snapshot(npc, victim, profile);
        ctx.comboReady = ctx.comboReady && !NpcSagaCombos.active(npc.getUUID());
        Intent intent = decide(ctx, p -> npc.getRandom().nextFloat() < p);
        execute(npc, profile, victim, intent, serverTick);
    }

    static void execute(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim,
                        Intent intent, int serverTick) {
        if (intent == null || npc == null || victim == null) {
            return;
        }
        switch (intent.type()) {
            case MELEE -> {
                // The tree reads DMZ's 4.5-block band; the swing reaches less. Out of reach, close
                // in rather than stand there - which is what "cant hit me" looked like (2026-09-29).
                if (net.bullettrain.xenopixelsmod.compat.npc.NpcCombatRanges.withinMelee(npc, victim)) {
                    melee(npc, victim);
                } else {
                    approach(npc, profile, victim, Locomotion.RUN);
                }
            }
            case APPROACH -> approach(npc, profile, victim, intent.locomotion());
            case TELEPORT -> {
                if (profile.brainVanish) {
                    NpcCombatMoves.vanish(npc, victim, 0);
                }
            }
            case CAST -> {
                if (npc.getRandom().nextFloat() < 0.5f) {
                    cast(npc, profile, victim, intent.kiBand());
                } else {
                    approach(npc, profile, victim, Locomotion.RUN);
                }
            }
            case COMBO -> {
                if (!NpcSagaCombos.start(npc, profile, victim, intent.combo(), serverTick)) {
                    approach(npc, profile, victim, Locomotion.RUN);
                }
            }
            case HOLD -> {
            }
        }
    }

    private static void melee(LivingEntity npc, LivingEntity victim) {
        NpcSagaCombos.swingWithoutClip(npc);
        NpcMeleeDamage.hit(npc, victim, 1.0f);
    }

    private static void approach(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim,
                                 Locomotion mode) {
        // The DMZ air-chase in steer() owns velocity while the NPC is flying. The optional V8/Xeno
        // chase move teleports onto a ground landing point; firing it from a six-tick combat
        // decision interrupted flight and made the NPC appear to stall or snap toward its target.
        boolean airChaseActive = directsFlight(npc.getUUID());
        if (!NpcFlightPolicy.canUseGroundChase(profile, airChaseActive)) {
            return;
        }
        if (mode == Locomotion.DASH) {
            if (NpcCombatMoves.chase(npc, victim)) {
                return;
            }
        }
        // RUN keeps walking continuously. Only the explicit DASH intent may teleport.
        // Close but out of reach - a floor below, across a gap - or the chase refused: walk there,
        // or step forward off the edge onto the target (2026-09-30).
        net.bullettrain.xenopixelsmod.compat.npc.NpcLedgeApproach.apply(npc, victim, 1.2);
    }

    private static void cast(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim,
                             NpcBrainKiRotation.Band band) {
        int cursor = KI_CURSOR.getOrDefault(npc.getUUID(), 0);
        String id = NpcBrainKiRotation.pick(profile, cursor, technique ->
                (band == null || NpcBrainKiRotation.bandOf(technique) == band)
                        && NpcKiCooldowns.ready(npc, technique));
        if (id == null) {
            return;
        }
        boolean fired = fireKi(npc, profile, victim, id);
        if (fired) {
            KI_CURSOR.put(npc.getUUID(), cursor + 1);
        }
    }

    private static boolean fireKi(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim,
                                  String id) {
        if ("kiblast".equals(id)) {
            if (!NpcKiCooldowns.ready(npc, id)) {
                return false;
            }
            NpcKiAttackDispatcher.fireKiBlast(npc, profile,
                    NpcKiAttackDispatcher.NO_DURATION_OVERRIDE, victim, 0);
            NpcKiCooldowns.consume(npc, "kiblast", NpcBrainKiRotation.GENERIC_KI_COOLDOWN);
            return true;
        }
        if ("kiwave".equals(id) || "kihame".equals(id) || "kamehame".equals(id)) {
            if (!NpcKiCooldowns.ready(npc, "kiwave")) {
                return false;
            }
            NpcKiAttackDispatcher.fireKiWave(npc, profile,
                    NpcKiAttackDispatcher.NO_DURATION_OVERRIDE, victim, 0);
            NpcKiCooldowns.consume(npc, "kiwave", NpcBrainKiRotation.GENERIC_KI_COOLDOWN);
            return true;
        }
        return NpcKiAttackDispatcher.fire(id, npc, profile,
                NpcKiAttackDispatcher.NO_DURATION_OVERRIDE, victim);
    }

    private static void dodgeReact(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim) {
        if (!profile.brainVanish || victim == null) {
            WAS_CASTING.remove(npc.getUUID());
            return;
        }
        NpcSagaCombatContext ctx = new NpcSagaCombatContext();
        if (victim instanceof net.minecraft.server.level.ServerPlayer) {
            ctx = NpcSagaCombatContext.snapshot(npc, victim, profile);
        }
        boolean was = Boolean.TRUE.equals(WAS_CASTING.get(npc.getUUID()));
        if (was && !ctx.targetCasting && ctx.targetFiring && npc.getRandom().nextFloat() < 0.35f) {
            NpcCombatMoves.vanish(npc, victim, npc.getRandom().nextBoolean() ? -1 : 1);
        }
        WAS_CASTING.put(npc.getUUID(), ctx.targetCasting);
    }

    /** Dead or missing victim: drop combo strings and looping punch clips. */
    public static void stopFighting(LivingEntity npc) {
        if (npc == null) {
            return;
        }
        NpcSagaCombos.forget(npc.getUUID());
        net.bullettrain.xenopixelsmod.compat.npc.NpcCombatBrain.disengage(npc);
    }

    /** True while the saga brain owns this NPC's flying navigator. */
    public static boolean directsFlight(UUID npcId) {
        return npcId != null && FLIGHT.contains(npcId);
    }

    private static void claimFlight(LivingEntity npc, NpcCombatProfile profile) {
        FLIGHT.add(npc.getUUID());
        npc.setNoGravity(true);
        net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour.claimMovement(npc,
                net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.COMBAT);
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
        NpcFlightBridge.setFlying(npc, false);
        Vec3 velocity = npc.getDeltaMovement();
        npc.setDeltaMovement(velocity.x * 0.15, Math.min(velocity.y, 0.0), velocity.z * 0.15);
        net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.release(npc,
                net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.COMBAT);
    }

    /** Holds an airborne NPC in place while a ki clash, charge, transform, or combo owns it. */
    private static void holdFlight(LivingEntity npc) {
        if (!directsFlight(npc.getUUID())) return;
        npc.setNoGravity(true);
        if (net.bullettrain.xenopixelsmod.compat.npc.NpcKnockbackGrace.active(npc)) return;
        npc.setDeltaMovement(Vec3.ZERO);
        if (npc instanceof Mob mob) mob.getNavigation().stop();
    }
}
