package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents;
import net.bullettrain.xenopixelsmod.combat.Bt3Landing;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.combat.VanishShadeFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.combat.v2.motion.MotionRules;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.Bt3CombatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The single-shot v2 moves that are not part of a combo string: chase and dragon homing, Dragon
 * Dash, and the vanish with the super counter that grows out of it.
 */
final class V2Moves {

    private V2Moves() {}

    // ---- chase / dragon homing ----

    static void chase(ServerPlayer player, V2Fighter f, LivingEntity requested, int now) {
        V2Config.Values cfg = V2Config.get();
        if (!cfg.chaseEnabled || f.state.committed()) return;
        if (now < f.stunUntilTick) return;

        // Dragon homing: for a short while after a launching hit, chase goes to whoever was
        // launched, whatever the client is aiming at, and it is free.
        LivingEntity homing = now <= f.homingUntilTick ? V2Support.living(player, f.homingVictimId) : null;
        LivingEntity target = homing != null ? homing : requested;
        if (target == null) return;
        String refusal = V2Support.refusal(player, target);
        if (refusal != null) {
            V2Support.hint(player, refusal);
            return;
        }
        if (f.travelKind == V2Fighter.TravelKind.CHASE && f.travelTargetId == target.getId()) return;

        double distance = player.distanceTo(target);
        if (distance <= cfg.chaseArriveDistance) return;
        if (homing == null) {
            if (distance > cfg.chaseMaxRange) {
                V2Support.hint(player, "Chase: too far");
                return;
            }
            if (!V2Support.spend(player, cfg.chaseKiCost, 0f)) {
                V2Support.hint(player, "Not enough ki");
                return;
            }
        }
        launch(player, f, target);
    }

    /**
     * Chases without being asked and without a cost: what a rush breaker or finisher does once it
     * has thrown its target.
     */
    static void autoChase(ServerPlayer player, V2Fighter f, LivingEntity target) {
        if (!V2Config.get().chaseEnabled || f.state.committed()) return;
        if (target == null || V2Support.refusal(player, target) != null) return;
        launch(player, f, target);
    }

    private static void launch(ServerPlayer player, V2Fighter f, LivingEntity target) {
        V2Config.Values cfg = V2Config.get();
        f.homingVictimId = -1;
        f.homingUntilTick = 0;
        f.clearCombo();
        if (f.state == V2State.ATTACK) f.state = V2State.NEUTRAL;
        Bt3CombatPacket.playItSound(player, player.getX(), player.getY(), player.getZ(), true);
        V2Motion.startTravel(player, f, target, V2Fighter.TravelKind.CHASE,
                cfg.chaseSpeed, cfg.chaseArriveDistance, 0f);
    }

    static void chaseStop(ServerPlayer player, V2Fighter f) {
        if (f.travelKind == V2Fighter.TravelKind.CHASE || f.travelKind == V2Fighter.TravelKind.DRAGON_DASH) {
            V2Motion.stopTravel(player, f);
        }
        f.clearDashFollow();
    }

    // ---- dragon dash ----

    static void dragonDash(ServerPlayer player, V2Fighter f, LivingEntity target, float charge, int now) {
        V2Config.Values cfg = V2Config.get();
        if (!cfg.dragonDashEnabled || f.state.committed() || Bt3CombatEvents.isGuarding(player)) return;
        if (now < f.stunUntilTick) return;
        if (target != null && f.dashWindowTarget(now) == target.getId()) {
            dashVanish(player, f, target, now);
            return;
        }
        if (now < f.dashReadyTick || f.traveling()) return;
        if (target == null) return;
        String refusal = V2Support.refusal(player, target);
        if (refusal != null) {
            V2Support.hint(player, refusal);
            return;
        }
        if (player.distanceTo(target) > cfg.dragonDashRange) {
            V2Support.hint(player, "Dragon Dash: too far");
            return;
        }
        float c = Math.max(0f, Math.min(1f, charge));
        float scale = 0.5f + 0.5f * c;
        if (!V2Support.spend(player, cfg.dragonDashKiCost * scale, cfg.dragonDashStaminaCost * scale)) {
            V2Support.hint(player, "Not enough ki or stamina");
            return;
        }
        f.clearCombo();
        f.clearDashFollow();
        if (f.state == V2State.ATTACK) f.state = V2State.NEUTRAL;
        f.dashReadyTick = now + cfg.dragonDashCooldownTicks;
        DmzAnimHelper.playChargeRelease(player, DmzAnimHelper.ChargeStyle.DRAGON, c >= 0.95f);
        V2Motion.startTravel(player, f, target, V2Fighter.TravelKind.DRAGON_DASH,
                cfg.dragonDashSpeed, Math.max(1.2, cfg.strikeRange * 0.6), c);
    }

    /** The dash has reached its target: land the hit and launch them, with a homing window. */
    static void onDashArrived(ServerPlayer player, V2Fighter f, LivingEntity target, int now) {
        V2Config.Values cfg = V2Config.get();
        if (target == null || V2Support.refusal(player, target) != null) return;
        // The dash carried the fighter to the target; only reach is asked, not facing.
        if (!V2Targeting.canStrike(player, target, cfg.strikeRange + 0.5, -1.0)) return;
        float scale = cfg.dragonDashDamageScale * (0.7f + 0.5f * f.travelCharge);
        if (!V2Damage.strike(player, target, scale)) return;
        V2Support.react(player, target, HitReaction.LAUNCH_FORWARD, f.travelCharge);
        V2Strikes.openHoming(f, target, HitReaction.LAUNCH_FORWARD, now);
        V2Support.impactFx(player, target, HitReaction.LAUNCH_FORWARD);
        f.dashFollowTargetId = target.getId();
        f.dashFollowUntilTick = now + cfg.dragonDashFollowupTicks;
    }

    private static void dashVanish(ServerPlayer player, V2Fighter f, LivingEntity target, int now) {
        V2Config.Values cfg = V2Config.get();
        if (now < f.vanishReadyTick || V2Support.refusal(player, target) != null
                || (!XenoServerConfig.lockOnThroughBlocks && !V2Targeting.sight(player, target))) return;
        Vec3 dest = V2DragonDashLanding.find(player, target, cfg.dragonDashRange);
        if (dest == null) {
            V2Support.hint(player, "No room for Dragon Dash vanish");
            return;
        }
        if (!V2Support.spend(player, cfg.vanishKiCost, cfg.vanishStaminaCost)) {
            V2Support.hint(player, "Not enough ki or stamina");
            return;
        }
        Vec3 from = player.position();
        float yaw = (float) (Math.toDegrees(Math.atan2(target.getZ() - dest.z, target.getX() - dest.x)) - 90);
        f.clearDashFollow();
        f.homingVictimId = -1;
        f.homingUntilTick = 0;
        f.vanishReadyTick = now + cfg.vanishCooldownTicks;
        blink(player, f, from, dest, yaw, now, cfg.vanishIFrameTicks);
        CombatFx.cue(player.serverLevel(), from, CombatFxKind.VANISH_CLAP, 1f);
    }

    // ---- vanish / super counter ----

    /**
     * The vanish key. In order: a super counter if the locked target just landed a hit and is in
     * range; a vanish behind the locked target throughout its validated lock range.
     *
     * <p>Unlike everything else a fighter can do, this is allowed while reeling from a hit: it is
     * the way out of a string. It costs ki, has its own short cooldown, and attacks pass through
     * the fighter for a few ticks after it so the blow that was already on its way does not land
     * on them where they reappear.
     */
    static void vanish(ServerPlayer player, V2Fighter f, LivingEntity requested, V2Direction direction, int now) {
        V2Config.Values cfg = V2Config.get();
        if (f.state.committed()) return;
        // A counter answers the locked target and nobody else: hit by a third party, the
        // fighter vanishes instead, which is the better answer to being hit from outside the duel.
        if (cfg.counterEnabled && CounterRules.live(now, f.counterOpenUntilTick)
                && requested != null && requested.getId() == f.counterAttackerId
                && counter(player, f, now)) {
            return;
        }
        if (!cfg.vanishEnabled || now < f.vanishReadyTick) return;

        // Going behind someone on the far side of a wall is the lock-on "through blocks" rule: a
        // server that has turned that off does not want fighters arriving through walls either.
        LivingEntity target = requested != null && V2Support.refusal(player, requested) == null
                && (XenoServerConfig.lockOnThroughBlocks || V2Targeting.sight(player, requested))
                ? requested : null;
        Vec3 from = player.position();
        Vec3 dest;
        float yaw;
        if (target != null) {
            int side = direction == V2Direction.LEFT ? -1 : direction == V2Direction.RIGHT ? 1 : 0;
            dest = V2VanishLanding.find(player, target, side, cfg, V2Lock.range(V2Support.stats(player)));
            // Reappear looking at the target's back.
            yaw = dest == null ? player.getYRot()
                    : (float) (Math.toDegrees(Math.atan2(target.getZ() - dest.z, target.getX() - dest.x)) - 90.0);
        } else {
            dest = blinkDestination(player, direction, cfg.vanishBlinkDistance);
            yaw = player.getYRot();
        }
        if (dest == null || dest.distanceToSqr(from) < 0.25) {
            V2Support.hint(player, "No room to vanish");
            return;
        }
        if (!V2Support.spend(player, cfg.vanishKiCost, cfg.vanishStaminaCost)) {
            V2Support.hint(player, "Not enough ki");
            return;
        }
        f.vanishReadyTick = now + cfg.vanishCooldownTicks;
        blink(player, f, from, dest, yaw, now, cfg.vanishIFrameTicks);
        CombatFx.cue(player.serverLevel(), from, CombatFxKind.VANISH_CLAP, 1.0f);
    }

    /** @return true when the counter was thrown; false leaves the vanish to do something else */
    private static boolean counter(ServerPlayer player, V2Fighter f, int now) {
        V2Config.Values cfg = V2Config.get();
        LivingEntity attacker = V2Support.living(player, f.counterAttackerId);
        if (attacker == null || V2Support.refusal(player, attacker) != null
                || player.distanceTo(attacker) > cfg.counterRange) {
            // Hit from across the map, or by something that is gone: nothing to counter.
            f.counterOpenUntilTick = 0;
            f.counterAttackerId = -1;
            return false;
        }
        Vec3 from = player.position();
        Vec3 dest = Bt3Landing.vanishLanding(player, attacker, 0);
        if (dest.distanceToSqr(from) < 1.0e-6 && player.distanceTo(attacker) > cfg.strikeRange) {
            // Nowhere open behind them and too far to hit from here.
            return false;
        }
        if (!V2Support.spend(player, cfg.counterKiCost, 0f)) {
            V2Support.hint(player, "Not enough ki");
            return false;
        }
        f.counterOpenUntilTick = 0;
        f.counterAttackerId = -1;
        f.counterLockedUntilTick = now + cfg.counterLockoutTicks;
        float yaw = (float) (Math.toDegrees(Math.atan2(attacker.getZ() - dest.z, attacker.getX() - dest.x)) - 90.0);
        blink(player, f, from, dest, yaw, now, cfg.vanishIFrameTicks);
        CombatFx.cue(player.serverLevel(), from, CombatFxKind.COUNTER_FLASH, 1.0f);

        if (V2Damage.strike(player, attacker, cfg.counterDamageScale)) {
            V2Support.react(player, attacker, HitReaction.KNOCKBACK_SHORT, 0f);
            V2Support.impactFx(player, attacker, HitReaction.KNOCKBACK_SHORT);
            if (attacker instanceof ServerPlayer countered) {
                V2Fighter victim = V2FighterStore.get(countered);
                victim.stunUntilTick = Math.max(victim.stunUntilTick,
                        now + HitReaction.KNOCKBACK_SHORT.stunTicks());
            }
        }
        return true;
    }

    /**
     * The one place v2 teleports: a vanish is not a travel. Ends whatever the fighter was doing,
     * moves them, and makes them untouchable for {@code iframeTicks}.
     */
    private static void blink(ServerPlayer player, V2Fighter f, Vec3 from, Vec3 dest, float yaw,
                              int now, int iframeTicks) {
        f.clearCombo();
        V2Motion.stopTravel(player, f);
        if (f.state == V2State.ATTACK || f.state == V2State.TRAVEL) {
            f.state = V2State.NEUTRAL;
        }
        // Vanishing out of a string ends the reel it was causing.
        f.stunUntilTick = Math.min(f.stunUntilTick, now);
        f.iframesUntilTick = Math.max(f.iframesUntilTick, now + iframeTicks);
        Bt3CombatEvents.setGuarding(player, false);
        DmzAnimHelper.broadcastBlockStop(player);

        Bt3CombatPacket.playItSound(player, from.x, from.y, from.z, true);
        player.connection.teleport(dest.x, dest.y, dest.z, yaw, player.getXRot());
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        resetTeleportMotion(player);
        Bt3CombatEvents.grantFallGrace(player, 60);
        Bt3CombatPacket.playItSound(player, dest.x, dest.y, dest.z, false);
        // Stamped after the teleport so the shade is left behind rather than on top of the fighter.
        VanishShadeFx.spawn(player, from);
    }

    /** Stops the fighter's previous movement after a teleport. */
    static void resetTeleportMotion(Entity fighter) {
        fighter.setDeltaMovement(Vec3.ZERO);
        // The tracker sends this reset to the fighter's client as well as nearby observers.
        fighter.hurtMarked = true;
        fighter.hasImpulse = true;
        fighter.fallDistance = 0f;
    }

    /**
     * Where a vanish with nobody to go behind puts the fighter: as far as there is room for, up
     * to {@code distance}, the way they are holding (backwards when they hold nothing).
     *
     * <p>Never through a wall. With no target to arrive beside, a blink that ignored blocks would
     * be a free way into any building, so the spot must be open and the straight line to it clear.
     */
    private static Vec3 blinkDestination(ServerPlayer player, V2Direction direction, double distance) {
        int forward = direction == V2Direction.FORWARD ? 1 : 0;
        int strafe = direction == V2Direction.RIGHT ? 1 : direction == V2Direction.LEFT ? -1 : 0;
        if (forward == 0 && strafe == 0) forward = -1;
        for (double d = distance; d >= 1.0; d -= 0.5) {
            double[] offset = MotionRules.stepOffset(player.getYRot(), forward, strafe, d);
            Vec3 move = new Vec3(offset[0], 0.0, offset[1]);
            Vec3 candidate = player.position().add(move);
            if (Bt3Landing.isSpotOpen(player, candidate) && clearLine(player, move)) return candidate;
        }
        return null;
    }

    /** No solid block between where the fighter stands and the same point moved by {@code move}. */
    private static boolean clearLine(ServerPlayer player, Vec3 move) {
        Vec3 feet = player.position().add(0.0, 0.2, 0.0);
        Vec3 head = player.getEyePosition();
        return unobstructed(player, feet, feet.add(move)) && unobstructed(player, head, head.add(move));
    }

    private static boolean unobstructed(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }
}
