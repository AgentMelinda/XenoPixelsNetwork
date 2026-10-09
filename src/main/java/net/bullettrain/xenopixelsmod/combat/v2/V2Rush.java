package net.bullettrain.xenopixelsmod.combat.v2;

import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.api.event.RushEvent;
import net.bullettrain.xenopixelsmod.api.registry.Bt3RushDefinition;
import net.bullettrain.xenopixelsmod.api.registry.RushRegistry;
import net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.Bt3RushStatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The v2 cinematic rush: an automatic run of impacts offered after a full light string.
 *
 * <p>Which choreography plays still comes from {@link RushRegistry}, the same public catalogue
 * v1 uses, and the same {@link RushEvent}s are posted, so an addon that registered a rush or
 * listens for one cannot tell which controller is running. The client is driven by the existing
 * {@link Bt3RushStatePacket}.
 */
final class V2Rush {

    private static int nextSequenceId = 1;

    private V2Rush() {}

    static void start(ServerPlayer player, V2Fighter f, LivingEntity target, int now) {
        V2Config.Values cfg = V2Config.get();
        if (!cfg.rushEnabled || target == null) return;
        if (V2Support.refusal(player, target) != null) return;
        if (player.distanceTo(target) > cfg.rushRange) {
            V2Support.hint(player, "Rush: too far");
            return;
        }
        Bt3RushDefinition definition = resolve(player);
        if (NeoForge.EVENT_BUS.post(new RushEvent.Start(player, target, definition.id())).isCanceled()) {
            f.clearCombo();
            f.state = V2State.NEUTRAL;
            return;
        }
        f.clearCombo();
        V2Motion.stopTravel(player, f);
        f.rush = definition;
        f.rushTargetId = target.getId();
        f.rushStartTick = now;
        f.rushNextImpact = 0;
        f.rushSequenceId = nextSequenceId++;
        if (nextSequenceId <= 0) nextSequenceId = 1;
        f.state = V2State.RUSH;
        V2Support.faceBody(player, target.getX(), target.getZ());
        ModNetwork.sendToTrackingAndSelf(player, new Bt3RushStatePacket(
                Bt3RushStatePacket.Phase.START, player.getId(), target.getId(),
                f.rushSequenceId, definition.id()));
    }

    static void tick(ServerPlayer player, V2Fighter f, int now) {
        if (f.state != V2State.RUSH || f.rush == null) return;
        LivingEntity target = V2Support.living(player, f.rushTargetId);
        if (target == null || player.distanceTo(target) > V2Config.get().rushRange + 2.0) {
            interrupt(player, f);
            return;
        }
        int elapsed = now - f.rushStartTick;
        V2Support.faceBody(player, target.getX(), target.getZ());
        pull(player, target);
        while (f.rushNextImpact < f.rush.impactCount()
                && elapsed >= f.rush.impactTick(f.rushNextImpact)) {
            if (!impact(player, f, target, f.rushNextImpact, now)) {
                interrupt(player, f);
                return;
            }
            f.rushNextImpact++;
        }
        if (elapsed >= f.rush.durationTicks()) end(player, f);
    }

    /** Cancels a running rush and tells clients and addons. Safe to call when none is running. */
    static void interrupt(ServerPlayer player, V2Fighter f) {
        if (f.rush == null) return;
        Bt3RushDefinition definition = f.rush;
        LivingEntity target = V2Support.living(player, f.rushTargetId);
        end(player, f);
        NeoForge.EVENT_BUS.post(new RushEvent.Interrupt(player, target, definition.id()));
    }

    private static void end(ServerPlayer player, V2Fighter f) {
        Bt3RushDefinition definition = f.rush;
        int sequence = f.rushSequenceId;
        f.rush = null;
        f.rushTargetId = -1;
        if (f.state == V2State.RUSH) f.state = V2State.NEUTRAL;
        if (definition == null) return;
        ModNetwork.sendToTrackingAndSelf(player, new Bt3RushStatePacket(
                Bt3RushStatePacket.Phase.CANCEL, player.getId(), -1, sequence, definition.id()));
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = true;
    }

    private static boolean impact(ServerPlayer player, V2Fighter f, LivingEntity target, int index, int now) {
        V2Config.Values cfg = V2Config.get();
        if (player.distanceTo(target) > cfg.rushRange) return false;
        // A rush does not go through a block: guarding ends it. The grab is the answer to guard.
        if (target instanceof ServerPlayer defender && Bt3CombatEvents.isGuarding(defender)) return false;
        if (!V2Support.spend(player, cfg.rushKiCostPerImpact, 0f)) return false;
        boolean finisher = f.rush.isFinalImpact(index);
        float scale = finisher ? cfg.rushFinisherDamageScale : cfg.rushDamageScale;
        // A dodged or refused impact ends the rush: the victim got out of it.
        if (!V2Damage.strike(player, target, scale)) return false;
        NeoForge.EVENT_BUS.post(new RushEvent.Impact(
                player, target, f.rush.id(), index, V2Damage.nominal(player, scale), finisher));
        HitReaction reaction = finisher ? HitReaction.KNOCKBACK_LONG : HitReaction.HIT_LIGHT;
        if (finisher) {
            V2Support.react(player, target, reaction, 0f);
            V2Strikes.openHoming(f, target, reaction, now);
            if (target instanceof ServerPlayer defender) {
                V2Fighter victim = V2FighterStore.get(defender);
                victim.stunUntilTick = Math.max(victim.stunUntilTick, now + reaction.stunTicks());
            }
        } else {
            // Hold the victim in the rush: damp whatever they were doing instead of pushing.
            net.bullettrain.xenopixelsmod.combat.CombatKnockback.set(
                    target, target.getDeltaMovement().scale(0.35));
        }
        V2Support.impactFx(player, target, reaction);
        return true;
    }

    /** Keeps the attacker on the victim between impacts, with velocity rather than a teleport. */
    private static void pull(ServerPlayer player, LivingEntity target) {
        Vec3 delta = target.position().subtract(player.position());
        Vec3 flat = new Vec3(delta.x, 0.0, delta.z);
        double distance = flat.length();
        if (distance <= 2.2 || distance < 1.0e-4) return;
        double speed = Math.min(0.42, Math.max(0.12, (distance - 2.0) * 0.18));
        Vec3 drive = flat.scale(speed / distance);
        player.setDeltaMovement(drive.x, player.getDeltaMovement().y * 0.25, drive.z);
        player.hasImpulse = true;
    }

    private static Bt3RushDefinition resolve(ServerPlayer player) {
        StatsData data = V2Support.stats(player);
        if (data == null || data.getCharacter() == null) return RushRegistry.resolve(null, null);
        return RushRegistry.resolve(data.getCharacter().getRace(), data.getCharacter().getActiveForm());
    }
}
