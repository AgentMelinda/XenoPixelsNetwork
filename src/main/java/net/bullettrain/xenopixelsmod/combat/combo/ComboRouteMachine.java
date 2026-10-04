package net.bullettrain.xenopixelsmod.combat.combo;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.bullettrain.xenopixelsmod.network.ChaseFlightSystem;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.Bt3AnimIntentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure transition table plus the server ticker that applies it.
 *
 * <p>{@link #decide} is Minecraft-free. Live sessions are server-only.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ComboRouteMachine {

    private static final Map<UUID, ComboRouteState> LIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    private ComboRouteMachine() {
    }

    /** {@code range <= 0} means unlimited. */
    public static boolean withinRange(double distance, double range) {
        if (!(range > 0.0)) return true;
        return distance <= range;
    }

    /** Returns whether the attacker has arrived at the selected target's hit range. */
    public static boolean arrivalReady(double distance, double hitRange) {
        if (!Double.isFinite(distance) || distance < 0.0) return false;
        return withinRange(distance, hitRange);
    }

    /**
     * Pure selector: lift combo uses the lift trio, everything else uses rush.
     * Does not read {@link XenoServerConfig}.
     */
    public static ComboKnockTravel knockTravel(ComboRoute route, ComboKnockTravel rush,
                                               ComboKnockTravel lift) {
        if (route != null && CombatSkills.LIFTCOMBO.equals(route.skillId())) {
            return lift != null ? lift : new ComboKnockTravel(0, 0, 0);
        }
        return rush != null ? rush : new ComboKnockTravel(0, 0, 0);
    }

    public static boolean isBusy(UUID playerId) {
        return playerId != null && LIVE.containsKey(playerId);
    }

    public static boolean onCooldown(ServerPlayer player) {
        if (player == null) return false;
        Integer until = COOLDOWN_UNTIL.get(player.getUUID());
        return until != null && player.tickCount < until;
    }

    public static boolean start(ServerPlayer player, LivingEntity target, ComboRoute route) {
        if (player == null || target == null || route == null || isBusy(player.getUUID())) {
            return false;
        }
        int now = player.tickCount;
        ComboRouteState state = new ComboRouteState(route, target.getId(), now,
                now + Math.max(1, XenoServerConfig.comboRouteCooldownTicks));
        LIVE.put(player.getUUID(), state);
        COOLDOWN_UNTIL.put(player.getUUID(), state.cooldownUntilTick);
        if (arrivalReady(player.distanceTo(target), XenoServerConfig.comboRouteHitRange)) {
            ChaseFlightSystem.stopChase(player);
        } else {
            ChaseFlightSystem.start(player, target);
        }
        return true;
    }

    public static void cancel(ServerPlayer player) {
        if (player == null) return;
        if (LIVE.remove(player.getUUID()) != null) {
            ChaseFlightSystem.stopChase(player);
        }
    }

    public static ComboRouteDecision decide(ComboRoute route, ComboRoutePhase phase, ComboRouteInput input) {
        if (route == null || phase == null || input == null) {
            return ComboRouteDecision.idle();
        }
        if (phase != ComboRoutePhase.IDLE && phase != ComboRoutePhase.RECOVERY && !input.targetAlive()) {
            return ComboRouteDecision.idle();
        }
        return switch (phase) {
            case IDLE -> ComboRouteDecision.to(ComboRoutePhase.APPROACH);
            case APPROACH -> input.inRange()
                    ? ComboRouteDecision.to(ComboRoutePhase.STRING1)
                    : ComboRouteDecision.to(ComboRoutePhase.APPROACH);
            case STRING1 -> stringHit(route.firstString(), input, ComboRoutePhase.STRING1,
                    ComboRoutePhase.KNOCKBACK);
            case KNOCKBACK -> knockback(route, input);
            case REAPPROACH -> input.inRange()
                    ? ComboRouteDecision.to(ComboRoutePhase.STRING2)
                    : ComboRouteDecision.to(ComboRoutePhase.REAPPROACH);
            case STRING2 -> stringHit(route.secondString(), input, ComboRoutePhase.STRING2,
                    ComboRoutePhase.RECOVERY);
            case RECOVERY -> ComboRouteDecision.idle();
        };
    }

    private static ComboRouteDecision stringHit(Bt3AnimationIntent[] string, ComboRouteInput input,
                                                ComboRoutePhase stay, ComboRoutePhase after) {
        int authored = string == null ? 0 : string.length;
        int count = Math.max(1, Math.min(input.hitCount(), Math.max(1, authored)));
        int index = Math.max(0, input.hitsLanded());
        if (index >= count - 1) {
            return ComboRouteDecision.hit(after, Math.min(index, count - 1));
        }
        return ComboRouteDecision.hit(stay, index);
    }

    private static ComboRouteDecision knockback(ComboRoute route, ComboRouteInput input) {
        boolean canReapproach = route.autoReapproach()
                && input.autoReapproach()
                && input.reapproachesDone() < Math.max(0, input.maxReapproach());
        if (canReapproach) {
            return ComboRouteDecision.knockback(ComboRoutePhase.REAPPROACH, false);
        }
        return ComboRouteDecision.knockback(ComboRoutePhase.RECOVERY, route.chaseAfterKnockback());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (LIVE.isEmpty()) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Iterator<Map.Entry<UUID, ComboRouteState>> it = LIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ComboRouteState> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.hasDisconnected()) {
                it.remove();
                continue;
            }
            if (!tickOne(player, entry.getValue())) {
                ChaseFlightSystem.stopChase(player);
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player);
            COOLDOWN_UNTIL.remove(player.getUUID());
        }
    }

    private static boolean tickOne(ServerPlayer player, ComboRouteState state) {
        state.ticksInPhase++;
        int timeout = Math.max(10, XenoServerConfig.comboRouteApproachTimeoutTicks);
        if ((state.phase == ComboRoutePhase.APPROACH || state.phase == ComboRoutePhase.REAPPROACH)
                && state.ticksInPhase > timeout) {
            return false;
        }
        if (player.tickCount < state.nextActionTick) {
            return true;
        }
        Entity raw = player.serverLevel().getEntity(state.targetEntityId);
        LivingEntity target = raw instanceof LivingEntity living && living.isAlive() ? living : null;
        boolean inRange = target != null
                && arrivalReady(player.distanceTo(target), XenoServerConfig.comboRouteHitRange);
        int hitCount = Math.max(3, Math.min(4, XenoServerConfig.comboRouteHitCount));
        ComboRouteInput input = new ComboRouteInput(
                target != null,
                inRange,
                hitCount,
                Math.max(0, Math.min(1, XenoServerConfig.comboRouteMaxReapproach)),
                XenoServerConfig.comboRouteAutoReapproach,
                state.hitsLanded,
                state.reapproachesDone);
        ComboRouteDecision decision = decide(state.route, state.phase, input);
        if (decision.finish() || decision.next() == ComboRoutePhase.IDLE) {
            return false;
        }
        if (decision.spendHit() && target != null) {
            landHit(player, target, state.route, decision.hitIndex(),
                    state.phase == ComboRoutePhase.STRING2);
            state.hitsLanded++;
            state.nextActionTick = player.tickCount + Math.max(2, XenoServerConfig.comboRouteHitTicks);
        }
        if (decision.applyKnockback() && target != null) {
            int mastery = CombatSkills.level(player, state.route.skillId());
            double scale = 1.0 + 0.15 * Math.max(0, mastery - 1);
            ComboKnockTravel travel = knockTravel(state.route,
                    new ComboKnockTravel(
                            XenoServerConfig.rushComboKnockTravel,
                            XenoServerConfig.rushComboKnockUp,
                            XenoServerConfig.rushComboKnockDown),
                    new ComboKnockTravel(
                            XenoServerConfig.liftComboKnockTravel,
                            XenoServerConfig.liftComboKnockUp,
                            XenoServerConfig.liftComboKnockDown));
            Vec3 away = target.position().subtract(player.position());
            if (!travel.isZero()) {
                Vec3 look = player.getLookAngle();
                double[] impulse = RushKnockbackPath.impulse(
                        look.x, look.y, look.z,
                        away.x, away.y, away.z,
                        travel.distance() * scale,
                        travel.up() * scale,
                        travel.down() * scale,
                        XenoServerConfig.rushKnockbackVerticalPitch,
                        player.getXRot());
                CombatKnockback.set(target, new Vec3(impulse[0], impulse[1], impulse[2]));
            }
            CombatFx.impact(player.serverLevel(), player, target, away, CombatFx.Weight.HEAVY);
            if (decision.startChase()) {
                ChaseFlightSystem.startAutomatic(player, target);
            }
            state.nextActionTick = player.tickCount + 4;
        }
        if (decision.next() == ComboRoutePhase.REAPPROACH && state.phase != ComboRoutePhase.REAPPROACH) {
            state.reapproachesDone++;
            state.hitsLanded = 0;
            state.ticksInPhase = 0;
            if (target != null) {
                ChaseFlightSystem.start(player, target);
            }
        }
        if (decision.next() == ComboRoutePhase.STRING1 || decision.next() == ComboRoutePhase.STRING2) {
            if (state.phase != decision.next()) {
                state.hitsLanded = 0;
                state.ticksInPhase = 0;
                ChaseFlightSystem.stopChase(player);
            }
        }
        state.phase = decision.next();
        return state.phase != ComboRoutePhase.IDLE && state.phase != ComboRoutePhase.RECOVERY;
    }

    private static void landHit(ServerPlayer player, LivingEntity target, ComboRoute route,
                                int hitIndex, boolean secondString) {
        Bt3AnimationIntent[] string = secondString ? route.secondString() : route.firstString();
        Bt3AnimationIntent intent = string != null && hitIndex >= 0 && hitIndex < string.length
                ? string[hitIndex] : Bt3AnimationIntent.JAB_LEFT;
        ModNetwork.sendToTrackingAndSelf(player, new Bt3AnimIntentPacket(player.getId(), intent));
        float base = 4.0f;
        var stats = com.dragonminez.common.stats.StatsProvider
                .get(com.dragonminez.common.stats.StatsCapability.INSTANCE, player).orElse(null);
        if (stats != null) {
            base = (float) Math.max(base, stats.getMeleeDamage() * 0.35);
            if (stats.getTechniques() != null) {
                stats.getTechniques().addExperienceToTechnique(route.strikeId(), 4);
                String[] beats = route.beatStrikeIds();
                if (beats != null && hitIndex >= 0 && hitIndex < beats.length) {
                    stats.getTechniques().addExperienceToTechnique(beats[hitIndex], 2);
                }
            }
        }
        float scale = XenoServerConfig.comboDamageScale;
        target.hurt(player.damageSources().playerAttack(player), base * scale);
        Vec3 away = target.position().subtract(player.position());
        Vec3 flat = new Vec3(away.x, 0.0, away.z);
        if (flat.lengthSqr() > 1.0e-4) {
            CombatKnockback.add(target, flat.normalize().scale(0.12));
        }
        CombatFx.impact(player.serverLevel(), player, target, flat, CombatFx.Weight.LIGHT);
    }
}
