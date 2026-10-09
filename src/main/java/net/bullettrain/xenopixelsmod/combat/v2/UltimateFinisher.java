package net.bullettrain.xenopixelsmod.combat.v2;

import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.network.S2C.MeleeAnimationS2C;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.dragonminez.server.events.players.combat.KnockbackHelper;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.event.GrabEvent;
import net.bullettrain.xenopixelsmod.combat.Bt3CinematicRushSystem;
import net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.combat.HakaiChannelSystem;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.combat.beam.BeamSurgeManager;
import net.bullettrain.xenopixelsmod.combat.combo.ComboRouteMachine;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.technique.KiFixedAim;
import net.bullettrain.xenopixelsmod.combat.technique.UltimateFinisherTechnique;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.ChaseFlightSystem;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.Bt3AnimIntentPacket;
import net.bullettrain.xenopixelsmod.network.packet.UltimateFinisherCameraPacket;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned approach, six punches, grab, arc throw, Kamehameha charge and Surge. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class UltimateFinisher {
    private static final double RANGE = 48.0;
    private static final String COOLDOWN = "TechniqueCooldown_" + UltimateFinisherTechnique.ID;
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static int nextSequence;
    private UltimateFinisher() {}

    public static boolean owns(ServerPlayer player) {
        if (ACTIVE.containsKey(player.getUUID())) return true;
        return ACTIVE.values().stream().anyMatch(a -> a.target == player && a.phase != UltimateFinisherRules.Phase.BEAM);
    }

    /** Always handled for our selected id, including a refused cast: DMZ must not also strike. */
    public static void cast(ServerPlayer player, StrikeAttackData strike, int targetId) {
        if (player == null || strike == null || !UltimateFinisherTechnique.ID.equals(strike.getId())) return;
        StatsData stats = V2Support.stats(player);
        LivingEntity target = V2Support.living(player, targetId);
        String refusal = V2Lock.refusal(player, target);
        if (refusal == null) refusal = V2Support.refusal(player, target);
        if (refusal == null && (player.distanceTo(target) > RANGE || !V2Targeting.sight(player, target))) {
            refusal = "Target is too far away or behind a wall";
        }
        if (refusal == null) refusal = V2Grab.refusal(player, target);
        if (refusal == null && ACTIVE.values().stream().anyMatch(a -> a.target == target
                && a.phase != UltimateFinisherRules.Phase.BEAM)) {
            refusal = "Target is already in UltimateFinisher";
        }
        if (!XenoServerConfig.bt3CombatEnabled || !player.isAlive() || player.isSpectator()
                || stats == null || !stats.getStatus().isHasCreatedCharacter()
                || stats.getSkills().getSkillLevel("kicontrol") < 1
                || stats.getSkills().getSkillLevel("fly") < 1
                || stats.getResources().getPowerRelease() < 5 || !V2Support.emptyHands(player)
                || V2Support.dmzStunned(player) || owns(player)
                || (target instanceof ServerPlayer other && (owns(other) || V2Support.dmzStunned(other)))
                || ComboRouteMachine.isBusy(player.getUUID()) || Bt3CinematicRushSystem.isActive(player)
                || HakaiChannelSystem.isChanneling(player)
                || !stats.getTechniques().getUnlockedTechniques().containsKey(UltimateFinisherTechnique.ID)
                || stats.getCooldowns().hasCooldown(COOLDOWN)) return;
        if (refusal != null) {
            V2Support.hint(player, refusal);
            return;
        }
        KiAttackData kamehameha = PredefinedTechniques.REGISTRY.get("kamehameha");
        if (kamehameha == null) return;
        if (!V2Support.spend(player, (float) Math.ceil(strike.getCalculatedCost(stats)), 0)) {
            V2Support.hint(player, "Not enough ki");
            return;
        }
        stats.getCooldowns().setCooldown(COOLDOWN, UltimateFinisherTechnique.COOLDOWN_TICKS);
        V2Fighter current = V2FighterStore.get(player);
        V2Motion.stopTravel(player, current);
        V2Rush.interrupt(player, current);
        V2Grab.release(player, current, null);
        current.clearCombo();
        V2Charges.cancel(player, current);
        ChaseFlightSystem.stopChase(player);
        Bt3CombatEvents.setGuarding(player, false);
        DmzAnimHelper.broadcastBlockStop(player);
        Active a = new Active(player, target, kamehameha, ++nextSequence, V2Support.tick(player));
        ACTIVE.put(player.getUUID(), a);
        lock(player, true);
        V2Motion.startTravel(player, a.travel, target, V2Fighter.TravelKind.ULTIMATE_FINISHER, 1.8, 2.4, 0);
        camera(a, UltimateFinisherRules.Phase.APPROACH);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        for (Active a : new ArrayList<>(ACTIVE.values())) tick(a, event.getServer().getTickCount());
    }

    private static void tick(Active a, int now) {
        ServerPlayer player = a.player;
        LivingEntity target = a.target;
        if (!XenoServerConfig.bt3CombatEnabled || !player.isAlive() || player.isSpectator()
                || !target.isAlive() || target.isRemoved() || player.level() != target.level()
                || player.isPassenger() || target.isPassenger() || target.isVehicle()
                || player.distanceTo(target) > RANGE + 16 || V2Support.refusal(player, target) != null
                || !CombatKnockback.canKnockBack(target) || now - a.started > 240) {
            stop(a);
            return;
        }
        if (a.arrived < 0) {
            aim(player, target);
            V2Fighter.TravelKind arrived = V2Motion.tickTravel(player, a.travel, now);
            if (arrived == null) {
                if (!a.travel.traveling()) stop(a);
                return;
            }
            if (!V2Targeting.canStrike(player, target, 3.5, -1.0)) { stop(a); return; }
            a.arrived = now;
            player.setNoGravity(true);
        }
        int elapsed = now - a.arrived;
        UltimateFinisherRules.Phase phase = UltimateFinisherRules.phase(elapsed);
        if (phase == UltimateFinisherRules.Phase.STOP) { stop(a); return; }
        if (phase != a.phase) {
            if (!enter(a, phase)) { stop(a); return; }
            camera(a, phase);
        }
        // Always face the victim. Copying wave yaw here was drifting the beam off to the side.
        aim(player, target);
        motion(player, Vec3.ZERO);
        switch (phase) {
            case COMBO -> {
                if (!V2Targeting.canStrike(player, target, 3.5, -1.0)) { stop(a); return; }
                while (a.punches < UltimateFinisherRules.punchesDue(elapsed)) {
                    String punch = a.punches % 2 == 0 ? DmzAnimHelper.PUNCH_RIGHT : DmzAnimHelper.PUNCH_LEFT;
                    NetworkHandler.sendToTrackingEntityAndSelf(new MeleeAnimationS2C(player.getId(), punch, false, 1.08f), player);
                    if (!V2Damage.strike(player, target, 0.35f)) { stop(a); return; }
                    if (!a.targetHeld) holdTarget(a);
                    a.punches++;
                    CombatKnockback.set(target, Vec3.ZERO);
                    CombatFx.impact(player.serverLevel(), player, target, target.position().subtract(player.position()), CombatFx.Weight.LIGHT);
                }
            }
            case GRAB -> {
                Vec3 front = player.getLookAngle().multiply(1, 0, 1).normalize();
                double distance = net.bullettrain.xenopixelsmod.combat.v2.grab.GrabRules.holdDistance(player.getBbWidth(), target.getBbWidth());
                Vec3 hold = player.position().add(front.scale(distance)).add(0, 0.1, 0);
                if (!target.level().noCollision(target, target.getBoundingBox().move(hold.subtract(target.position())))) { stop(a); return; }
                V2Grab.pin(player, target);
            }
            case THROW -> {
                int step = elapsed - UltimateFinisherRules.THROW_START + 1;
                if (!UltimateFinisherThrow.step(target, a.throwOrigin, a.throwDirection, step)) { stop(a); return; }
            }
            case CHARGE -> {
                CombatKnockback.set(target, Vec3.ZERO);
                if (a.wave == null || !a.wave.isAlive()) { stop(a); return; }
                a.wave.setCastSize(0.2f + (elapsed - UltimateFinisherRules.CHARGE_START) / 40f);
            }
            case BEAM -> {
                // Keep the victim frozen for the wave and the post-wave hold (owner: +4s).
                CombatKnockback.set(target, Vec3.ZERO);
                pinHeldTarget(a);
                if (UltimateFinisherRules.beamWaveActive(elapsed)) {
                    if (a.wave == null || !a.wave.isAlive()) {
                        // Wave ended early: still serve the remaining post-freeze window.
                        return;
                    }
                    // Re-aim every tick at the frozen victim centre so the beam does not drift right/left.
                    aimWaveAtTarget(a);
                    BeamSurgeManager.reportFeeding(player);
                }
            }
            default -> {}
        }
    }

    private static boolean enter(Active a, UltimateFinisherRules.Phase phase) {
        ServerPlayer player = a.player;
        LivingEntity target = a.target;
        if (phase == UltimateFinisherRules.Phase.GRAB) {
            if (V2Grab.refusal(player, target) != null
                    || NeoForge.EVENT_BUS.post(new GrabEvent.Connect(player, target)).isCanceled()) return false;
            ModNetwork.sendToTrackingAndSelf(player, new Bt3AnimIntentPacket(player.getId(), V2Grab.REACH_POSE));
        } else if (phase == UltimateFinisherRules.Phase.THROW) {
            a.throwOrigin = target.position();
            Vec3 direction = target.position().subtract(player.position()).multiply(1, 0, 1);
            a.throwDirection = direction.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : direction.normalize();
            ModNetwork.sendToTrackingAndSelf(player, new Bt3AnimIntentPacket(player.getId(), Bt3AnimationIntent.HEAVY_FINISH));
            NeoForge.EVENT_BUS.post(new GrabEvent.Throw(player, target, 0));
        } else if (phase == UltimateFinisherRules.Phase.CHARGE) {
            StatsData stats = V2Support.stats(player);
            if (stats == null) return false;
            a.wave = UltimateFinisherKamehameha.start(player, stats, a.kamehameha);
            if (a.wave == null) return false;
            animation(player, a.kamehameha.getAnimationPrefix() + "_cast", 1);
        } else if (phase == UltimateFinisherRules.Phase.BEAM) {
            if (a.wave == null || !a.wave.isAlive()) return false;
            aim(player, target);
            StatsData stats = V2Support.stats(player);
            if (stats == null || !UltimateFinisherKamehameha.release(player, stats, a.kamehameha, a.wave)) return false;
            // DMZ kame setups use OFFSET_X=0.4 (right hand). That muzzle offset makes the beam
            // miss to the right of the victim; centre the wave on the eye→target line instead.
            a.wave.setContinuousFollow(false);
            a.wave.setCastOffsets(0f, 0f, 0f);
            Vec3 to = target.getBoundingBox().getCenter();
            Vec3 from = player.getEyePosition();
            Vec3 dir = to.subtract(from);
            if (dir.lengthSqr() < 1.0E-8) dir = player.getLookAngle();
            else dir = dir.normalize();
            a.wave.setPos(from.add(dir.scale(1.5)));
            aimWaveAtTarget(a);
            animation(player, a.kamehameha.getAnimationPrefix() + "_fire", 1);
            // Do not free the victim here — freeze continues through the wave and POST_BEAM_FREEZE_TICKS.
            if (!a.targetHeld) holdTarget(a);
            BeamSurgeManager.reportFeeding(player);
        }
        return true;
    }

    /** Re-pin a held victim each beam tick so knockback/AI cannot walk them off the freeze. */
    private static void pinHeldTarget(Active a) {
        if (!a.targetHeld || a.target == null || !a.target.isAlive()) return;
        a.target.setDeltaMovement(Vec3.ZERO);
        a.target.hurtMarked = true;
        a.target.hasImpulse = true;
        a.target.setNoGravity(true);
    }

    /** A confirmed first punch commits the victim; refused damage never changes their movement. */
    private static void holdTarget(Active a) {
        LivingEntity target = a.target;
        if (target instanceof ServerPlayer defender) {
            Bt3CombatEvents.setGuarding(defender, false);
            DmzAnimHelper.broadcastBlockStop(defender);
            Bt3CinematicRushSystem.interrupt(defender);
            ComboRouteMachine.cancel(defender);
            ChaseFlightSystem.stopChase(defender);
            V2Fighter f = V2FighterStore.get(defender);
            V2Motion.stopTravel(defender, f);
            V2Rush.interrupt(defender, f);
            f.clearCombo();
            lock(defender, true);
            a.targetLocked = true;
            ModNetwork.sendToPlayer(defender, new net.bullettrain.xenopixelsmod.network.packet.ChaseFlightStatePacket(true));
        }
        a.targetNoGravity = target.isNoGravity();
        if (target instanceof Mob mob) {
            a.targetNoAi = mob.isNoAi();
            mob.setNoAi(true);
        }
        a.targetHeld = true;
        target.setNoGravity(true);
    }

    private static void aim(ServerPlayer player, LivingEntity target) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
        player.setYHeadRot(player.getYRot());
        player.yBodyRot = player.getYRot();
    }

    /**
     * Keep the finisher wave centred on the eye→victim line and pointed at the victim.
     * Clears DMZ's right-hand cast offset every tick so KiWaveEntity.tick cannot push OFFSET_X
     * back to 0.4 and miss right of the target.
     */
    private static void aimWaveAtTarget(Active a) {
        if (a.wave == null || a.target == null || !a.target.isAlive() || a.player == null) return;
        a.wave.setContinuousFollow(false);
        a.wave.setCastOffsets(0f, 0f, 0f);
        Vec3 to = a.target.getBoundingBox().getCenter();
        Vec3 eye = a.player.getEyePosition();
        Vec3 line = to.subtract(eye);
        if (line.lengthSqr() < 1.0E-8) line = a.player.getLookAngle();
        else line = line.normalize();
        a.wave.setPos(eye.add(line.scale(1.5)));
        Vec3 from = a.wave.position();
        Vec3 d = to.subtract(from);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        if (horiz < 1.0E-8 && Math.abs(d.y) < 1.0E-8) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = horiz < 1.0E-8 ? (d.y > 0 ? -90f : 90f)
                : (float) (-Math.toDegrees(Math.atan2(d.y, horiz)));
        a.wave.setYRot(yaw);
        a.wave.setXRot(pitch);
        if (a.wave instanceof KiFixedAim fixed) fixed.xenopixels$setFixedAim(yaw, pitch);
    }

    private static void motion(ServerPlayer player, Vec3 velocity) {
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.resetFallDistance();
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static void lock(ServerPlayer player, boolean locked) {
        StatsData stats = V2Support.stats(player);
        if (stats == null) return;
        stats.getStatus().setStrikeLocked(locked);
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
    }

    private static void animation(ServerPlayer player, String name, int hold) {
        NetworkHandler.sendToTrackingEntityAndSelf(new TriggerAnimationS2C(player.getUUID(),
                TriggerAnimationS2C.AnimationType.KI_ANIMATION, hold, -1, name), player);
    }

    private static void camera(Active a, UltimateFinisherRules.Phase phase) {
        a.phase = phase;
        ModNetwork.sendToPlayer(a.player, new UltimateFinisherCameraPacket(a.target.getId(), a.sequence, phase));
    }

    private static void freeTarget(Active a) {
        if (a.targetLocked && a.target instanceof ServerPlayer defender) {
            a.targetLocked = false;
            lock(defender, false);
        }
        if (!a.targetHeld) return;
        a.targetHeld = false;
        if (a.target instanceof Mob mob) mob.setNoAi(a.targetNoAi);
        a.target.setNoGravity(a.targetNoGravity);
        a.target.resetFallDistance();
        if (a.target instanceof ServerPlayer defender) {
            ModNetwork.sendToPlayer(defender, new net.bullettrain.xenopixelsmod.network.packet.ChaseFlightStatePacket(false));
            Bt3CombatEvents.grantFallGrace(defender, 200);
        }
    }

    private static void stop(Active a) {
        if (ACTIVE.remove(a.player.getUUID()) == null) return;
        V2Motion.stopTravel(a.player, a.travel);
        a.player.setNoGravity(a.playerNoGravity);
        freeTarget(a);
        lock(a.player, false);
        motion(a.player, Vec3.ZERO);
        Bt3CombatEvents.grantFallGrace(a.player, 200);
        if (a.wave != null && !a.wave.isFiring()) a.wave.discard();
        NetworkHandler.sendToTrackingEntityAndSelf(new TriggerAnimationS2C(a.player.getUUID(),
                TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP, 0, -1, ""), a.player);
        camera(a, UltimateFinisherRules.Phase.STOP);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && event.getSource().getDirectEntity() instanceof KiWaveEntity wave
                && wave.isFiring() && UltimateFinisherKamehameha.owns(wave)
                && CombatKnockback.canKnockBack(event.getEntity())
                && (!(wave.getOwner() instanceof ServerPlayer caster)
                || V2Support.refusal(caster, event.getEntity()) == null)) {
            // Vanilla hurt applies its knockback after this event. Consume this witness at DMZ's return.
            wave.getPersistentData().putUUID("xenopixelsmod.finisher_accepted_damage", event.getEntity().getUUID());
        }
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0) {
            Active a = ACTIVE.get(player.getUUID());
            if (a != null) stop(a);
        }
    }

    public static void afterWaveDamage(KiWaveEntity wave, net.minecraft.world.entity.Entity hit, boolean accepted) {
        var data = wave.getPersistentData();
        String key = "xenopixelsmod.finisher_accepted_damage";
        if (!data.hasUUID(key)) return;
        java.util.UUID victimId = data.getUUID(key);
        data.remove(key);
        hit = com.dragonminez.common.combat.logic.player.TargetHelper.resolveHittable(hit);
        if (!accepted || wave.level().isClientSide || !wave.isFiring() || !UltimateFinisherKamehameha.owns(wave)
                || !(hit instanceof LivingEntity victim) || !victim.getUUID().equals(victimId)
                || !CombatKnockback.canKnockBack(victim)
                || (wave.getOwner() instanceof ServerPlayer caster && V2Support.refusal(caster, victim) != null)) return;
        Vec3 velocity = Vec3.directionFromRotation(wave.getXRot(), wave.getYRot()).scale(1.1).add(0, 0.25, 0);
        CombatKnockback.set(victim, velocity);
        KnockbackHelper.apply(victim, velocity);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        for (Active a : new ArrayList<>(ACTIVE.values())) {
            if (a.player == event.getEntity() || a.target == event.getEntity()) stop(a);
        }
    }

    /** Clear the old capability before respawn listeners copy its temporary strike lock. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClone(PlayerEvent.Clone event) {
        for (Active a : new ArrayList<>(ACTIVE.values())) {
            if (a.player == event.getOriginal() || a.target == event.getOriginal()) stop(a);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (Active a : new ArrayList<>(ACTIVE.values())) stop(a);
    }

    private static final class Active {
        final ServerPlayer player;
        final LivingEntity target;
        final KiAttackData kamehameha;
        final int sequence, started;
        final boolean playerNoGravity;
        boolean targetNoGravity;
        final V2Fighter travel;
        int arrived = -1, punches;
        boolean targetHeld, targetNoAi, targetLocked;
        UltimateFinisherRules.Phase phase;
        Vec3 throwOrigin, throwDirection;
        KiWaveEntity wave;

        Active(ServerPlayer player, LivingEntity target, KiAttackData kamehameha, int sequence, int started) {
            this.player = player;
            this.target = target;
            this.kamehameha = kamehameha;
            this.sequence = sequence;
            this.started = started;
            this.playerNoGravity = player.isNoGravity();
            this.targetNoGravity = target.isNoGravity();
            this.travel = new V2Fighter(player.getUUID());
        }
    }
}
