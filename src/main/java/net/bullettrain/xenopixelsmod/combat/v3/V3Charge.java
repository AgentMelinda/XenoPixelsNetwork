package net.bullettrain.xenopixelsmod.combat.v3;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.MeleeAnimationS2C;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.anim.CombatStateAnim;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.v2.V2ChargeRules;
import net.bullettrain.xenopixelsmod.combat.v2.V2ChargedArc;
import net.bullettrain.xenopixelsmod.combat.v2.V2ChargedLanding;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * V3 charged punch and kick. The server's clock owns the charge: the client only says when the
 * hold started and when it was let go.
 *
 * <p>Timing, the fourth-release cadence, the landing spot and the kick arc are the existing v2
 * rules; the saved cadence counters are the same NBT keys, so switching controllers neither
 * resets nor duplicates a player's count.
 */
public final class V3Charge {
    private static final float PUNCH_SCALE = 1.9f;
    private static final float KICK_SCALE = 1.8f;
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1f, 0.72f, 0.08f), 1.1f);

    private V3Charge() {}

    /** The saved count after a release and whether that release teleports. */
    public record Cadence(int count, boolean teleport) {}

    static String countKey(boolean kick) {
        return kick ? "xenopixelsmod.v2_charged_kick_count" : "xenopixelsmod.v2_charged_punch_count";
    }

    /**
     * Only a fully charged release at 7-20 blocks with a clear line counts. Every fourth such
     * release teleports; one that has nowhere to land keeps its turn instead of spending it.
     */
    static Cadence cadence(int saved, float charge, double distance, boolean sight, boolean landingOpen) {
        if (!sight || !V2ChargeRules.eligible(charge, distance)) return new Cadence(saved, false);
        int next = V2ChargeRules.nextCount(saved);
        if (next == 0 && !landingOpen) return new Cadence(saved, false);
        return new Cadence(next, next == 0);
    }

    static float progress(long startTick, long now) {
        return now <= startTick ? 0f : V2ChargeRules.progress((int) Math.min(now - startTick, V2ChargeRules.MAX_TICKS));
    }

    static float damageScale(boolean kick, float charge) {
        float full = kick ? KICK_SCALE : PUNCH_SCALE;
        return 1f + (full - 1f) * Math.clamp(charge, 0f, 1f);
    }

    static boolean startAllowed(long now, long readyTick, long activeStartTick) {
        return activeStartTick < 0 && now >= readyTick;
    }

    static boolean becameFullyCharged(int previousTicks, int currentTicks) {
        return previousTicks < V2ChargeRules.FULL_TICKS && currentTicks >= V2ChargeRules.FULL_TICKS;
    }

    static boolean releaseAllowed(long startTick, long now, int startSequence, int releaseSequence) {
        return startTick >= 0 && now > startTick && startSequence >= 0 && releaseSequence > startSequence;
    }

    /** A reentrant callback must not let an old strike write a newer gesture's state. */
    static boolean ownsReleasedStrike(V3Fighter original, V3Fighter current, UUID session, int sequence) {
        return original != null && current == original && session != null && session.equals(current.session())
                && current.acknowledgedSequence() == sequence && current.state == V3State.IDLE
                && current.chargeStartTick < 0 && current.motion.owner() == null;
    }

    private static boolean canFinishRelease(ServerPlayer player, LivingEntity target, V3Fighter fighter,
                                            UUID session, int sequence) {
        return ownsReleasedStrike(fighter, V3FighterStore.peek(player), session, sequence)
                && target != null && target.isAlive() && !target.isRemoved()
                && V3CombatServer.canContinueTechnique(player, session, target.getUUID())
                && V3Targeting.resolve(player) == target;
    }

    public static boolean start(ServerPlayer player, LivingEntity target, boolean kick, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || target == null || fighter.state != V3State.IDLE
                || !startAllowed(now, fighter.heavyReadyTick, fighter.chargeStartTick)
                || !V3Heavy.emptyHands(player) || V3Heavy.stunned(player)) return false;
        fighter.chargeStartTick = now;
        fighter.chargeStartSequence = fighter.acknowledgedSequence();
        fighter.chargeKick = kick;
        fighter.chargeTicks = 0;
        fighter.state = kick ? V3State.CHARGING_KICK : V3State.CHARGING_PUNCH;
        V3AttackSounds.chargeStart(player, kick);
        String hold = CombatStateAnim.resolve(player, kick ? TechniqueAnimSlot.CHARGE_KICK : TechniqueAnimSlot.CHARGE_PUNCH);
        try {
            NetworkHandler.sendToTrackingEntityAndSelf(new TriggerAnimationS2C(player.getUUID(),
                    TriggerAnimationS2C.AnimationType.KI_ANIMATION, 1, player.getId(), hold), player);
        } catch (RuntimeException | LinkageError unavailable) {
            // Presentation only.
        }
        return true;
    }

    public static void tick(ServerPlayer player, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || fighter.chargeStartTick < 0) return;
        boolean charging = fighter.state == V3State.CHARGING_PUNCH || fighter.state == V3State.CHARGING_KICK;
        if (!charging) {
            // Something else (lost lock, session change) already cancelled the live state.
            fighter.chargeStartTick = -1;
            stopHold(player);
            return;
        }
        long elapsed = now - fighter.chargeStartTick;
        if (elapsed > V2ChargeRules.MAX_TICKS || V3Heavy.stunned(player) || !V3Heavy.emptyHands(player)) {
            cancel(player);
            return;
        }
        int ticks = (int) Math.min(elapsed, V2ChargeRules.FULL_TICKS);
        if (ticks != fighter.chargeTicks) {
            if (becameFullyCharged(fighter.chargeTicks, ticks)) V3AttackSounds.chargeReady(player);
            fighter.chargeTicks = ticks;
            // The glow follows this value; every second tick is plenty for a 20-tick fill.
            if ((ticks & 1) == 0 || ticks == V2ChargeRules.FULL_TICKS) V3CombatServer.syncState(player);
        }
        if ((now & 3) == 0) {
            float progress = V2ChargeRules.progress(ticks);
            player.serverLevel().sendParticles(GOLD, player.getX(), player.getY() + 0.9, player.getZ(),
                    3 + (int) (progress * 9), 0.4 + progress * 0.25, 0.7, 0.4 + progress * 0.25, 0);
        }
    }

    public static void cancel(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || fighter.chargeStartTick < 0) return;
        fighter.chargeStartTick = -1;
        fighter.chargeStartSequence = -1;
        fighter.chargeTicks = 0;
        if (fighter.state == V3State.CHARGING_PUNCH || fighter.state == V3State.CHARGING_KICK) {
            fighter.state = V3State.IDLE;
        }
        stopHold(player);
        V3CombatServer.syncState(player);
    }

    /** @return true when a charged strike was thrown, whether or not it connected */
    public static boolean release(ServerPlayer player, LivingEntity target, boolean kick, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || fighter.chargeStartTick < 0) return false;
        UUID session = fighter.session();
        int sequence = fighter.acknowledgedSequence();
        long startedAt = fighter.chargeStartTick;
        int startSequence = fighter.chargeStartSequence;
        boolean matches = fighter.chargeKick == kick
                && (fighter.state == V3State.CHARGING_PUNCH || fighter.state == V3State.CHARGING_KICK);
        boolean admittedRelease = releaseAllowed(startedAt, now, startSequence, fighter.acknowledgedSequence());
        float charge = progress(startedAt, now);
        cancel(player);
        if (!matches || !admittedRelease || target == null || now < fighter.heavyReadyTick
                || V3Heavy.stunned(player) || !V3Heavy.emptyHands(player)
                || !canFinishRelease(player, target, fighter, session, sequence)) return false;

        V3Config.Values config = V3Config.get();
        String key = countKey(kick);
        int saved = player.getPersistentData().getInt(key);
        boolean sight = V3LoadedVisibility.visible(player, target);
        boolean wouldTeleport = sight && V2ChargeRules.eligible(charge, player.distanceTo(target))
                && V2ChargeRules.nextCount(saved) == 0;
        Vec3 landing = wouldTeleport ? V2ChargedLanding.find(player, target, kick) : null;
        // A mandatory fourth-release landing is part of admission. Refusal spends no count, cost
        // or cooldown and leaves the fourth turn ready for the next charged release.
        if (wouldTeleport && landing == null) return false;

        // The charged kick is a heavy-button action and pays the heavy price; refused, it costs
        // nothing and leaves the cadence count alone.
        if (kick) {
            float cost = player.isCreative() ? 0f : (float) config.heavyAttackerStaminaCost();
            if (fighter.heavy.start(session, sequence, true,
                    V3Resources.stamina(player), cost) != V3Heavy.Start.STARTED) return false;
        }

        Cadence cadence = cadence(saved, charge, player.distanceTo(target), sight, landing != null);
        if (cadence.teleport()) {
            float yaw = (float) (Math.toDegrees(Math.atan2(target.getZ() - landing.z, target.getX() - landing.x)) - 90);
            player.connection.teleport(landing.x, landing.y, landing.z, yaw, 0);
            if (!canFinishRelease(player, target, fighter, session, sequence)) return false;
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
            player.yBodyRot = yaw;
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            player.hasImpulse = true;
            player.fallDistance = 0f;
        }

        if (!canFinishRelease(player, target, fighter, session, sequence)) return false;
        if (cadence.count() != saved) player.getPersistentData().putInt(key, cadence.count());
        fighter.heavyReadyTick = (long) now + V3Heavy.COOLDOWN_TICKS;
        String fire = CombatStateAnim.resolve(player,
                kick ? TechniqueAnimSlot.CHARGE_KICK_FIRE : TechniqueAnimSlot.CHARGE_PUNCH_FIRE);
        try {
            NetworkHandler.sendToTrackingEntityAndSelf(new MeleeAnimationS2C(player.getId(), fire, false, 1.0f), player);
        } catch (RuntimeException | LinkageError unavailable) {
            // Presentation only.
        }
        V3AttackSounds.chargeRelease(player, charge);
        if (!V3Heavy.inReach(player, target)) {
            V3AttackSounds.whiff(player);
            return true;
        }

        float accepted = V3Heavy.strike(player, target, damageScale(kick, charge));
        // Hurt listeners can replace the lock/session or start a different action. The damage
        // already occurred, but this old strike must not drain stamina or apply a late launch.
        if (!canFinishRelease(player, target, fighter, session, sequence)
                || !CombatKnockback.canKnockBack(target)) return true;
        if (kick) {
            fighter.heavy.accept(session, sequence, accepted,
                    V3Resources.victimStamina(target), (float) config.heavyVictimStaminaDrain());
        }
        if (accepted > 0f) {
            if (!kick && charge >= 1f) V3AttackSounds.chargedExplosion(player, target);
            else V3AttackSounds.heavyHit(player, target);
        } else {
            V3AttackSounds.contact(player, target);
        }
        if (accepted > 0f) {
            Vec3 away = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
            if (away.lengthSqr() < 1.0e-6) away = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
            if (away.lengthSqr() < 1.0e-6) away = new Vec3(0, 0, 1);
            if (kick && cadence.teleport()) {
                // The explicit launch of V3: twenty blocks, six-block apex, collision-aware.
                V2ChargedArc.start(target, away, now);
                // The one intentional launch in V3, so the one thing a chase can follow.
                V3Chase.open(player, target);
            } else {
                CombatKnockback.set(target, V3Heavy.reaction(V3Direction.FORWARD, away.x, 0, away.z), player);
            }
            if (player.level() instanceof ServerLevel level) {
                boolean explosion = !kick && charge >= 1f;
                CombatFx.impact(level, player, target, away,
                        explosion ? CombatFx.Weight.ULTIMATE : CombatFx.Weight.HEAVY);
            }
        }
        return true;
    }

    private static void stopHold(ServerPlayer player) {
        try {
            NetworkHandler.sendToTrackingEntityAndSelf(new TriggerAnimationS2C(player.getUUID(),
                    TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP, 0, player.getId(), ""), player);
        } catch (RuntimeException | LinkageError unavailable) {
            // Presentation only.
        }
    }
}
