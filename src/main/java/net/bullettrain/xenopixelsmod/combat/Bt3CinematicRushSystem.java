package net.bullettrain.xenopixelsmod.combat;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.Bt3RushStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.bullettrain.xenopixelsmod.api.event.RushEvent;
import net.bullettrain.xenopixelsmod.api.registry.Bt3RushDefinition;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned X-X-X then A follow-up window and automatic four-impact rush. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class Bt3CinematicRushSystem {

    private static final double MAX_START_RANGE = 5.5;
    private static final double MAX_ACTIVE_RANGE = 7.5;
    private static final Map<UUID, Bt3RushFollowup.Gate> FOLLOWUPS = new HashMap<>();
    private static final Map<UUID, ActiveRush> ACTIVE = new HashMap<>();
    private static int nextSequenceId = 1;

    private Bt3CinematicRushSystem() {
    }

    public static void clearFollowup(ServerPlayer player) {
        if (player != null) FOLLOWUPS.remove(player.getUUID());
    }

    public static void armAfterHit(ServerPlayer player, LivingEntity target, int comboStep) {
        if (player == null || target == null || !XenoServerConfig.bt3CinematicRushEnabled) return;
        Bt3RushFollowup.Gate gate = Bt3RushFollowup.arm(comboStep, target.getUUID(), tick(player));
        if (gate != null) FOLLOWUPS.put(player.getUUID(), gate);
    }

    public static boolean isActive(ServerPlayer player) {
        return player != null && ACTIVE.containsKey(player.getUUID());
    }

    public static boolean tryStart(ServerPlayer player, LivingEntity target) {
        if (player == null || target == null || !target.isAlive()) return false;
        if (!XenoServerConfig.bt3CombatEnabled || !XenoServerConfig.bt3ComboEnabled
                || !XenoServerConfig.bt3CinematicRushEnabled || isActive(player)) return false;
        int now = tick(player);
        Bt3RushFollowup.Gate gate = FOLLOWUPS.remove(player.getUUID());
        if (!Bt3RushFollowup.accepts(gate, target.getUUID(), now)) return false;
        if (player.distanceTo(target) > MAX_START_RANGE) return false;

        Bt3RushDefinition definition = resolve(player);
        // The follow-up gate was already consumed above, so cancelling here costs the player their
        // armed window as though the rush had been attempted - which it was.
        RushEvent.Start start = new RushEvent.Start(player, target, definition.id());
        if (NeoForge.EVENT_BUS.post(start).isCanceled()) return false;
        int sequenceId = nextSequenceId++;
        if (nextSequenceId <= 0) nextSequenceId = 1;
        ACTIVE.put(player.getUUID(), new ActiveRush(target.getUUID(), definition, now, sequenceId));
        Bt3CombatLimiter.resetCombo(player);
        face(player, target);
        ModNetwork.sendToTrackingAndSelf(player, new Bt3RushStatePacket(
                Bt3RushStatePacket.Phase.START, player.getId(), target.getId(), sequenceId, definition.id()));
        return true;
    }

    public static void interrupt(ServerPlayer player) {
        if (player == null) return;
        FOLLOWUPS.remove(player.getUUID());
        ActiveRush active = ACTIVE.remove(player.getUUID());
        if (active != null) {
            sendCancel(player, active);
            Entity raw = player.serverLevel().getEntity(active.targetId());
            NeoForge.EVENT_BUS.post(new RushEvent.Interrupt(player,
                    raw instanceof LivingEntity living ? living : null, active.definition().id()));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int now = tick(player);
        Bt3RushFollowup.Gate gate = FOLLOWUPS.get(player.getUUID());
        if (Bt3RushFollowup.expired(gate, now)) FOLLOWUPS.remove(player.getUUID());

        ActiveRush active = ACTIVE.get(player.getUUID());
        if (active == null) return;
        if (!player.isAlive() || player.isSpectator() || !XenoServerConfig.bt3CinematicRushEnabled) {
            interrupt(player);
            return;
        }
        Entity raw = player.serverLevel().getEntity(active.targetId());
        if (!(raw instanceof LivingEntity target) || !target.isAlive()
                || player.distanceTo(target) > MAX_ACTIVE_RANGE) {
            interrupt(player);
            return;
        }

        int elapsed = now - active.startedAtTick();
        face(player, target);
        pullTowardTarget(player, target);
        while (active.nextImpact() < active.definition().impactCount()
                && elapsed >= active.definition().impactTick(active.nextImpact())) {
            if (!landImpact(player, target, active, active.nextImpact())) {
                interrupt(player);
                return;
            }
            active.advanceImpact();
        }
        if (elapsed >= active.definition().durationTicks()) {
            ACTIVE.remove(player.getUUID());
            sendCancel(player, active);
            player.setDeltaMovement(Vec3.ZERO);
            player.hasImpulse = true;
        }
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Pre event) {
        if (event.getNewDamage() <= 0.05f) return;
        if (event.getEntity() instanceof ServerPlayer player && isActive(player)) interrupt(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) interrupt(player);
    }

    private static boolean landImpact(ServerPlayer player, LivingEntity target, ActiveRush active, int index) {
        if (player.distanceTo(target) > MAX_START_RANGE) return false;
        if (target instanceof ServerPlayer defender && Bt3CombatEvents.isGuarding(defender)) return false;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        Resources resources = data == null ? null : data.getResources();
        boolean finisher = active.definition().isFinalImpact(index);
        float cost = finisher ? XenoServerConfig.finisherKiCost : XenoServerConfig.comboKiCost;
        if (!trySpendKi(resources, cost)) return false;

        float base = (float) Math.max(1.0, player.getAttackStrengthScale(0.5f) * 4.0f);
        if (data != null) base = (float) Math.max(base, data.getMeleeDamage() * 0.35);
        float comboStepScale = 1.0f + (4 + index) * 0.12f;
        float multiplier = comboStepScale * XenoServerConfig.comboDamageScale;
        if (finisher) multiplier *= XenoServerConfig.finisherDamageScale;
        float dealt = base * multiplier;
        if (!target.hurt(player.damageSources().playerAttack(player), dealt)) return false;
        NeoForge.EVENT_BUS.post(new RushEvent.Impact(
                player, target, active.definition().id(), index, dealt, finisher));

        Vec3 away = target.position().subtract(player.position());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        if (flat.lengthSqr() < 1.0e-4) {
            Vec3 look = player.getLookAngle();
            flat = new Vec3(look.x, 0, look.z);
        }
        if (flat.lengthSqr() > 1.0e-4) flat = flat.normalize();
        if (finisher) {
            CombatKnockback.add(target, flat.scale(1.85).add(0, 0.58, 0));
        } else {
            // The non-finisher beat damps the victim rather than adding to it, so it needs the
            // absolute form -- but it is still knockback, and a master must not be dragged either.
            CombatKnockback.set(target,
                    target.getDeltaMovement().scale(0.35).add(flat.scale(0.08)));
        }
        player.serverLevel().playSound(null, target.blockPosition(),
                finisher ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS, finisher ? 1.15f : 0.8f, finisher ? 0.8f : 1.05f + index * 0.06f);
        CombatFx.impact(player.serverLevel(), target, flat,
                finisher ? CombatFx.Weight.HEAVY : CombatFx.Weight.LIGHT);
        return true;
    }

    private static void pullTowardTarget(ServerPlayer player, LivingEntity target) {
        Vec3 delta = target.position().subtract(player.position());
        Vec3 flat = new Vec3(delta.x, 0, delta.z);
        double distance = flat.length();
        if (distance <= 2.2 || distance < 1.0e-4) return;
        double speed = Math.min(0.42, Math.max(0.12, (distance - 2.0) * 0.18));
        Vec3 current = player.getDeltaMovement();
        Vec3 drive = flat.scale(1.0 / distance).scale(speed);
        player.setDeltaMovement(drive.x, current.y * 0.25, drive.z);
        player.hasImpulse = true;
    }

    private static void face(ServerPlayer player, LivingEntity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
    }

    private static Bt3RushDefinition resolve(ServerPlayer player) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null || data.getCharacter() == null) return Bt3RushResolver.resolve(null, null);
        return Bt3RushResolver.resolve(data.getCharacter().getRace(), data.getCharacter().getActiveForm());
    }

    private static boolean trySpendKi(Resources resources, float cost) {
        if (cost <= 0f) return true;
        if (resources == null || resources.getCurrentEnergy() < cost) return false;
        resources.removeEnergy(cost);
        return true;
    }

    private static int tick(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        return server == null ? 0 : server.getTickCount();
    }

    private static void sendCancel(ServerPlayer player, ActiveRush active) {
        ModNetwork.sendToTrackingAndSelf(player, new Bt3RushStatePacket(
                Bt3RushStatePacket.Phase.CANCEL, player.getId(), -1, active.sequenceId(),
                active.definition().id()));
    }

    private static final class ActiveRush {
        private final UUID targetId;
        private final Bt3RushDefinition definition;
        private final int startedAtTick;
        private final int sequenceId;
        private int nextImpact;

        private ActiveRush(UUID targetId, Bt3RushDefinition definition, int startedAtTick, int sequenceId) {
            this.targetId = targetId;
            this.definition = definition;
            this.startedAtTick = startedAtTick;
            this.sequenceId = sequenceId;
        }

        UUID targetId() { return targetId; }
        Bt3RushDefinition definition() { return definition; }
        int startedAtTick() { return startedAtTick; }
        int sequenceId() { return sequenceId; }
        int nextImpact() { return nextImpact; }
        void advanceImpact() { nextImpact++; }
    }
}
