package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.api.event.GrabEvent;
import net.bullettrain.xenopixelsmod.combat.Bt3CinematicRushSystem;
import net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.combat.DragonHoming;
import net.bullettrain.xenopixelsmod.combat.HakaiChannelSystem;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.combat.combo.ComboRouteMachine;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.combat.v2.grab.GrabRules;
import net.bullettrain.xenopixelsmod.network.ChaseFlightSystem;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.Bt3AnimIntentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Grab and throw.
 *
 * <p>The answer to guard, which until now had none short of draining a defender's stamina. A
 * grab ignores a block, but it has startup, so a defender who swings instead of blocking hits the
 * grabber out of it. Once it connects the victim is held in front of the grabber for a fixed time
 * and then thrown in whichever direction the grabber is holding. A player victim can break the
 * hold by pressing grab back inside the tech window.
 *
 * <p>Timing is {@link GrabRules}. The hold is re-validated every tick and released the moment
 * anything about it stops being true, the same shape the Hakai channel uses, so a victim can
 * never be left pinned by a grabber who logged out, died or changed dimension.
 */
final class V2Grab {

    /** How much of a grab's damage lands when it connects; the rest lands with the throw. */
    private static final float CONNECT_SHARE = 0.25f;

    /** The reach-in a grab starts with when no combo route says otherwise. */
    static final Bt3AnimationIntent REACH_POSE = Bt3AnimationIntent.STEP_IN_DASH;

    private V2Grab() {}

    /**
     * @param pose what the grabber is seen doing as they reach in: the grab beat's own pose when
     *             a v2 combo route led here, {@link #REACH_POSE} otherwise
     */
    static void start(ServerPlayer player, V2Fighter f, LivingEntity target, V2Direction direction, int now,
                      Bt3AnimationIntent pose) {
        V2Config.Values cfg = V2Config.get();
        if (!cfg.grabEnabled || now < f.grabReadyTick) return;
        // A grab needs both hands, and these two have them: a cinematic rush until its last
        // impact, a Hakai for as long as it is channelled.
        if (Bt3CinematicRushSystem.isActive(player) || HakaiChannelSystem.isChanneling(player)) return;
        // Only the fighter's lock can be grabbed, and only from arm's length.
        if (!inReach(player, target, cfg.grabRange)) return;
        String refusal = refusal(player, target);
        if (refusal != null) {
            V2Support.hint(player, refusal);
            return;
        }
        if (!V2Support.spend(player, 0f, cfg.grabStaminaCost)) {
            V2Support.hint(player, "Not enough stamina");
            return;
        }
        f.clearCombo();
        V2Motion.stopTravel(player, f);
        // The legacy chase is the same thing under the other controllers, and ends the same way.
        ChaseFlightSystem.stopChase(player);
        f.grabVictimId = target.getId();
        f.grabStartTick = now;
        f.grabConnectTick = -1;
        f.grabDirection = direction == null ? V2Direction.NONE : direction;
        f.grabReadyTick = now + cfg.grabCooldownTicks;
        f.state = V2State.GRAB_STARTUP;
        V2Support.faceBody(player, target.getX(), target.getZ());
        Bt3CombatEvents.setGuarding(player, false);
        DmzAnimHelper.broadcastBlockStop(player);
        pose(player, pose == null ? REACH_POSE : pose);
    }

    private static void pose(ServerPlayer player, Bt3AnimationIntent intent) {
        ModNetwork.sendToTrackingAndSelf(player, new Bt3AnimIntentPacket(player.getId(), intent));
    }

    /**
     * What the grabber is seen doing as the victim leaves their hands. There are no throw clips
     * yet, so each direction borrows the strike that sends a target the same way: the blow that
     * ends a string for a throw forward, the launcher for a throw up.
     */
    static Bt3AnimationIntent throwPose(V2Direction direction) {
        if (direction == null) return Bt3AnimationIntent.HEAVY_FINISH;
        return switch (direction) {
            case UP -> Bt3AnimationIntent.UPPERCUT_RIGHT;
            case DOWN -> Bt3AnimationIntent.BODY_PUNCH_RIGHT;
            case BACK -> Bt3AnimationIntent.HOOK_LEFT;
            default -> Bt3AnimationIntent.HEAVY_FINISH;
        };
    }

    /** The grabber changed the held direction during the hold: the throw follows the latest. */
    static void redirect(V2Fighter f, V2Direction direction) {
        if (f.state == V2State.GRAB_HOLD || f.state == V2State.GRAB_STARTUP) {
            f.grabDirection = direction == null ? V2Direction.NONE : direction;
        }
    }

    static void tick(ServerPlayer player, V2Fighter f, int now) {
        if (f.state != V2State.GRAB_STARTUP && f.state != V2State.GRAB_HOLD) return;
        LivingEntity victim = V2Support.living(player, f.grabVictimId);
        if (victim == null) {
            release(player, f, null);
            return;
        }
        V2Config.Values cfg = V2Config.get();
        GrabRules.Phase phase = GrabRules.phase(
                now - f.grabStartTick, cfg.grabStartupTicks, cfg.grabHoldTicks);
        switch (phase) {
            case STARTUP -> {
            }
            case CONNECT -> connect(player, f, victim, now);
            case HOLD -> {
                if (f.state != V2State.GRAB_HOLD) {
                    release(player, f, victim);
                } else {
                    pin(player, victim);
                }
            }
            case THROW -> {
                if (f.state == V2State.GRAB_HOLD) {
                    throwVictim(player, f, victim, now);
                } else {
                    release(player, f, victim);
                }
            }
        }
    }

    private static void connect(ServerPlayer player, V2Fighter f, LivingEntity victim, int now) {
        // A vanish gets out of a grab the same way it gets out of a punch.
        boolean dodged = victim instanceof ServerPlayer defender && V2Support.invulnerable(defender, now);
        // Half a block of slack over the start range: the victim may have drifted during startup,
        // and a grab that visibly reached them should not whiff on a rounding error.
        if (dodged || refusal(player, victim) != null
                || !inReach(player, victim, V2Config.get().grabRange + 0.5)
                || NeoForge.EVENT_BUS.post(new GrabEvent.Connect(player, victim)).isCanceled()) {
            release(player, f, null);
            return;
        }
        // A grab goes through a block, so the guard comes down before the grab lands rather than
        // after: otherwise the hit below is a blocked one, costing the defender the stamina of a
        // block and drawing the "blocked" flash for the one attack a block does not stop.
        ServerPlayer blocker = victim instanceof ServerPlayer d && Bt3CombatEvents.isGuarding(d) ? d : null;
        if (blocker != null) Bt3CombatEvents.setGuarding(blocker, false);
        // The grab lands as a hit, and a grab that cannot hurt cannot hold. Holding and throwing
        // are not damage, so nothing that protects an entity by refusing damage (a claim, a safe
        // zone, an invulnerable NPC) would otherwise stop it being picked up and thrown.
        if (!V2Damage.strike(player, victim, V2Config.get().grabDamageScale * CONNECT_SHARE)) {
            // Refused: the defender is exactly as they were, guard included.
            if (blocker != null) Bt3CombatEvents.setGuarding(blocker, true);
            release(player, f, null);
            return;
        }
        f.state = V2State.GRAB_HOLD;
        f.grabConnectTick = now;
        if (victim instanceof ServerPlayer defender) {
            if (blocker != null) DmzAnimHelper.broadcastBlockStop(defender);
            // Whatever the defender had under way stops here, under whichever controller it was
            // started: nothing of theirs may keep driving a body that is being held.
            Bt3CinematicRushSystem.interrupt(defender);
            ComboRouteMachine.cancel(defender);
            ChaseFlightSystem.stopChase(defender);
            V2Fighter held = V2FighterStore.get(defender);
            V2Motion.stopTravel(defender, held);
            V2Rush.interrupt(defender, held);
            held.clearCombo();
            held.state = V2State.GRABBED;
            held.grabbedById = player.getId();
            held.grabbedSinceTick = now;
        }
        pin(player, victim);
        CombatFx.cue(player.serverLevel(), victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0),
                CombatFxKind.GUARD_BLOCK, 1.0f);
    }

    /** Holds the victim in front of the grabber for one tick. */
    static void pin(ServerPlayer player, LivingEntity victim) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0e-4) flat = new Vec3(0.0, 0.0, 1.0);
        flat = flat.normalize();
        double distance = GrabRules.holdDistance(player.getBbWidth(), victim.getBbWidth());
        Vec3 at = player.position().add(flat.scale(distance)).add(0.0, 0.1, 0.0);
        if (victim instanceof ServerPlayer held) {
            float yaw = V2Support.yawToward(held, player.getX(), player.getZ());
            held.connection.teleport(at.x, at.y, at.z, yaw, held.getXRot());
        } else {
            victim.setPos(at.x, at.y, at.z);
        }
        victim.setDeltaMovement(Vec3.ZERO);
        victim.hasImpulse = true;
        victim.fallDistance = 0f;
        player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
        player.hasImpulse = true;
    }

    private static void throwVictim(ServerPlayer player, V2Fighter f, LivingEntity victim, int now) {
        HitReaction reaction = HitReaction.throwFor(f.grabDirection);
        float scale = V2Config.get().grabDamageScale * (1f - CONNECT_SHARE);
        float damage = V2Damage.nominal(player, scale);
        pose(player, throwPose(f.grabDirection));
        release(player, f, victim);
        V2Damage.strike(player, victim, scale);
        V2Support.reactAlongLook(player, victim, reaction);
        V2Strikes.openHoming(f, victim, reaction, now);
        // Under the other controllers the chase after a throw is their own dragon homing, the
        // window a launching kick opens there: one tap of forward flies to whoever was thrown.
        // Opened only when the window above was, so the two never disagree about it.
        if (!V2CombatServer.active() && f.homingVictimId == victim.getId() && f.homingUntilTick > now) {
            DragonHoming.open(player, victim);
        }
        V2Support.impactFx(player, victim, reaction);
        if (victim instanceof ServerPlayer defender) {
            V2Fighter held = V2FighterStore.get(defender);
            held.stunUntilTick = Math.max(held.stunUntilTick, now + reaction.stunTicks());
        }
        NeoForge.EVENT_BUS.post(new GrabEvent.Throw(player, victim, damage));
    }

    /**
     * The victim pressed grab back. Breaks the hold if it is inside the tech window.
     *
     * @return true when the hold was broken
     */
    static boolean tryTech(ServerPlayer victim, V2Fighter held, int now) {
        if (held.state != V2State.GRABBED || held.grabbedById < 0) return false;
        if (!GrabRules.techs(now - held.grabbedSinceTick, V2Config.get().grabTechWindowTicks, true)) return false;
        if (!(victim.level().getEntity(held.grabbedById) instanceof ServerPlayer grabber)) return false;
        V2Fighter f = V2FighterStore.peek(grabber);
        if (f == null || f.grabVictimId != victim.getId()) return false;
        release(grabber, f, victim);
        // Shove the two apart so the break reads and neither is standing inside the other.
        Vec3 away = victim.position().subtract(grabber.position());
        Vec3 flat = new Vec3(away.x, 0.0, away.z);
        if (flat.lengthSqr() < 1.0e-4) flat = new Vec3(0.0, 0.0, 1.0);
        flat = flat.normalize().scale(0.6);
        CombatKnockback.set(victim, flat.add(0.0, 0.2, 0.0));
        CombatKnockback.set(grabber, flat.scale(-1.0).add(0.0, 0.2, 0.0));
        CombatFx.cue(grabber.serverLevel(), victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0),
                CombatFxKind.COUNTER_FLASH, 1.0f);
        NeoForge.EVENT_BUS.post(new GrabEvent.Tech(grabber, victim));
        return true;
    }

    /** A hit on the grabber: cancels the grab only while it is still starting up. */
    static void onGrabberHurt(ServerPlayer player, V2Fighter f, float damage) {
        if (f.state == V2State.GRAB_STARTUP
                && GrabRules.interruptedByHit(GrabRules.Phase.STARTUP, damage)) {
            release(player, f, null);
        }
    }

    /**
     * Ends a grab from the grabber's side and frees the victim. Safe to call in any state.
     *
     * @param victim the held entity when the caller already has it, or null to look it up
     */
    static void release(ServerPlayer player, V2Fighter f, LivingEntity victim) {
        LivingEntity held = victim != null ? victim : V2Support.living(player, f.grabVictimId);
        f.grabVictimId = -1;
        f.grabConnectTick = -1;
        if (f.state == V2State.GRAB_STARTUP || f.state == V2State.GRAB_HOLD) f.state = V2State.NEUTRAL;
        if (held instanceof ServerPlayer defender) free(V2FighterStore.peek(defender), player.getId());
    }

    /** Frees a held fighter if {@code grabberId} is the one holding them. */
    static void free(V2Fighter held, int grabberId) {
        if (held == null || held.grabbedById != grabberId) return;
        held.grabbedById = -1;
        if (held.state == V2State.GRABBED) held.state = V2State.NEUTRAL;
    }

    /** Within arm's length and in front. A grab is hands, so it is measured body to body. */
    private static boolean inReach(ServerPlayer player, LivingEntity target, double range) {
        return target != null && GrabRules.inReach(player.distanceTo(target), range,
                V2Targeting.facing(player, target), V2Config.get().grabFacingDot);
    }

    static String refusal(ServerPlayer player, LivingEntity target) {
        String refusal = V2Support.refusal(player, target);
        if (refusal != null) return refusal;
        // Nobody is lifted out of a saddle, a boat or a ship's chair, nothing is picked up with
        // its rider, and nobody grabs from a seat of their own.
        if (player.isPassenger() || target.isPassenger() || target.isVehicle()) return "Not while riding";
        V2Config.Values cfg = V2Config.get();
        if (!(target instanceof ServerPlayer) && !cfg.grabNonPlayers) return "Only players can be grabbed";
        if (target.getBbHeight() > cfg.grabMaxVictimHeight) return "Too big to grab";
        if (!CombatKnockback.canKnockBack(target)) return "That cannot be grabbed";
        if (target instanceof ServerPlayer other) {
            V2Fighter held = V2FighterStore.peek(other);
            if (held != null && (held.state == V2State.GRABBED || held.state == V2State.GRAB_HOLD)) {
                return "Already in a grab";
            }
        }
        return null;
    }
}
