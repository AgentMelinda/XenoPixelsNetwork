package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents;
import net.bullettrain.xenopixelsmod.anim.CombatStateAnim;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.MeleeAnimationS2C;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboGraphs;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboInput;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboMachine;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboNode;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.Bt3AnimIntentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Walks a fighter through the combo graph: accepts or holds an input, starts the beat, lands its
 * hit when the startup has passed, and closes the string when its window runs out.
 *
 * <p>The rules are {@link ComboMachine} and the routes are data; this class is the part that
 * spends resources and touches the world.
 *
 * <p>A beat's life, counted from its input: {@code startup} ticks to the hit, {@code cancel}
 * ticks of recovery in which the next input is held rather than played, then {@code window} ticks
 * in which a follow-up continues the string. Nothing starts while a beat is closed, so the
 * authored timings are the pace of a string whatever the click rate.
 */
final class V2Strikes {

    private V2Strikes() {}

    static void charged(ServerPlayer player, V2Fighter f, ComboInput input, LivingEntity target,
                        V2Direction direction, float charge, boolean teleport, int now) {
        boolean kick = input == ComboInput.HEAVY;
        ComboNode node = new ComboNode(kick ? "charged_kick" : "charged_punch",
                kick ? Bt3AnimationIntent.MID_KICK_RIGHT : Bt3AnimationIntent.HEAVY_FINISH,
                Map.of(), 5, 7, 0, kick ? 1.8f : 1.9f, 0, 0,
                kick ? HitReaction.KICK_ARC : HitReaction.HIT_HEAVY,
                null, ComboNode.Action.STRIKE, Map.of());
        begin(player, f, node, target, direction, charge, true, teleport, now);
    }

    static void input(ServerPlayer player, V2Fighter f, ComboInput input, LivingEntity target,
                      V2Direction direction, float charge, int now) {
        if (f.state.committed()) return;
        if (now < f.stunUntilTick) return;
        V2Config.Values cfg = V2Config.get();
        if (f.traveling() && f.travelKind != V2Fighter.TravelKind.Z_BURST) {
            // Swinging mid-flight only makes sense once the target is in reach; then the swing
            // ends the flight. Otherwise the press is ignored rather than cancelling the chase.
            if (V2Targeting.inReach(player, target, cfg.strikeRange, cfg.strikeFacingDot) == null) return;
            V2Motion.stopTravel(player, f);
        }
        if (f.node != null && f.hitPending) {
            // The current beat has not even hit yet (it may still be closing the gap).
            buffer(f, input, target, direction, charge, now);
            return;
        }
        int ticksInNode = f.node == null ? 0 : now - f.nodeStartTick;
        ComboMachine.Decision decision =
                ComboMachine.decide(ComboGraphs.active(), f.node, ticksInNode, f.hitLanded, input);
        switch (decision.outcome()) {
            case BUFFER -> buffer(f, input, target, direction, charge, now);
            case PLAY -> begin(player, f, decision.node(), target, direction, charge, false, false, now);
            case REJECT -> {
            }
        }
    }

    private static void buffer(V2Fighter f, ComboInput input, LivingEntity target,
                               V2Direction direction, float charge, int now) {
        f.buffered = input;
        f.bufferedTick = now;
        f.bufferedTargetId = target == null ? -1 : target.getId();
        f.bufferedDirection = direction;
        f.bufferedCharge = charge;
    }

    private static void begin(ServerPlayer player, V2Fighter f, ComboNode node, LivingEntity target,
                              V2Direction direction, float charge, boolean charged, boolean teleport, int now) {
        V2Config.Values cfg = V2Config.get();
        if (node.action() == ComboNode.Action.GRAB) {
            V2Grab.start(player, f, target, direction, now, node.intentFor(direction));
            return;
        }
        if (node.action() == ComboNode.Action.RUSH) {
            V2Rush.start(player, f, target, now);
            return;
        }
        if (target != null) {
            String refusal = V2Support.refusal(player, target);
            if (refusal != null) {
                V2Support.hint(player, refusal);
                return;
            }
        }
        if (!V2Support.spend(player, node.kiCost(), node.staminaCost())) {
            V2Support.hint(player, node.staminaCost() > 0f ? "Not enough stamina" : "Not enough ki");
            return;
        }

        f.clearBuffer();
        f.node = node;
        f.nodeStartTick = now;
        f.hitPending = true;
        f.hitLanded = false;
        f.targetId = target == null ? -1 : target.getId();
        f.nodeDirection = direction;
        f.nodeCharge = Math.max(0f, Math.min(1f, charge));
        f.chargedStrike = charged;
        f.chargedTeleport = teleport;
        f.state = V2State.ATTACK;

        // Body follows the crosshair, as ordinary DragonMineZ punches do, so the pose lines up
        // with where the fighter is aiming.
        player.yBodyRot = player.getYRot();
        player.yBodyRotO = player.getYRot();
        if (f.chargedStrike) {
            String fire = CombatStateAnim.resolve(player, node.intent().isKick()
                    ? TechniqueAnimSlot.CHARGE_KICK_FIRE : TechniqueAnimSlot.CHARGE_PUNCH_FIRE);
            NetworkHandler.sendToTrackingEntityAndSelf(new MeleeAnimationS2C(player.getId(), fire, false, 1f), player);
        } else {
            ModNetwork.sendToTrackingAndSelf(player,
                    new Bt3AnimIntentPacket(player.getId(), node.intentFor(direction)));
        }

        // Z-Burst: a swing at a locked target that is out of reach closes the gap rather than
        // whiffing.
        if (!f.chargedStrike && target != null && cfg.zBurstEnabled && !f.traveling()
                && V2Targeting.inReach(player, target, cfg.strikeRange, cfg.strikeFacingDot) == null
                && player.distanceTo(target) <= cfg.zBurstRange
                && V2Targeting.facing(player, target) >= cfg.strikeFacingDot
                && V2Support.spend(player, cfg.zBurstKiCost, 0f)) {
            V2Motion.startTravel(player, f, target, V2Fighter.TravelKind.Z_BURST,
                    cfg.zBurstSpeed, Math.max(1.2, cfg.strikeRange * 0.6), 0f);
        }
    }

    /** A Z-Burst has closed the gap: the beat it was carrying lands now, with a full window. */
    static void onBurstArrived(ServerPlayer player, V2Fighter f, int now) {
        if (f.node == null || !f.hitPending) return;
        f.nodeStartTick = now - f.node.startupTicks();
        f.state = V2State.ATTACK;
        resolveHit(player, f, now);
    }

    static void tick(ServerPlayer player, V2Fighter f, int now) {
        if (f.node == null) return;
        if (f.hitPending) {
            if (f.traveling()) return;
            if (now - f.nodeStartTick < f.node.startupTicks()) return;
            resolveHit(player, f, now);
        }
        int ticksInNode = now - f.nodeStartTick;
        if (ticksInNode < f.node.openTick()) return;

        if (f.buffered != null) {
            ComboInput input = f.buffered;
            int targetId = f.bufferedTargetId;
            V2Direction direction = f.bufferedDirection;
            float charge = f.bufferedCharge;
            boolean live = ComboMachine.bufferLive(f.bufferedTick, now, V2Config.get().inputBufferTicks);
            f.clearBuffer();
            if (live) {
                input(player, f, input, V2Support.living(player, targetId), direction, charge, now);
                return;
            }
        }
        // A whiff has nothing to wait for: the string is over the moment the fighter has recovered.
        if (!f.hitLanded || ComboMachine.expired(f.node, ticksInNode)) {
            f.clearCombo();
            if (f.state == V2State.ATTACK) f.state = V2State.NEUTRAL;
        }
    }

    private static void resolveHit(ServerPlayer player, V2Fighter f, int now) {
        ComboNode node = f.node;
        V2Config.Values cfg = V2Config.get();
        f.hitPending = false;
        f.hitLanded = false;
        // The beat lands on the target it was thrown at, the fighter's lock, or on nobody.
        LivingEntity target = V2Targeting.inReach(player, V2Support.living(player, f.targetId),
                cfg.strikeRange, cfg.strikeFacingDot);
        if (target == null) return;

        float charge = f.nodeCharge;
        float scale = node.damageScale() * (1f + (float) cfg.chargeDamageBonus * charge);
        if (!V2Damage.strike(player, target, scale)) return;
        f.hitLanded = true;

        HitReaction reaction = node.reactionFor(f.nodeDirection, charge > 0f);
        if (charge >= 0.95f && !reaction.isKick()) reaction = reaction.charged();
        boolean guarded = target instanceof ServerPlayer defender && Bt3CombatEvents.isGuarding(defender);
        // A blocked hit still lands and still continues the string, but it does not move the
        // defender: that is what the block is for. The grab is the answer to it.
        if (!guarded) {
            if (f.chargedTeleport && node.intent().isKick()) {
                Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
                V2ChargedArc.start(target, away, now);
            } else {
                V2Support.react(player, target, reaction, charge);
            }
            openHoming(f, target, reaction, now);
            if (target instanceof ServerPlayer defender) {
                V2Fighter victim = V2FighterStore.get(defender);
                victim.stunUntilTick = Math.max(victim.stunUntilTick, now + reaction.stunTicks());
            }
        }
        V2Support.impactFx(player, target, reaction);
        if (f.chargedStrike && !node.intent().isKick() && charge >= 1f) {
            // Visual blast only: the accepted punch already dealt damage; terrain stays intact.
            Vec3 impact = target.position().add(0, target.getBbHeight() * 0.6, 0);
            player.serverLevel().sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
            player.serverLevel().playSound(null, impact.x, impact.y, impact.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9f, 1.1f);
        }
    }

    static void openHoming(V2Fighter f, LivingEntity target, HitReaction reaction, int now) {
        int window = V2Config.get().homingWindowTicks;
        if (!reaction.opensChase() || window <= 0) return;
        f.homingVictimId = target.getId();
        f.homingUntilTick = now + window;
    }
}
