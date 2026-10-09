package net.bullettrain.xenopixelsmod.combat.v2;

import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents;
import net.bullettrain.xenopixelsmod.combat.KiDeflect;
import net.bullettrain.xenopixelsmod.combat.combo.ComboRouteMachine;
import net.bullettrain.xenopixelsmod.combat.controller.CombatControllerMode;
import net.bullettrain.xenopixelsmod.combat.technique.XenoRushStrikeView;
import net.bullettrain.xenopixelsmod.combat.technique.XenoRushTechniques;
import net.bullettrain.xenopixelsmod.combat.v2.combo.BranchFlavor;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboGraph;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboGraphs;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboInput;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboMachine;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.CombatV2StatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The server side of XenoCombat v2: where an input arrives, where every fighter is ticked, and
 * where a fighter's state is sent back.
 *
 * <p>One entry point and one tick. A client states what it pressed; this asks
 * {@link V2CombatGate} whether v2 is running for that player at all, checks the fighter is in a
 * condition to act, and hands the input to the move that owns it. Nothing the client sends is
 * trusted beyond "which input, at which entity id".
 *
 * <p><b>v2 is a lock-on system.</b> The entity id an input carries is the fighter's DragonMineZ
 * lock-on target. With no lock there is no v2: the input is refused here, before any move sees
 * it, and every move that does run is made at that target and nobody else. The one thing a
 * fighter can do without a lock is break out of a grab that has caught them. See {@link V2Lock}.
 *
 * <p><b>The grab is shared.</b> Under the {@code legacy} and {@code bt3_manual} controllers this
 * class still runs, for one move: the grab, with its throw and its break-out. It is the same grab,
 * under the same rules, made at the same locked target; those controllers simply reach it from
 * their own guard key. Every other input is refused there, and none of the rest of v2 (the
 * counter window, the rush strikes, the legacy counter being drained) applies. Because a grab can
 * be in progress under any controller, fighters are ticked under all of them.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class V2CombatServer {

    /** Longest a DragonMineZ strike is waited on before v2 gives up and frees the fighter. */
    private static final int STRIKE_MAX_TICKS = 120;

    /** The controller the fighters were last ticked under; null until the first tick. */
    private static CombatControllerMode tickedMode;

    private V2CombatServer() {}

    public static boolean active() {
        return XenoServerConfig.controllerMode() == CombatControllerMode.V2;
    }

    /**
     * Whether a fighter can grab at all under the controller that is running: always part of v2,
     * and shared with the legacy and manual controllers unless the server has turned that off.
     */
    public static boolean grabAvailable() {
        if (XenoServerConfig.controllerMode() == CombatControllerMode.V3) return false;
        V2Config.Values cfg = V2Config.get();
        return XenoServerConfig.bt3CombatEnabled && cfg.grabEnabled && (active() || cfg.grabOutsideV2);
    }

    // ---- input ----

    public static void handleInput(ServerPlayer player, V2Input input, int targetId,
                                   V2Direction direction, int chargePercent) {
        // V3 owns its grab as well as its attacks; old V2 packets must not bypass that owner.
        if (XenoServerConfig.controllerMode() == CombatControllerMode.V3) return;
        if (UltimateFinisher.owns(player)) return;
        if (input == null || input == V2Input.STEP) return;
        boolean able = player.isAlive() && !player.isSpectator()
                && !(player.getVehicle() instanceof XenoPilotSeatEntity);
        boolean v2 = active();
        V2Config.Values cfg = V2Config.get();
        // Being grabbed is not something a fighter chose, so getting out of it needs no leave.
        V2Fighter caught = V2FighterStore.peek(player);
        boolean breakingOut = input == V2Input.GRAB && caught != null && caught.state == V2State.GRABBED;
        V2CombatGate.Decision decision = v2
                ? V2CombatGate.decide(XenoServerConfig.controllerMode(), XenoServerConfig.bt3CombatEnabled,
                        breakingOut || XenoPermissions.hasPermission(player, XenoPermissions.COMBAT_V2_USE), able)
                : V2CombatGate.decideGrabOnly(input, XenoServerConfig.bt3CombatEnabled,
                        cfg.grabEnabled && cfg.grabOutsideV2,
                        breakingOut || XenoPermissions.hasPermission(player, XenoPermissions.COMBAT_GRAB_USE),
                        able);
        if (!decision.accepted()) return;
        int now = V2Support.tick(player);
        V2Fighter f = V2FighterStore.get(player);
        if (input != V2Input.DRAGON_DASH) f.clearDashFollow();

        // A held fighter has exactly one input: grab, to break free.
        if (f.state == V2State.GRABBED) {
            if (input == V2Input.GRAB) V2Grab.tryTech(player, f, now);
            sync(player, f, now);
            return;
        }
        if (input == V2Input.CHASE_STOP) {
            V2Moves.chaseStop(player, f);
            sync(player, f, now);
            return;
        }
        if (input == V2Input.CHARGE_CANCEL) {
            V2Charges.cancel(player, f);
            sync(player, f, now);
            return;
        }
        if (input == V2Input.GRAB && f.state == V2State.GRAB_HOLD) {
            V2Grab.redirect(f, direction);
            return;
        }
        // DragonMineZ's own hard stun (its stun effect, a knockdown, being held in a strike
        // technique) stops everything, as it stops DragonMineZ's own attacks.
        if (V2Support.dmzStunned(player)) return;
        // A rush or lift combo technique is driving this fighter until it finishes.
        if (ComboRouteMachine.isBusy(player.getUUID())) return;

        // Everything from here on is a move the fighter starts, and those need a lock.
        LivingEntity target = V2Support.living(player, targetId);
        String noLock = V2Lock.refusal(player, target);
        if (noLock != null) {
            // Naming nobody is what an unlocked client does; only a refused lock is worth a word.
            if (target != null) V2Support.hint(player, noLock);
            return;
        }
        if (input == V2Input.VANISH || input == V2Input.COUNTER) {
            V2Charges.cancel(player, f);
            // The way out of a string: allowed while reeling, and paced by its own cooldown.
            V2Moves.vanish(player, f, target, direction, now);
            sync(player, f, now);
            return;
        }

        // Server-side pacing: a forged client replays packets at network speed, and this refuses
        // anything faster than a hand can press.
        boolean chargeRelease = input == V2Input.LIGHT_HOLD || input == V2Input.HEAVY_HOLD;
        // This continuation is already paced by a single-use server window and Vanish cooldown.
        // A fast second N must not be swallowed by the initial launch's input timestamp.
        boolean dashContinuation = input == V2Input.DRAGON_DASH && f.dashWindowTarget(now) == targetId;
        if (!chargeRelease && !dashContinuation && now - f.lastInputTick < cfg.minInputIntervalTicks) return;
        f.lastInputTick = now;

        if (!v2) {
            // The gate let through one input, the grab. There is no v2 combo for it to be a
            // branch of here, so it is thrown from standing, straight at the lock.
            if (fists(player)) V2Grab.start(player, f, target, direction, now, V2Grab.REACH_POSE);
            sync(player, f, now);
            return;
        }

        switch (input) {
            case LIGHT_PRESS -> {
                V2Charges.cancel(player, f);
                if (fists(player) && !deflect(player)) {
                    V2Strikes.input(player, f, ComboInput.LIGHT, target, direction, 0f, now);
                }
            }
            case LIGHT_HOLD -> {
                if (fists(player)) {
                    V2Charges.release(player, f, target, false, direction, now);
                }
            }
            case HEAVY_PRESS -> {
                V2Charges.cancel(player, f);
                if (fists(player)) {
                    V2Strikes.input(player, f, ComboInput.HEAVY, target, direction, 0f, now);
                }
            }
            case HEAVY_HOLD -> {
                if (fists(player)) {
                    V2Charges.release(player, f, target, true, direction, now);
                }
            }
            case LIGHT_CHARGE_START, HEAVY_CHARGE_START -> {
                if (fists(player)) V2Charges.start(player, f, target, input == V2Input.HEAVY_CHARGE_START, now);
            }
            case GRAB -> {
                V2Charges.cancel(player, f);
                if (fists(player)) {
                    V2Strikes.input(player, f, ComboInput.GRAB, target, direction, 0f, now);
                }
            }
            case CHASE -> {
                V2Charges.cancel(player, f);
                V2Moves.chase(player, f, target, now);
            }
            case DRAGON_DASH -> {
                if (!fists(player)) break;
                V2Charges.cancel(player, f);
                // After a full light string the dash key is the rush, as it was in v1.
                if (rushOpen(f, now)) {
                    V2Strikes.input(player, f, ComboInput.RUSH, target, direction, 0f, now);
                } else {
                    V2Moves.dragonDash(player, f, target, chargePercent / 100f, now);
                }
            }
            case CINEMATIC_RUSH -> {
                if (fists(player)) {
                    V2Strikes.input(player, f, ComboInput.RUSH, target, direction, 0f, now);
                }
            }
            default -> {
            }
        }
        sync(player, f, now);
    }

    /**
     * Attacks need empty hands, the same rule the client applies before it sends one. Checked
     * again here because a packet is not proof of what the sender is holding.
     */
    private static boolean fists(ServerPlayer player) {
        return V2Support.emptyHands(player);
    }

    /** A released hold is a charged attack even when it was released the instant it qualified. */
    private static float held(int chargePercent) {
        return Math.max(0.05f, Math.min(1f, chargePercent / 100f));
    }

    /** Punching an incoming ki blast back is part of the light attack, as in v1. */
    private static boolean deflect(ServerPlayer player) {
        StatsData data = V2Support.stats(player);
        return data != null && KiDeflect.tryDeflect(player, data.getResources());
    }

    private static boolean rushOpen(V2Fighter f, int now) {
        return f.node != null && f.hitLanded && !f.hitPending
                && !ComboMachine.expired(f.node, now - f.nodeStartTick)
                && ComboInput.has(f.node.branchMask(), ComboInput.RUSH);
    }

    // ---- hooks other systems call ----

    /**
     * True while this player is committed to something a legacy action must not cut across: a
     * grab on either end, a rush, or a strike technique. Asked by the legacy packet handler under
     * every controller, because a grab can be in progress under every controller.
     */
    public static boolean blocksLegacyAction(ServerPlayer player) {
        if (UltimateFinisher.owns(player)) return true;
        V2Fighter f = V2FighterStore.peek(player);
        return f != null && f.state.committed();
    }

    /**
     * One of the four DragonMineZ rush strikes has just started while v2 is running.
     *
     * <p>DragonMineZ fires this at the <em>start</em> of the strike and then, for the length of
     * it, holds both fighters still and aims the attacker's camera at the target every tick.
     * Anything applied now is erased by that hold, and anything that moves the attacker now
     * fights it for the camera. So nothing happens here but taking note: what the strike does to
     * its target, and the chase after a breaker or finisher, are applied by {@link #tickStrike}
     * once DragonMineZ has let go.
     *
     * <p>Only for a strike thrown at the attacker's lock, like everything else in v2. One thrown
     * with nothing locked is not v2's: it is left to the legacy handling.
     *
     * @return true when v2 has taken the strike, so the legacy knockback and chase must not run
     */
    public static boolean onRushStrike(ServerPlayer player, LivingEntity target, String strikeId) {
        if (!active() || player == null || target == null) return false;
        if (!XenoRushStrikeView.castWithLockOn(player, target)) return false;
        HitReaction reaction;
        boolean chase;
        if (XenoRushTechniques.RUSH_BREAKER.equals(strikeId)) {
            reaction = HitReaction.LAUNCH_UP;
            chase = true;
        } else if (XenoRushTechniques.RUSH_FINISHER.equals(strikeId)) {
            reaction = HitReaction.KNOCKBACK_LONG;
            chase = true;
        } else {
            reaction = HitReaction.KNOCKBACK_SHORT;
            chase = false;
        }
        int now = V2Support.tick(player);
        V2Fighter f = V2FighterStore.get(player);
        // Whatever v2 had this fighter doing ends: DragonMineZ owns them until the strike is over.
        V2Charges.cancel(player, f);
        V2Motion.stopTravel(player, f);
        V2Rush.interrupt(player, f);
        V2Grab.release(player, f, null);
        f.clearCombo();
        f.strikeTargetId = target.getId();
        f.strikeStartTick = now;
        f.strikeReaction = reaction;
        f.strikeChase = chase;
        f.strikeTargetHealth = target.getHealth() + target.getAbsorptionAmount();
        f.state = V2State.STRIKE;
        sync(player, f, now);
        return true;
    }

    /** Waits out a DragonMineZ strike, then applies what v2 adds to it. */
    private static void tickStrike(ServerPlayer player, V2Fighter f, int now) {
        // The lock is set in the same call that reported the strike; never judge it that tick.
        if (now <= f.strikeStartTick) return;
        StatsData data = V2Support.stats(player);
        boolean locked = data != null && data.getStatus() != null && data.getStatus().isStrikeLocked();
        if (locked && now - f.strikeStartTick < STRIKE_MAX_TICKS) return;

        LivingEntity target = V2Support.living(player, f.strikeTargetId);
        HitReaction reaction = f.strikeReaction;
        boolean chase = f.strikeChase;
        float healthBefore = f.strikeTargetHealth;
        f.clearStrike();
        f.state = V2State.NEUTRAL;
        if (target == null || reaction == null || V2Support.refusal(player, target) != null) return;
        // A strike that took nothing off its target was refused (a protected area, an
        // invulnerable target): it does not get to launch them either.
        if (target.getHealth() + target.getAbsorptionAmount() >= healthBefore) return;

        V2Support.react(player, target, reaction, 0f);
        V2Strikes.openHoming(f, target, reaction, now);
        if (target instanceof ServerPlayer defender) {
            V2Fighter victim = V2FighterStore.get(defender);
            victim.stunUntilTick = Math.max(victim.stunUntilTick, now + reaction.stunTicks());
        }
        if (chase && V2Config.get().rushStrikeAutoChase) V2Moves.autoChase(player, f, target);
    }

    /**
     * Drops everything in progress for one player. Used by the controller-mode sweep.
     *
     * <p>The idle state is sent whether or not there was anything to drop: the sweep is also how
     * a client learns whether the controller it has just been moved to has a grab.
     */
    public static void clear(ServerPlayer player) {
        if (player == null) return;
        V2Fighter f = V2FighterStore.remove(player.getUUID());
        if (f != null) end(player, f);
        ModNetwork.sendToPlayer(player, idlePacket());
    }

    /**
     * Sends every online player their state again. For a reload of the combat config, which can
     * turn the grab on or off without the controller mode changing.
     */
    public static void resyncAll(MinecraftServer server) {
        if (server == null) return;
        int now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            V2Fighter f = V2FighterStore.peek(player);
            if (f == null) {
                ModNetwork.sendToPlayer(player, idlePacket());
            } else {
                f.lastSyncKey = Long.MIN_VALUE;
                sync(player, f, now);
            }
        }
    }

    private static void end(ServerPlayer player, V2Fighter f) {
        f.clearDashFollow();
        V2Charges.cancel(player, f);
        V2ChargedArc.stop(player.getUUID());
        V2Motion.stopTravel(player, f);
        V2Rush.interrupt(player, f);
        V2Grab.release(player, f, null);
        if (f.grabbedById >= 0 && player.level().getEntity(f.grabbedById) instanceof ServerPlayer grabber) {
            V2Fighter g = V2FighterStore.peek(grabber);
            if (g != null) V2Grab.release(grabber, g, player);
        }
        f.grabbedById = -1;
        f.clearCombo();
        f.clearStrike();
        f.state = V2State.NEUTRAL;
    }

    // ---- tick ----

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        CombatControllerMode mode = XenoServerConfig.controllerMode();
        if (mode != tickedMode) {
            // Nothing started under one controller carries into another, however the mode moved
            // (a command, a setting, a config reload). The controller service sweeps on the same
            // change; this makes sure not one tick is run under the new mode before it has.
            if (tickedMode != null) sweep(server);
            tickedMode = mode;
        }
        int now = server.getTickCount();
        V2ChargedArc.tick(now);
        if (V2FighterStore.isEmpty()) return;
        List<java.util.UUID> gone = null;
        // A copy: a grab that connects creates the victim's fighter, which would otherwise
        // modify the map mid-iteration. Bounded by the players who are fighting.
        for (V2Fighter f : new ArrayList<>(V2FighterStore.all())) {
            ServerPlayer player = server.getPlayerList().getPlayer(f.playerId);
            if (player == null) {
                if (gone == null) gone = new ArrayList<>();
                gone.add(f.playerId);
                continue;
            }
            if (f.idle(now)) {
                if (f.lastSyncKey != IDLE_KEY) sync(player, f, now);
                continue;
            }
            tickFighter(player, f, now);
            sync(player, f, now);
        }
        if (gone != null) gone.forEach(V2FighterStore::remove);
    }

    private static void tickFighter(ServerPlayer player, V2Fighter f, int now) {
        if (UltimateFinisher.owns(player)) return;
        if (f.dashFollowTargetId >= 0 && (now >= f.dashFollowUntilTick
                || V2Support.living(player, f.dashFollowTargetId) == null
                || Bt3CombatEvents.isGuarding(player) || V2Support.dmzStunned(player)
                || ComboRouteMachine.isBusy(player.getUUID()))) f.clearDashFollow();
        if (f.travelKind == V2Fighter.TravelKind.DRAGON_DASH && Bt3CombatEvents.isGuarding(player)) {
            V2Motion.stopTravel(player, f);
        }
        if (!player.isAlive() || player.isSpectator()) {
            end(player, f);
            return;
        }
        V2Charges.tick(player, f, now);
        if (f.state == V2State.GRABBED) {
            // Held: the grabber's tick moves this body. Only make sure the grabber still exists.
            if (!(player.level().getEntity(f.grabbedById) instanceof ServerPlayer grabber)
                    || V2FighterStore.peek(grabber) == null
                    || V2FighterStore.peek(grabber).grabVictimId != player.getId()) {
                V2Grab.free(f, f.grabbedById);
            }
            return;
        }
        if (f.state == V2State.STRIKE) {
            tickStrike(player, f, now);
            return;
        }
        if (busy(f) && (V2Support.dmzStunned(player) || ComboRouteMachine.isBusy(player.getUUID()))) {
            // Stunned, knocked down, held in someone else's strike, or being driven by a rush or
            // lift combo technique: whatever this fighter was doing is over, and must not keep
            // pushing a body something else is moving.
            V2Motion.stopTravel(player, f);
            V2Rush.interrupt(player, f);
            V2Grab.release(player, f, null);
            V2Charges.cancel(player, f);
            f.clearCombo();
            f.state = V2State.NEUTRAL;
            return;
        }
        // Read before the travel is advanced: arriving clears it.
        int travelTargetId = f.travelTargetId;
        V2Fighter.TravelKind arrived = V2Motion.tickTravel(player, f, now);
        if (arrived == V2Fighter.TravelKind.Z_BURST) {
            V2Strikes.onBurstArrived(player, f, now);
        } else if (arrived == V2Fighter.TravelKind.DRAGON_DASH) {
            V2Moves.onDashArrived(player, f, V2Support.living(player, travelTargetId), now);
        }
        V2Strikes.tick(player, f, now);
        V2Rush.tick(player, f, now);
        V2Grab.tick(player, f, now);
    }

    /** Whether the fighter is in the middle of something v2 is moving or timing. */
    private static boolean busy(V2Fighter f) {
        return f.state != V2State.NEUTRAL || f.node != null || f.traveling();
    }

    private static void sweep(MinecraftServer server) {
        V2ChargedArc.clear();
        for (V2Fighter f : new ArrayList<>(V2FighterStore.all())) {
            ServerPlayer player = server.getPlayerList().getPlayer(f.playerId);
            if (player != null) {
                end(player, f);
                ModNetwork.sendToPlayer(player, idlePacket());
            }
        }
        V2FighterStore.clear();
    }

    // ---- being hit ----

    /**
     * A fighter just out of a vanish is not there to be hit. Refused before any
     * damage is worked out, so the attacker's own move sees a miss: no reaction, no combo
     * continuing off a hit that never landed.
     *
     * <p>Only attacks: a fall, lava or the void are not something a vanish avoids.
     *
     * <p>The same event refuses the blows of whoever is being held in a grab, player or not.
     * Their hands are pinned, whatever their client or their AI wants: under the manual
     * controller left click is still DragonMineZ's own punch, and a held mob would otherwise go on
     * hitting the fighter holding it. Only blows struck directly: a ki blast already in the air
     * when the grab closed still arrives.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (V2FighterStore.isEmpty()) return;
        if (event.getSource().getDirectEntity() instanceof LivingEntity striker
                && striker != event.getEntity() && held(striker)) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == victim) return;
        V2Fighter f = V2FighterStore.peek(victim);
        if (f != null && f.invulnerable(V2Support.tick(victim))) event.setCanceled(true);
    }

    /** Whether {@code entity} is in some fighter's hands right now. */
    private static boolean held(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            V2Fighter f = V2FighterStore.peek(player);
            return f != null && f.state == V2State.GRABBED;
        }
        // Not a player, so it has no fighter of its own: ask the grabbers. Bounded by the
        // players who have fought, and only reached when something that is not a player hits.
        int id = entity.getId();
        for (V2Fighter f : V2FighterStore.all()) {
            if (f.state == V2State.GRAB_HOLD && f.grabVictimId == id) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        float damage = event.getNewDamage();
        if (damage <= 0.05f) return;
        boolean v2 = active();
        V2Fighter f = V2FighterStore.peek(victim);
        // Under the other controllers the only thing a hit can interrupt here is a grab.
        if (!v2 && f == null) return;
        int now = V2Support.tick(victim);
        // The legacy handler has just opened its own counter window for this hit. Under v2 the
        // counter is v2's, so that window is drained here or a vanish would fire both. Under the
        // other controllers that window is theirs and is left alone.
        if (v2) Bt3CombatEvents.consumeCounterWindow(victim);

        V2Config.Values cfg = V2Config.get();
        if (f != null) {
            f.clearDashFollow();
            if (f.travelKind == V2Fighter.TravelKind.DRAGON_DASH) V2Motion.stopTravel(victim, f);
            V2Charges.cancel(victim, f);
            V2Rush.interrupt(victim, f);
            V2Grab.onGrabberHurt(victim, f, damage);
        }
        if (v2 && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {
            int lockedUntil = f == null ? 0 : f.counterLockedUntilTick;
            if (CounterRules.opens(damage, cfg.counterEnabled, now, lockedUntil)
                    && victim.distanceTo(attacker) <= cfg.counterRange) {
                if (f == null) f = V2FighterStore.get(victim);
                f.counterOpenUntilTick = now + cfg.counterWindowTicks;
                f.counterAttackerId = attacker.getId();
            }
        }
        if (f != null) sync(victim, f, now);
    }

    // ---- lifecycle ----

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        V2Config.load();
        ComboGraphs.reload();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Ended, not just dropped. This fires before players are logged out and saved, so a
        // fighter cut off mid-chase would otherwise be saved weightless, with a borrowed aura and
        // flight still on, and come back that way. Every tick field is on the server tick count,
        // which restarts with the server, so nothing may survive a stop either.
        sweep(event.getServer());
        tickedMode = null;
    }

    /**
     * A client starts out not knowing whether there is a grab to offer; this tells it, so the
     * grab hint is right from the first fight rather than from the first input.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.sendToPlayer(player, idlePacket());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        drop(event.getEntity(), false);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        drop(event.getEntity(), true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        drop(event.getEntity(), true);
    }

    /**
     * @param tell the player is still connected, so their client is told the fighter is gone.
     *             Nothing will sync a dropped fighter again, and a client last told "held in a
     *             grab" would otherwise go on believing it after dying in one.
     */
    private static void drop(net.minecraft.world.entity.Entity entity, boolean tell) {
        if (!(entity instanceof ServerPlayer player)) return;
        V2ChargedArc.stop(player.getUUID());
        V2Fighter f = V2FighterStore.remove(player.getUUID());
        if (f == null) return;
        end(player, f);
        if (tell) ModNetwork.sendToPlayer(player, idlePacket());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        var old = event.getOriginal().getPersistentData();
        var next = event.getEntity().getPersistentData();
        next.putInt(V2Charges.KICK_COUNT, old.getInt(V2Charges.KICK_COUNT));
        next.putInt(V2Charges.PUNCH_COUNT, old.getInt(V2Charges.PUNCH_COUNT));
    }

    // ---- sync ----

    private static final long IDLE_KEY = 0L;

    private static CombatV2StatePacket idlePacket() {
        return new CombatV2StatePacket(V2State.NEUTRAL.ordinal(), 0, 0, 0, 0, 0, -1,
                grabAvailable(), (float) V2Config.get().grabRange, 0, -1, 0, -1);
    }

    /** Sends the fighter's state to its owner when anything a client can see has changed. */
    static void sync(ServerPlayer player, V2Fighter f, int now) {
        V2Config.Values cfg = V2Config.get();
        // The choice of what follows a beat is shown from the moment it lands, through its
        // recovery, to the end of its window: the fighter picks while the hit is still playing.
        boolean landed = f.node != null && !f.hitPending && f.hitLanded;
        int ticksInNode = landed ? now - f.nodeStartTick : 0;
        int choiceLeft = landed ? ComboMachine.choiceTicksLeft(f.node, ticksInNode) : 0;
        boolean choiceOpen = choiceLeft > 0 && !f.node.terminal();
        int mask = choiceOpen ? branches(f) : 0;
        int choiceTotal = choiceOpen ? ComboMachine.choiceTicksTotal(f.node) : 0;
        int counterLeft = Math.max(0, f.counterOpenUntilTick - now);
        int homingLeft = Math.max(0, f.homingUntilTick - now);
        int dashEnd = f.dashWindowEnd(now);
        int dashLeft = Math.max(0, dashEnd - now);
        int dashTarget = f.dashWindowTarget(now);
        boolean grabReady = grabAvailable() && now >= f.grabReadyTick
                && !f.state.committed();
        int grabLeft = 0;
        if (f.state == V2State.GRAB_HOLD) {
            grabLeft = Math.max(0, f.grabStartTick + cfg.grabStartupTicks + cfg.grabHoldTicks - now);
        } else if (f.state == V2State.GRABBED) {
            grabLeft = Math.max(0, f.grabbedSinceTick + cfg.grabTechWindowTicks - now);
        }

        // Anchors, not countdowns: a window that is merely ticking down is the same window, and
        // the client counts it down on its own between packets.
        long key = 1L + f.state.ordinal();
        key = key * 31 + mask;
        key = key * 31 + (choiceOpen ? f.nodeStartTick : 0);
        key = key * 31 + (counterLeft > 0 ? f.counterOpenUntilTick : 0);
        key = key * 31 + (homingLeft > 0 ? f.homingUntilTick : 0);
        key = key * 31 + (dashLeft > 0 ? dashEnd : 0);
        key = key * 31 + dashTarget;
        key = key * 31 + (grabReady ? 1 : 0);
        key = key * 31 + (grabLeft > 0 ? f.grabStartTick + f.grabbedSinceTick : 0);
        if (f.idle(now)) key = IDLE_KEY;
        if (key == f.lastSyncKey) return;
        f.lastSyncKey = key;
        ModNetwork.sendToPlayer(player, new CombatV2StatePacket(
                f.state.ordinal(), mask, choiceOpen ? choiceLeft : 0, choiceTotal, counterLeft, homingLeft,
                homingLeft > 0 ? f.homingVictimId : -1, grabReady, (float) cfg.grabRange, grabLeft,
                counterLeft > 0 ? f.counterAttackerId : -1, dashLeft, dashTarget));
    }

    /**
     * Which inputs lead somewhere from the fighter's current beat, with what each of light and
     * heavy leads to packed in beside it so the prompt can say "Kick" rather than "Light".
     */
    private static int branches(V2Fighter f) {
        ComboGraph graph = ComboGraphs.active();
        int mask = f.node.branchMask();
        mask = BranchFlavor.pack(mask, ComboInput.LIGHT, BranchFlavor.of(graph.next(f.node, ComboInput.LIGHT)));
        mask = BranchFlavor.pack(mask, ComboInput.HEAVY, BranchFlavor.of(graph.next(f.node, ComboInput.HEAVY)));
        return mask;
    }
}
