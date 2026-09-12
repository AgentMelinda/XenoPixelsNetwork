package net.bullettrain.xenopixelsmod.network;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.combat.DragonHoming;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.dragonminez.compat.util.LazyOptional;
import com.dragonminez.compat.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Budokai Tenkaichi / Sparking Zero inspired combat (server-authoritative).
 */
public class Bt3CombatPacket {
    public enum Action {
        COMBO_HIT,
        /** Vanish behind lock-on target (close range). */
        VANISH,
        /** High-speed chase into the target from mid range. */
        CHASE_DASH,
        /** Step back off the target while facing them. */
        BACKSTEP,
        /** Hold-charge fist smash (stamina). */
        CHARGE_FIST,
        /** Hold-charge kick (stamina). */
        CHARGE_KICK,
        /** Dragon dash: smash target away then chase. */
        DRAGON_DASH,
        /** Hold-to-block. comboStep 1 = start/hold, 0 = release. */
        GUARD,
        /** Super-counter vanish (after hit window). */
        SUPER_COUNTER,
        /** Mid-combo light ki blast that cancels the string. */
        KI_BLAST_CANCEL,
        /** Mid-combo Z-Burst step-in toward target. */
        Z_BURST,
        /** Air rush / chase chain after knockup. */
        RUSH_CHAIN,
        /** Sonic sway side-step (comboStep: -1 left, +1 right). */
        SONIC_SWAY,
        /** Ultimate skill smash. */
        ULTIMATE,
        /** Activate sparking mode (full meter). */
        SPARKING,
        /** Start a Hakai erasure channel on the target (Ctrl + left click). */
        HAKAI_START,
        /** Ctrl or the mouse button was released: stop channeling, no penalty. */
        HAKAI_CANCEL,
        /** Release chase hold: stop mid-flight. Appended — do not reorder. */
        CHASE_STOP,
        /** Open a Zanzoken read. Appended — do not reorder. */
        ZANZOKEN,
        /** Shi Shin No Ken: divide into several bodies, or reunite. Appended — do not reorder. */
        MULTIFORM,
        /** Sync ki charge percent to clones (0-100). Appended — do not reorder. */
        SYNC_KI_CHARGE,
        /** X-X-X then Dragon Dash button automatic cinematic rush. Appended — do not reorder. */
        CINEMATIC_RUSH
    }

    /** Fallback when config has not loaded yet. Prefer {@link XenoServerConfig#vanishGap}. */
    public static final double VANISH_GAP = 1.35;
    /** Left/right offset for A vs D vanish. */
    public static final double VANISH_SIDE = 1.05;
    /** Fallback; live chase stop uses {@link XenoServerConfig#chaseStopGap}. */
    public static final double CHASE_GAP = 0.0;
    public static final double BACKSTEP_DIST = 3.2;
    public static final double DRAGON_LAUNCH = 6.5;

    private final Action action;
    private final int targetId;
    private final int comboStep;
    /** 0..100 charge percent for charge attacks / dragon dash. */
    private final int chargePercent;
    /** Kick vertical bias: +1 hold W (up), -1 hold S (down), 0 neutral. */
    private final int verticalBias;
    /**
     * COMBO_HIT only: which held direction key was down, as a
     * {@code Bt3ComboChoreography.MASH_STYLE_*} constant. The client cannot be trusted for the
     * combo step, but it is the only side that knows which key is held, so the style rides along
     * and the server applies it to the step it owns.
     */
    private final int mashStyle;

    public Bt3CombatPacket(Action action, int targetId, int comboStep) {
        this(action, targetId, comboStep, 0, 0);
    }

    public Bt3CombatPacket(Action action, int targetId, int comboStep, int chargePercent) {
        this(action, targetId, comboStep, chargePercent, 0);
    }

    public Bt3CombatPacket(Action action, int targetId, int comboStep, int chargePercent, int verticalBias) {
        this(action, targetId, comboStep, chargePercent, verticalBias,
                net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.MASH_STYLE_ROUTE);
    }

    public Bt3CombatPacket(Action action, int targetId, int comboStep, int chargePercent, int verticalBias,
                           int mashStyle) {
        this.action = action;
        this.targetId = targetId;
        this.comboStep = comboStep;
        this.chargePercent = Math.max(0, Math.min(100, chargePercent));
        this.verticalBias = verticalBias < 0 ? -1 : (verticalBias > 0 ? 1 : 0);
        this.mashStyle = Math.max(0, Math.min(2, mashStyle));
    }

    public static void encode(Bt3CombatPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeVarInt(msg.targetId);
        buf.writeVarInt(msg.comboStep);
        buf.writeVarInt(msg.chargePercent);
        buf.writeByte(msg.verticalBias);
        buf.writeByte(msg.mashStyle);
    }

    public static Bt3CombatPacket decode(FriendlyByteBuf buf) {
        return new Bt3CombatPacket(
                buf.readEnum(Action.class),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readByte(),
                buf.readByte());
    }

    public static void handle(Bt3CombatPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.action == Action.SYNC_KI_CHARGE) {
                // Protocol compatibility only. Older clients may still send this obsolete
                // melee-charge message; it must not enter target validation or consume pacing.
                return;
            }
            if (msg.action == Action.CHASE_STOP) {
                ChaseFlightSystem.stopChase(player);
                return;
            }
            if (msg.action == Action.ZANZOKEN) {
                handleZanzoken(player);
                return;
            }
            if (msg.action == Action.MULTIFORM) {
                handleMultiForm(player);
                return;
            }
            if (msg.action == Action.HAKAI_CANCEL) {
                net.bullettrain.xenopixelsmod.combat.HakaiChannelSystem.cancel(player, null);
                return;
            }
            if (msg.action == Action.GUARD && msg.comboStep == 0) {
                net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.setGuarding(player, false);
                DmzAnimHelper.broadcastBlockStop(player);
                return;
            }
            try {
                if (!XenoServerConfig.bt3CombatEnabled || !player.isAlive() || player.isSpectator()
                        || player.getVehicle() instanceof net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity) return;
                if ((msg.action == Action.COMBO_HIT || msg.action == Action.CHARGE_FIST
                        || msg.action == Action.CINEMATIC_RUSH)
                        && !net.bullettrain.xenopixelsmod.combat.FistInputPolicy.emptyHands(
                                player.getMainHandItem().isEmpty(), player.getOffhandItem().isEmpty(),
                                com.dragonminez.common.combat.logic.player.PlayerAttackHelper.isKiWeaponActive(player))) return;

                LivingEntity target = null;
                if (msg.targetId > 0) {
                    Entity raw = player.level().getEntity(msg.targetId);
                    if (raw instanceof LivingEntity living && living.isAlive()) {
                        target = living;
                    }
                }
                if (msg.action == Action.CHASE_DASH) {
                    LivingEntity homingVictim = DragonHoming.victim(player);
                    if (homingVictim != null) {
                        target = homingVictim;
                    }
                }

                // Combo / kick / guard / ki-blast may run without a target.
                if (target == null
                        && msg.action != Action.CHARGE_FIST
                        && msg.action != Action.CHARGE_KICK
                        && msg.action != Action.COMBO_HIT
                        && msg.action != Action.GUARD
                        && msg.action != Action.KI_BLAST_CANCEL
                        && msg.action != Action.SONIC_SWAY
                        && msg.action != Action.SPARKING
                        && msg.action != Action.ULTIMATE
                        && msg.action != Action.HAKAI_CANCEL
                        && msg.action != Action.HAKAI_START
                        && msg.action != Action.CHASE_STOP) {
                    return;
                }
                // Never let BT3 tools hit DMZ masters
                if (target != null && net.bullettrain.xenopixelsmod.event.DmzMasterProtection.isDmzMaster(target)
                        && msg.action != Action.GUARD && msg.action != Action.SPARKING
                        && msg.action != Action.HAKAI_CANCEL) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§7Masters cannot be attacked"), true);
                    return;
                }

                LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
                StatsData data = opt.orElse(null);
                Resources res = data != null ? data.getResources() : null;

                if (net.bullettrain.xenopixelsmod.combat.Bt3CinematicRushSystem.isActive(player)
                        && msg.action != Action.HAKAI_CANCEL && msg.action != Action.CHASE_STOP) {
                    return;
                }

                // Server-authoritative pacing: cooldowns, costs, and the combo step are never taken
                // from the client. A forged client replays these packets at network speed; this
                // rejects anything faster than a human can swing. GUARD is a hold toggle and SPARKING
                // is meter-gated, so both are exempt from the general action floor.
                if (msg.action != Action.GUARD && msg.action != Action.SPARKING
                        && msg.action != Action.HAKAI_CANCEL
                        && msg.action != Action.HAKAI_START
                        && msg.action != Action.CHASE_STOP
                        && msg.action != Action.CINEMATIC_RUSH
                        && !net.bullettrain.xenopixelsmod.combat.Bt3CombatLimiter.canAct(player)) {
                    return;
                }

                switch (msg.action) {
                    case VANISH -> {
                        // Lock-on only (client); comboStep encodes side: -1 left (A), +1 right (D)
                        // If counter window is open, upgrade to super-counter
                        if (target == null) return;
                        if (XenoServerConfig.bt3SuperCounterEnabled
                                && net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.consumeCounterWindow(player)) {
                            handleSuperCounter(player, target, res, data, msg.comboStep);
                            return;
                        }
                        if (!XenoServerConfig.bt3VanishEnabled) return;
                        int side = msg.comboStep < 0 ? -1 : (msg.comboStep > 0 ? 1 : 0);
                        handleVanish(player, target, res, side);
                    }
                    case CHASE_DASH -> {
                        if (!XenoServerConfig.bt3ChaseDashEnabled || target == null) return;
                        handleChase(player, target, res);
                    }
                    case CHASE_STOP -> ChaseFlightSystem.stopChase(player);
                    case BACKSTEP -> {
                        if (!XenoServerConfig.bt3BackstepEnabled || target == null) return;
                        handleBackstep(player, target, res);
                    }
                    case COMBO_HIT -> {
                        // Freelook or lock-on — target optional for counter advance
                        if (!XenoServerConfig.bt3ComboEnabled) return;
                        if (target != null && player.distanceTo(target) > 48.0) return;
                        handleCombo(player, target, res, data, msg.verticalBias, msg.mashStyle);
                    }
                    case CINEMATIC_RUSH -> {
                        if (target == null) return;
                        net.bullettrain.xenopixelsmod.combat.Bt3CinematicRushSystem.tryStart(player, target);
                    }
                    case CHARGE_FIST -> {
                        // Punch: lock-on / freelook if in range, otherwise swing into empty space
                        if (!XenoServerConfig.bt3ChargeAttackEnabled) return;
                        if (target != null && target.isAlive()
                                && player.distanceTo(target) <= XenoServerConfig.chargeAttackRange + 1.5) {
                            handleChargeAttack(player, target, res, data, false, msg.chargePercent, 0);
                        } else {
                            handleUntargetedCharge(player, res, data, false, msg.chargePercent, 0);
                        }
                    }
                    case CHARGE_KICK -> {
                        // Kick: freelook or air
                        if (!XenoServerConfig.bt3ChargeAttackEnabled) return;
                        if (target != null) {
                            handleChargeAttack(player, target, res, data, true, msg.chargePercent, msg.verticalBias);
                        } else {
                            handleUntargetedCharge(player, res, data, true, msg.chargePercent, msg.verticalBias);
                        }
                    }
                    case SYNC_KI_CHARGE -> { }
                    case DRAGON_DASH -> {
                        // Lock-on only (client); server requires target
                        if (!XenoServerConfig.bt3DragonDashEnabled || target == null) return;
                        handleDragonDash(player, target, res, data, msg.chargePercent);
                    }
                    case GUARD -> {
                        if (!XenoServerConfig.bt3GuardEnabled) return;
                        boolean hold = msg.comboStep != 0;
                        if (net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.isGuardStunned(player)) {
                            hold = false;
                        }
                        boolean was = net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.isGuarding(player);
                        net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.setGuarding(player, hold);
                        // DMZ-style block hold pose for nearby players
                        if (hold && !was) {
                            DmzAnimHelper.broadcastBlockStart(player);
                        } else if (!hold && was) {
                            DmzAnimHelper.broadcastBlockStop(player);
                        }
                    }
                    case SUPER_COUNTER -> {
                        if (!XenoServerConfig.bt3SuperCounterEnabled || target == null) return;
                        if (!net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.consumeCounterWindow(player)) return;
                        handleSuperCounter(player, target, res, data, msg.comboStep);
                    }
                    case KI_BLAST_CANCEL -> {
                        if (!XenoServerConfig.bt3KiBlastCancelEnabled) return;
                        handleKiBlastCancel(player, target, res, data);
                    }
                    case Z_BURST -> {
                        if (!XenoServerConfig.bt3ZBurstEnabled || target == null) return;
                        handleZBurst(player, target, res, data);
                    }
                    case RUSH_CHAIN -> {
                        if (!XenoServerConfig.bt3RushChainEnabled || target == null) return;
                        handleRushChain(player, target, res, data);
                    }
                    case SONIC_SWAY -> {
                        if (!XenoServerConfig.bt3SonicSwayEnabled) return;
                        // Server-side cooldown: the client-side one is decorative.
                        if (!net.bullettrain.xenopixelsmod.combat.Bt3CombatLimiter.canSonicSway(player)) return;
                        handleSonicSway(player, res, msg.comboStep);
                    }
                    case ULTIMATE -> {
                        if (!XenoServerConfig.bt3UltimateEnabled) return;
                        // Server-side cooldown: the client-side one is decorative.
                        if (!net.bullettrain.xenopixelsmod.combat.Bt3CombatLimiter.canUltimate(player)) return;
                        handleUltimate(player, target, res, data);
                    }
                    case SPARKING -> {
                        if (!XenoServerConfig.bt3SparkingEnabled) return;
                        net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem.tryActivate(player);
                    }
                    case HAKAI_START -> startHakai(player, target);
                    case HAKAI_CANCEL -> net.bullettrain.xenopixelsmod.combat.HakaiChannelSystem.cancel(player, null);
                }
            } finally {
                if (msg.action == Action.CHASE_DASH) ChaseFlightSystem.syncState(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * @param side -1 = double-tap A (left of behind), +1 = double-tap D (right of behind)
     */
    private static void handleVanish(ServerPlayer player, LivingEntity target, Resources res, int side) {
        if (player.distanceTo(target) > XenoServerConfig.vanishMaxRange) return;
        if (!trySpendKi(res, XenoServerConfig.vanishKiCost)) return;

        Vec3 from = player.position();
        playItSound(player, from.x, from.y, from.z, true);

        Vec3 dest = vanishLanding(player, target, side);
        // Face-on teleport: syncs camera + kills residual flight (teleportTo alone desyncs DMZ flight)
        teleportFacing(player, dest, target);
        playItSound(player, dest.x, dest.y, dest.z, false);
        // Stamped after the teleport so the shade is left behind the fighter rather than being
        // spawned on top of them for a tick.
net.bullettrain.xenopixelsmod.combat.VanishShadeFx.spawn(player, from);
        CombatFx.cue(player.serverLevel(), from, CombatFxKind.VANISH_CLAP, 1.0f);
    }

    private static void handleChase(ServerPlayer player, LivingEntity target, Resources res) {
        if (ChaseFlightSystem.isChasing(player, target)) return;
        boolean homing = DragonHoming.isLive(player, target);
        double dist = player.distanceTo(target);
        if (!XenoServerConfig.chaseRangeUnlimited()) {
            if (homing) {
                if (dist > XenoServerConfig.chaseMaxRange * 1.5) return;
            } else if (dist > XenoServerConfig.chaseMaxRange) {
                return;
            }
        }
        if (!homing && dist < 2.5) {
            return;
        }
        if (!trySpendKi(res, XenoServerConfig.chaseKiCost)) return;

        if (homing) {
            DragonHoming.close(player);
            Vec3 from = player.position();
            playItSound(player, from.x, from.y, from.z, true);
            ChaseFlightSystem.start(player, target);
            return;
        }

        if (!rollChaseSuccess(player)) {
            Vec3 toTarget = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
            if (toTarget.lengthSqr() > 1.0e-4) {
                player.setDeltaMovement(toTarget.normalize().scale(0.2).add(0, 0.02, 0));
                player.hurtMarked = true;
                player.hasImpulse = true;
            }
            faceTarget(player, target);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Chase failed"), true);
            return;
        }

        Vec3 from = player.position();
        playItSound(player, from.x, from.y, from.z, true);
        ChaseFlightSystem.start(player, target);
    }

    private static void handleBackstep(ServerPlayer player, LivingEntity target, Resources res) {
        if (player.distanceTo(target) > XenoServerConfig.backstepMaxRange) return;
        if (!trySpendKi(res, XenoServerConfig.backstepKiCost)) return;

        Vec3 from = player.position();
        playItSound(player, from.x, from.y, from.z, true);

        Vec3 dest = backstepDest(player, target);
        teleportFacing(player, dest, target);
        playItSound(player, dest.x, dest.y, dest.z, false);
    }

    /**
     * Charge punch or kick with no lock-on: play the release anim and hit a short cone ahead.
     * Stay planted; only entities in the cone take knockback.
     */
    private static void handleUntargetedCharge(ServerPlayer player, Resources res, StatsData data,
                                               boolean kick, int chargePercent, int verticalBias) {
        float charge = Math.max(0.25f, Math.min(1f, chargePercent / 100f));
        float stamCost = kick
                ? XenoServerConfig.kickReleaseStamina(charge, verticalBias)
                : XenoServerConfig.fistReleaseStamina(charge);
        if (!trySpendStamina(res, stamCost)) return;

        boolean full = charge >= 0.95f;
        DmzAnimHelper.ChargeStyle style = kick
                ? DmzAnimHelper.ChargeStyle.KICK
                : (full ? DmzAnimHelper.ChargeStyle.FIST_HEAVY : DmzAnimHelper.ChargeStyle.FIST_LIGHT);
        DmzAnimHelper.playChargeRelease(player, style, full, kick ? verticalBias : 0, true);

        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0e-4) {
            flat = new Vec3(0, 0, 1);
        }
        flat = flat.normalize();

        double range = kick ? kickHitRange(charge, verticalBias) : 5.0;
        float base = (float) Math.max(2.0, player.getAttackStrengthScale(0.5f) * 5.0f);
        if (data != null) {
            base = (float) Math.max(base, data.getMeleeDamage() * 0.45);
        }
        float mult = XenoServerConfig.chargeDamageScale * (0.55f + 0.7f * charge);
        if (kick) mult *= XenoServerConfig.kickDamageScale;

        boolean anyHit = false;
        double inflate = kick && verticalBias < 0 ? 1.85 : 1.35;
        var box = player.getBoundingBox().expandTowards(flat.scale(range)).inflate(inflate);
        for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive())) {
            Vec3 to = living.position().add(0, living.getBbHeight() * 0.4, 0).subtract(player.getEyePosition());
            double distSq = to.lengthSqr();
            if (distSq > range * range) continue;
            Vec3 flatTo = new Vec3(to.x, 0, to.z);
            if (flatTo.lengthSqr() < 1.0e-4) continue;
            if (flat.dot(flatTo.normalize()) < 0.25) continue;

            living.hurt(player.damageSources().playerAttack(player), base * mult);
            if (kick) {
                CombatKnockback.set(living, kickTargetLaunch(flat, charge, verticalBias));
                playKickHitSound(player, living, full);
                DragonHoming.open(player, living);
            } else {
                double kb = 0.85 * (0.6 + charge);
                double up = 0.18 + charge * 0.15;
                CombatKnockback.add(living, flat.scale(kb).add(0, up, 0));
                playHitSound(player, living, full);
            }
            CombatFx.impact(player.serverLevel(), living, flat,
                    full ? CombatFx.Weight.HEAVY : CombatFx.Weight.LIGHT);
            anyHit = true;
        }

    }

    private static void handleChargeAttack(ServerPlayer player, LivingEntity target, Resources res, StatsData data,
                                           boolean kick, int chargePercent, int verticalBias) {
        float charge = Math.max(0.25f, Math.min(1f, chargePercent / 100f));
        double engageRange = kick ? kickHitRange(charge, verticalBias) : XenoServerConfig.chargeAttackRange;
        if (player.distanceTo(target) > engageRange + 1.5) return;
        float stamCost = kick
                ? XenoServerConfig.kickReleaseStamina(charge, verticalBias)
                : XenoServerConfig.fistReleaseStamina(charge);
        if (!trySpendStamina(res, stamCost)) return;

        boolean full = charge >= 0.95f;
        DmzAnimHelper.ChargeStyle style = kick
                ? DmzAnimHelper.ChargeStyle.KICK
                : (full ? DmzAnimHelper.ChargeStyle.FIST_HEAVY : DmzAnimHelper.ChargeStyle.FIST_LIGHT);
        // Fancy chain anims for kick/punch (tracking clients; attacker predicts locally)
        DmzAnimHelper.playChargeRelease(player, style, full, verticalBias, true);

        // No player lunge — stay put; only target takes KB
        faceBodyToLook(player);

        double hitRange = kick ? engageRange : 5.0;
        if (player.distanceTo(target) > hitRange) return;

        float base = (float) Math.max(2.0, player.getAttackStrengthScale(0.5f) * 5.0f);
        if (data != null) {
            base = (float) Math.max(base, data.getMeleeDamage() * 0.45);
        }
        float mult = XenoServerConfig.chargeDamageScale * (0.55f + 0.7f * charge);
        if (kick) mult *= XenoServerConfig.kickDamageScale;
        target.hurt(player.damageSources().playerAttack(player), base * mult);

        Vec3 kbDir = target.position().subtract(player.position());
        Vec3 kbFlat = new Vec3(kbDir.x, 0, kbDir.z);
        if (kbFlat.lengthSqr() > 1.0e-4) {
            kbFlat = kbFlat.normalize();
            if (kick) {
                CombatKnockback.set(target, kickTargetLaunch(kbFlat, charge, verticalBias));
                if (verticalBias == 0) {
                    startAutomaticBallChase(player, target);
                } else {
                    DragonHoming.open(player, target);
                }
            } else {
                double kb = 0.85 * (0.6 + charge);
                double up = 0.18 + charge * 0.15;
                CombatKnockback.add(target, kbFlat.scale(kb).add(0, up, 0));
            }
        }

        if (kick) {
            playKickHitSound(player, target, full);
        } else {
            playHitSound(player, target, full);
        }
        Vec3 blow = kbFlat.lengthSqr() > 1.0e-4 ? kbFlat : player.getLookAngle();
        CombatFx.impact(player.serverLevel(), target, blow,
                full ? CombatFx.Weight.HEAVY : CombatFx.Weight.LIGHT);
    }

    /** Hit reach for charged kicks; S-hold extends range. */
    private static double kickHitRange(float charge, int verticalBias) {
        double base = Math.max(5.5, XenoServerConfig.chargeAttackRange + 0.75);
        if (verticalBias < 0) {
            base += XenoServerConfig.kickDownRangeBonus * (0.75 + 0.35 * charge);
        }
        return base;
    }

    /**
     * +1 W = launch target upward, -1 S = smash downward, 0 = default arc.
     */
    private static Vec3 kickTargetLaunch(Vec3 awayFlat, float charge, int verticalBias) {
        double horiz = (0.9 + charge) * (verticalBias == 0 ? 1.0 : 0.55)
                * Math.max(0.1, XenoServerConfig.kickKnockbackScale);
        double up;
        if (verticalBias > 0) {
            double scale = Math.max(0.1, XenoServerConfig.kickKnockbackScale);
            double[] d = net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.launcherDelta(
                    awayFlat.x, awayFlat.z,
                    XenoServerConfig.comboLauncherHoriz * scale,
                    XenoServerConfig.comboLauncherUp);
            return new Vec3(d[0], d[1], d[2]);
        } else if (verticalBias < 0) {
            up = -XenoServerConfig.kickDownLaunch * (0.7 + charge * 0.8);
            horiz *= 0.65;
        } else {
            return ballArcLaunch(awayFlat, charge);
        }
        return awayFlat.normalize().scale(horiz).add(0, up, 0);
    }

    /** Ballistic hang-time arc for kicks (mash + charged). */
    private static Vec3 ballArcLaunch(Vec3 awayFlat, float charge) {
        float c = Math.max(0.25f, Math.min(1.0f, charge));
        double scale = Math.max(0.1, XenoServerConfig.kickKnockbackScale);
        double horiz = Math.max(0.7, XenoServerConfig.comboLauncherHoriz * 1.5) * scale * (0.75 + 0.4 * c);
        double up = Math.max(1.5, XenoServerConfig.comboLauncherUp * 0.95) * (0.8 + 0.35 * c);
        double[] d = net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.launcherDelta(
                awayFlat.x, awayFlat.z, horiz, up);
        return new Vec3(d[0], d[1], d[2]);
    }

    private static double kickVerticalImpulse(ServerPlayer player, float charge, int verticalBias, boolean aerialSelf) {
        if (verticalBias > 0) {
            return (aerialSelf ? 0.55 : 0.35) + charge * XenoServerConfig.kickUpLaunch * 0.45;
        }
        if (verticalBias < 0) {
            return aerialSelf ? -0.15 - charge * 0.25 : -0.05;
        }
        if (aerialSelf) {
            return player.onGround() ? 0.18 + charge * 0.12 : 0.28 + charge * 0.35;
        }
        return 0.12;
    }

    /**
     * Dragon dash: heavy hit launches the target, then the attacker teleports/chases after them.
     */
    private static void handleDragonDash(ServerPlayer player, LivingEntity target, Resources res, StatsData data,
                                         int chargePercent) {
        if (player.distanceTo(target) > XenoServerConfig.dragonDashRange) return;
        float charge = Math.max(0.35f, Math.min(1f, chargePercent / 100f));
        if (!trySpendStamina(res, XenoServerConfig.dragonDashStaminaCost * (0.5f + 0.5f * charge))) return;
        if (!trySpendKi(res, XenoServerConfig.dragonDashKiCost * (0.5f + 0.5f * charge))) return;

        DmzAnimHelper.playChargeRelease(player, DmzAnimHelper.ChargeStyle.DRAGON, charge >= 0.95f);

        Vec3 from = player.position();
        playItSound(player, from.x, from.y, from.z, true);

        // Snap in front of target first
        Vec3 land = chaseLanding(player, target);
        land = findOpenSpot(player, target, land);
        teleport(player, land);
        faceTarget(player, target);

        float base = (float) Math.max(3.0, player.getAttackStrengthScale(0.5f) * 6.0f);
        if (data != null) {
            base = (float) Math.max(base, data.getMeleeDamage() * 0.55);
        }
        float mult = XenoServerConfig.chargeDamageScale * 1.25f * (0.7f + 0.5f * charge);
        target.hurt(player.damageSources().playerAttack(player), base * mult);

        // Launch target away / upward (Sparking Zero style)
        Vec3 away = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
        if (away.lengthSqr() < 1.0e-4) {
            away = player.getLookAngle().multiply(1, 0, 1);
        }
        away = away.normalize();
        double launch = DRAGON_LAUNCH * (0.75 + 0.5 * charge);
        double up = 0.85 + charge * 0.55;
        CombatKnockback.set(target, away.scale(launch * 0.22).add(0, up, 0));
        playHitSound(player, target, true);

        // Chase phase is probabilistic (default 50%)
        if (!rollChaseSuccess(player)) {
            faceTarget(player, target);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Dragon chase failed"), true);
            playItSound(player, player.getX(), player.getY(), player.getZ(), false);
            return;
        }

        // Chase: appear near where they were launched
        Vec3 chasePos = new Vec3(
                target.getX() + away.x * (launch * 0.55),
                target.getY() + Math.min(2.8, up * 1.6),
                target.getZ() + away.z * (launch * 0.55));
        if (!isSpotOpen(player, chasePos)) {
            chasePos = new Vec3(target.getX() + away.x * 2.2, target.getY() + 0.5, target.getZ() + away.z * 2.2);
        }
        teleport(player, chasePos);
        player.setDeltaMovement(away.scale(0.65).add(0, 0.12, 0));
        player.hurtMarked = true;
        player.hasImpulse = true;
        faceTarget(player, target);
        playItSound(player, chasePos.x, chasePos.y, chasePos.z, false);
    }

    /** Returns true if chase succeeds based on {@link XenoServerConfig#chaseSuccessChance}. */
    private static boolean rollChaseSuccess(ServerPlayer player) {
        return rollChaseSuccess(XenoServerConfig.chaseSuccessChance, () -> player.getRandom().nextFloat());
    }

    static boolean rollChaseSuccess(float chance, java.util.function.DoubleSupplier roll) {
        if (chance >= 1f) return true;
        if (!Float.isFinite(chance) || chance <= 0f) return false;
        return roll.getAsDouble() < chance;
    }

    private static void handleSuperCounter(ServerPlayer player, LivingEntity target, Resources res, StatsData data,
                                           int side) {
        if (player.distanceTo(target) > XenoServerConfig.vanishMaxRange + 2.0) return;
        if (!trySpendKi(res, XenoServerConfig.superCounterKiCost)) return;

        net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.setGuarding(player, false);

        Vec3 from = player.position();
        playItSound(player, from.x, from.y, from.z, true);
        int s = side < 0 ? -1 : (side > 0 ? 1 : 0);
        Vec3 dest = vanishLanding(player, target, s);
        teleportFacing(player, dest, target);
        playItSound(player, dest.x, dest.y, dest.z, false);
        // A super counter is a vanish, so it leaves the same shade behind.
net.bullettrain.xenopixelsmod.combat.VanishShadeFx.spawn(player, from);
        CombatFx.cue(player.serverLevel(), from, CombatFxKind.COUNTER_FLASH, 1.0f);

        float base = (float) Math.max(2.0, player.getAttackStrengthScale(0.5f) * 5.0f);
        if (data != null) {
            base = (float) Math.max(base, data.getMeleeDamage() * 0.5);
        }
        float dmg = base * XenoServerConfig.superCounterDamageScale;
        target.hurt(player.damageSources().playerAttack(player), dmg);
        Vec3 away = target.position().subtract(player.position());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        if (flat.lengthSqr() > 1.0e-4) {
            CombatKnockback.set(target, flat.normalize().scale(0.9).add(0, 0.35, 0));
        }
        playHitSound(player, target, true);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§bSUPER COUNTER"), true);
    }

    private static void handleKiBlastCancel(ServerPlayer player, LivingEntity target, Resources res, StatsData data) {
        if (!trySpendKi(res, XenoServerConfig.kiBlastCancelKiCost)) return;

        Vec3 look = player.getLookAngle();
        float base = 3.0f;
        if (data != null) {
            try {
                base = (float) Math.max(base, data.getKiDamage() * 0.25);
            } catch (Throwable t) {
                base = (float) Math.max(base, data.getMeleeDamage() * 0.3);
            }
        }
        float dmg = base * XenoServerConfig.kiBlastCancelDamageScale;

        // Cone hit in front; prefers lock-on target if in cone
        double range = 7.0;
        LivingEntity hit = null;
        if (target != null && target.isAlive() && player.distanceTo(target) <= range) {
            Vec3 to = target.getEyePosition().subtract(player.getEyePosition()).normalize();
            if (look.dot(to) > 0.55) hit = target;
        }
        if (hit == null) {
            for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.2),
                    ent -> ent != player && ent.isAlive())) {
                Vec3 to = e.getEyePosition().subtract(player.getEyePosition());
                if (to.lengthSqr() > range * range) continue;
                if (look.dot(to.normalize()) < 0.6) continue;
                hit = e;
                break;
            }
        }
        if (hit != null) {
            hit.hurt(player.damageSources().playerAttack(player), dmg);
            CombatKnockback.add(hit, look.scale(0.45).add(0, 0.12, 0));
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, 1.35f);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            Vec3 tip = player.getEyePosition().add(look.scale(1.2));
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    tip.x, tip.y, tip.z, 10, 0.15, 0.15, 0.15, 0.02);
        }
    }

    private static void handleRushChain(ServerPlayer player, LivingEntity target, Resources res, StatsData data) {
        if (player.distanceTo(target) > XenoServerConfig.rushChainRange) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Too far for rush — chase instead"), true);
            return;
        }
        boolean launched = !target.onGround()
                || target.getDeltaMovement().y > 0.08
                || target.getDeltaMovement().horizontalDistanceSqr() > 0.0064
                || target.getY() - player.getY() > 0.8
                // An airborne attacker may enter a rush from above as a downward dive, even
                // when the victim is standing on the ground.
                || (!player.onGround() && player.getY() - target.getY() > 1.5);
        if (!launched) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Rush: knock them first"), true);
            return;
        }
        if (!trySpendKi(res, XenoServerConfig.rushChainKiCost)) return;

        int rushStep = net.bullettrain.xenopixelsmod.combat.Bt3CombatLimiter.nextRushStep(player);
        net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent rushIntent =
                net.bullettrain.xenopixelsmod.combat.technique.XenoRushTechniques
                        .animationForRushStep(rushStep);
        net.bullettrain.xenopixelsmod.network.ModNetwork.sendToTrackingAndSelf(player,
                new net.bullettrain.xenopixelsmod.network.packet.Bt3AnimIntentPacket(
                        player.getId(), rushIntent));
        Vec3 from = player.position();
        playItSound(player, from.x, from.y, from.z, true);
        Vec3 land = vanishLanding(player, target, 0).add(target.getDeltaMovement().scale(0.35));
        teleportFacing(player, land, target);
        playItSound(player, land.x, land.y, land.z, false);
        DmzAnimHelper.broadcastDash(player, 0);

        if (player.distanceTo(target) <= 5.5) {
            float base = (float) Math.max(2.0, player.getAttackStrengthScale(0.5f) * 4.5f);
            if (data != null) base = (float) Math.max(base, data.getMeleeDamage() * 0.42);
            float mult = XenoServerConfig.rushChainDamageScale
                    * net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem.damageMult(player);
            target.hurt(player.damageSources().playerAttack(player), base * mult);
            boolean breaker = rushStep % 4 == 3;
            boolean finisher = rushStep % 4 == 0;
            CombatKnockback.add(target,
                    new Vec3(0, breaker ? 0.85 : (finisher ? 0.55 : 0.15), 0));
            if (finisher) {
                Vec3 away = target.position().subtract(player.position());
                Vec3 flat = new Vec3(away.x, 0, away.z);
                if (flat.lengthSqr() > 1.0e-4) {
                    CombatKnockback.add(target, flat.normalize().scale(1.35));
                }
            }
            playHitSound(player, target, false);
            CombatFx.impact(player.serverLevel(), target,
                    target.position().subtract(player.position()), CombatFx.Weight.LIGHT);
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§dRUSH CHAIN"), true);
    }


    /**
     * Opens a Zanzoken read. The ki is spent on the press, not on the dodge — reading the swing
     * wrong is supposed to cost something, or the move is just a better block.
     */
    public static void handleZanzoken(ServerPlayer player) {
        // Three different refusals used to look like one message or like nothing at all, which is
        // what made a stuck cooldown so hard to tell apart from a technique that was not wired up.
        if (!net.bullettrain.xenopixelsmod.features.progression.CombatSkills.zanzokenUnlocked(player)) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§7Zanzoken: unlock it in the skill tree first (/xenoskills)"), true);
            return;
        }
        if (!XenoServerConfig.zanzokenEnabled) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Zanzoken is disabled here"), true);
            return;
        }
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        Resources res = data != null ? data.getResources() : null;
        if (res == null || res.getCurrentEnergy() < XenoServerConfig.zanzokenKiCost) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Zanzoken: not enough KI"), true);
            return;
        }
        if (!net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.openDodgeWindow(player)) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Zanzoken: cooling down"), true);
            return;
        }
        trySpendKi(res, XenoServerConfig.zanzokenKiCost);
        if (!XenoServerConfig.zanzokenRequireTiming) {
            LivingEntity center = net.bullettrain.xenopixelsmod.combat.clone.XenoCloneSystem
                    .lockedTarget(player);
            performZanzoken(player, center != null ? center : player);
            return;
        }
        // The images are now standing in for this fighter: onlookers cannot pick the real body out,
        // and AI cannot acquire them until the images fade (ZanzokenConfusion).
        net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.markAfterimages(
                player, XenoServerConfig.zanzokenAfterimageTicks);
        playZanzoken(player, player.position());
        // Without this a press that simply had nothing to dodge is indistinguishable from a
        // technique that is not working at all.
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§bZanzoken: reading…"), true);
    }

    /**
     * A read that landed: leave the image where the body was, then reappear behind the attacker.
     *
     * <p>Called from the damage hook rather than the packet handler, because the technique only
     * resolves at the moment something actually swings.
     */
    public static void performZanzoken(ServerPlayer player, LivingEntity attacker) {
        if (player == null || attacker == null) return;
        Vec3 from = player.position();

        net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem.grantIFrames(
                player, XenoServerConfig.zanzokenIFramesTicks);

        // The read is not "he stepped aside" — it is that he is suddenly everywhere you could
        // turn. A closed ring of bodies around the attacker, each looking inward at them.
        //
        // The dodger stands in that ring. Teleporting them somewhere else, as this used to, left
        // the one body that had moved plainly identifiable and made the images decoration.
        Vec3 dest;
        if (XenoServerConfig.zanzokenGhostAfterimage) {
            int slots = Math.max(2, XenoServerConfig.zanzokenRingClones + 1);
            int mine = player.getRandom().nextInt(slots);
            double radius = XenoServerConfig.zanzokenRingRadius;

            java.util.List<net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity> images =
                    net.bullettrain.xenopixelsmod.combat.clone.XenoCloneSystem.encircle(
                            player, attacker, slots, radius,
                            Math.max(20, XenoServerConfig.zanzokenRingTicks), mine);

            dest = openRingLanding(player, attacker, mine, slots, radius);
            // The images stand for as long as the ring does, and everything that reads "are this
            // fighter's images up?" has to agree with what is actually on screen.
            //
            // It did not. The press marked them for zanzokenAfterimageTicks and nothing re-marked
            // them here, so by the time the ring existed — the whole point of the technique — the
            // mark had usually expired and AI went on tracking the real body through it.
            net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.markAfterimages(player,
                    net.bullettrain.xenopixelsmod.combat.ZanzokenWindow.afterimageTicks(
                            XenoServerConfig.zanzokenAfterimageTicks,
                            XenoServerConfig.zanzokenRingTicks));
            redirectLockToImage(player, attacker, images);
            net.bullettrain.xenopixelsmod.combat.ZanzokenConfusion.scatter(player, images);
        } else {
            net.bullettrain.xenopixelsmod.combat.VanishShadeFx.spawn(player, from);
            dest = vanishLanding(player, attacker, 0);
        }

        teleportFacing(player, dest, attacker);
        playZanzoken(player, dest);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§b§lZANZOKEN!"), true);
        try {
            net.bullettrain.xenopixelsmod.combat.AfterimageFx.spawnTrailServer(player, from, dest, 6);
        } catch (Throwable ignored) {
        }
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            net.bullettrain.xenopixelsmod.combat.fx.CombatFx.cue(level, from,
                    net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind.VANISH_CLAP, 1.0f);
        }
    }

    /**
     * Points the attacker's lock-on at one of the images.
     *
     * <p>DragonMineZ's lock marker follows the real entity, so without this it names the dodger
     * outright and the ring is decoration. Redirecting rather than clearing matters: a lock that
     * simply drops announces the dodge, while a lock resting on an image actively misleads, and it
     * breaks on its own when that image is struck.
     *
     * <p>Only touched when the attacker actually had the dodger locked. A lock aimed at someone
     * else is none of this technique's business.
     */
    private static void redirectLockToImage(
            ServerPlayer player, LivingEntity attacker,
            java.util.List<net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity> images) {
        if (images == null || images.isEmpty()) return;
        // Everyone locked onto the dodger, not only whoever happened to swing. A second hunter
        // standing off to the side kept a lock naming the real body, and one marker on the correct
        // target tells the whole room which one it is.
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        for (ServerPlayer hunter : level.players()) {
            if (hunter == player) continue;
            redirectOneLock(player, hunter, images);
        }
        if (attacker instanceof ServerPlayer swinger && !level.players().contains(swinger)) {
            redirectOneLock(player, swinger, images);
        }
    }

    private static void redirectOneLock(
            ServerPlayer player, ServerPlayer hunter,
            java.util.List<net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity> images) {
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, hunter).orElse(null);
            if (data == null || data.getTechniques() == null) return;
            if (data.getTechniques().getHomingTargetId() != player.getId()) return;
            data.getTechniques().setHomingTargetId(
                    images.get(player.getRandom().nextInt(images.size())).getId());
        } catch (Throwable ignored) {
            // A lock that cannot be moved only costs the disguise, never the dodge.
        }
    }

    /** DragonMineZ ships the afterimage sound; fall back to the generic evade cue if it is gone. */
    private static void playZanzoken(ServerPlayer player, Vec3 at) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(
                ResourceLocation.fromNamespaceAndPath("dragonminez", "zanzoken"));
        if (sound == null) {
            playItSound(player, at.x, at.y, at.z, true);
            return;
        }
        player.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * Shi Shin No Ken. Toggles the split, charging ki only when dividing — reuniting is free,
     * because a fighter should never be stranded in four weak bodies by an empty bar.
     */
    public static void handleMultiForm(ServerPlayer player) {
        if (!net.bullettrain.xenopixelsmod.features.progression.CombatSkills.multiFormUnlocked(player)) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§7Shi Shin No Ken: unlock it in the skill tree first (/xenoskills)"), true);
            return;
        }
        if (!XenoServerConfig.multiFormEnabled) return;
        if (net.bullettrain.xenopixelsmod.combat.clone.XenoCloneSystem.isSplit(player)) {
            net.bullettrain.xenopixelsmod.combat.clone.XenoCloneSystem.reunite(player);
            return;
        }
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        Resources res = data != null ? data.getResources() : null;
        if (res == null || res.getCurrentEnergy() < XenoServerConfig.multiFormKiCost) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Not enough KI to divide"), true);
            return;
        }
        if (!net.bullettrain.xenopixelsmod.combat.clone.XenoCloneSystem.toggleSplit(player)) return;
        trySpendKi(res, XenoServerConfig.multiFormKiCost);
        playZanzoken(player, player.position());
    }

    private static void handleSonicSway(ServerPlayer player, Resources res, int side) {
        if (!trySpendStamina(res, XenoServerConfig.sonicSwayStaminaCost)) return;
        int s = side < 0 ? -1 : 1;
        float yaw = player.getYRot() * ((float) Math.PI / 180F);
        // Strafe left/right relative to look
        Vec3 right = new Vec3(-Math.sin(yaw + Math.PI / 2), 0, Math.cos(yaw + Math.PI / 2));
        if (s < 0) right = right.scale(-1);
        Vec3 dest = player.position().add(right.scale(2.4)).add(0, 0.05, 0);
        playItSound(player, player.getX(), player.getY(), player.getZ(), true);
        player.connection.teleport(dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(right.scale(0.35));
        player.hurtMarked = true;
        player.hasImpulse = true;
        playItSound(player, dest.x, dest.y, dest.z, false);
        net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem.grantIFrames(
                player, XenoServerConfig.sonicSwayIFramesTicks);
        DmzAnimHelper.broadcastDash(player, s < 0 ? 1 : 2);
        try {
            Vec3 from = player.position().subtract(right.scale(2.4));
            net.bullettrain.xenopixelsmod.combat.AfterimageFx.spawnTrailServer(player, from, dest, 5);
        } catch (Throwable ignored) {
        }
    }

    private static void handleUltimate(ServerPlayer player, LivingEntity target, Resources res, StatsData data) {
        if (!trySpendKi(res, XenoServerConfig.ultimateKiCost)) return;
        DmzAnimHelper.broadcastChargeStart(player, DmzAnimHelper.ChargeStyle.DRAGON);
        DmzAnimHelper.playChargeRelease(player, DmzAnimHelper.ChargeStyle.DRAGON, true, 0, true);

        float base = 8.0f;
        if (data != null) {
            try {
                base = (float) Math.max(base, data.getKiDamage() * 0.55);
            } catch (Throwable t) {
                base = (float) Math.max(base, data.getMeleeDamage() * 0.7);
            }
        }
        float mult = XenoServerConfig.ultimateDamageScale
                * net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem.damageMult(player)
                * net.bullettrain.xenopixelsmod.features.progression.CombatSkills.ultimateMult(player)
                * net.bullettrain.xenopixelsmod.features.progression.SuperSoulCatalog.ultMult(player);

        if (target != null && target.isAlive() && player.distanceTo(target) <= 10.0) {
            target.hurt(player.damageSources().playerAttack(player), base * mult);
            Vec3 away = target.position().subtract(player.position());
            Vec3 flat = new Vec3(away.x, 0, away.z);
            if (flat.lengthSqr() > 1.0e-4) {
                CombatKnockback.set(target, flat.normalize().scale(1.8).add(0, 0.7, 0));
            }
            playHitSound(player, target, true);
        } else {
            // Cone blast
            Vec3 look = player.getLookAngle();
            for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().expandTowards(look.scale(9)).inflate(2.0),
                    ent -> ent != player && ent.isAlive()
                            && !net.bullettrain.xenopixelsmod.event.DmzMasterProtection.isDmzMaster(ent))) {
                Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(player.getEyePosition());
                if (to.lengthSqr() > 81) continue;
                if (look.dot(to.normalize()) < 0.55) continue;
                e.hurt(player.damageSources().playerAttack(player), base * mult * 0.85f);
            }
        }
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                    player.getX(), player.getY() + 1, player.getZ(), 3, 0.5, 0.5, 0.5, 0.01);
            // Gold ring along the firing direction. The only ULTIMATE-weight call in the mod;
            // it is the ceiling the other tiers are read against.
            CombatFx.impact(sl, player.position().add(0.0, player.getBbHeight() * 0.6, 0.0),
                    player.getLookAngle(), CombatFx.Weight.ULTIMATE);
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c§lULTIMATE!"), true);
    }

    private static void handleZBurst(ServerPlayer player, LivingEntity target, Resources res, StatsData data) {
        if (player.distanceTo(target) > XenoServerConfig.zBurstRange) return;
        if (!trySpendKi(res, XenoServerConfig.zBurstKiCost)) return;

        Vec3 from = player.position();
        playItSound(player, from.x, from.y, from.z, true);
        Vec3 land = chaseLanding(player, target);
        teleportFacing(player, land, target);
        playItSound(player, land.x, land.y, land.z, false);

        if (player.distanceTo(target) <= 4.8) {
            float base = (float) Math.max(1.5, player.getAttackStrengthScale(0.5f) * 4.0f);
            if (data != null) {
                base = (float) Math.max(base, data.getMeleeDamage() * 0.4);
            }
            target.hurt(player.damageSources().playerAttack(player),
                    base * XenoServerConfig.zBurstDamageScale);
            playHitSound(player, target, false);
        }
    }

    private static void handleCombo(ServerPlayer player, LivingEntity target, Resources res, StatsData data,
                                    int verticalBias, int mashStyle) {
        // Deflection first, and it consumes the swing. Checked here rather than on a key of its
        // own because this runs on every attack press including a whiff, which is exactly when a
        // player is punching at an incoming blast rather than at a body.
        if (net.bullettrain.xenopixelsmod.combat.KiDeflect.tryDeflect(player, res)) return;

        // Server-side combo step: the string is earned on the server (advance per hit, decay on
        // a gap), not taken from the client where a forged step could force the finisher every
        // swing. maxComboSteps = finisher every N hits.
        net.bullettrain.xenopixelsmod.combat.Bt3CinematicRushSystem.clearFollowup(player);
        int step = net.bullettrain.xenopixelsmod.combat.Bt3CombatLimiter.nextComboStep(player, target);
        int finisherEvery = Math.max(1, XenoServerConfig.maxComboSteps);
        int scaleStep = Math.min(step, finisherEvery * 4);
        boolean launcher = verticalBias > 0;
        // Precedence: an explicit W tap beats a held direction, which beats the authored route.
        // The client ran this same chain to predict the beat, so the two agree and the
        // confirmation broadcast below is a no-op on the attacker's own screen.
        net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent styled = launcher
                ? null
                : net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.styled(mashStyle, step);
        net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent intent;
        if (styled != null) {
            intent = styled;
        } else {
            net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent authored =
                    net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.resolve(
                            net.bullettrain.xenopixelsmod.combat.Bt3CombatLimiter.comboRoute(player),
                            step,
                            false,
                            net.bullettrain.xenopixelsmod.combat.Bt3CombatLimiter.comboAirborne(player));
            intent = net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.overlay(
                    authored, launcher ? 1 : 0);
        }
        net.bullettrain.xenopixelsmod.combat.Bt3ComboBeat beat =
                net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.beat(
                        intent, step, mashStyle, verticalBias);
        boolean finisher = XenoServerConfig.bt3FinisherEnabled && beat.finisher();

        // BT3 earns its signature moves out of a string rather than giving them buttons. The rule
        // is pure and lives in Bt3ComboTerminator so the client predicts the same beat; the gates
        // and the spending stay here.
        boolean inMeleeRange = target != null
                && player.distanceTo(target) <= XenoServerConfig.chargeAttackRange + 1.5;
        switch (net.bullettrain.xenopixelsmod.combat.Bt3ComboTerminator.resolve(
                finisher, launcher, target != null && target.isAlive(), inMeleeRange)) {
            case Z_BURST -> {
                if (XenoServerConfig.bt3ZBurstEnabled) {
                    handleZBurst(player, target, res, data);
                    return;
                }
            }
            case ULTIMATE -> {
                // canUltimate stamps its own cooldown, so it is asked last, only once the beat has
                // really resolved to an Ultimate. A failed check falls through to the ordinary
                // finisher rather than eating the swing.
                if (XenoServerConfig.bt3UltimateEnabled
                        && net.bullettrain.xenopixelsmod.combat.Bt3CombatLimiter.canUltimate(player)) {
                    handleUltimate(player, target, res, data);
                    return;
                }
            }
            default -> {
            }
        }
        float cost = finisher ? XenoServerConfig.finisherKiCost : XenoServerConfig.comboKiCost;
        // Hard gate: a swing you cannot pay for does not land. Previously this was a soft spend
        // that let a low-ki finisher hit for free; the combo step is server-side now, so this is
        // safe and no longer enables free finishers.
        if (!trySpendKi(res, cost)) return;

        net.bullettrain.xenopixelsmod.network.ModNetwork.sendToTrackingAndSelf(player,
                new net.bullettrain.xenopixelsmod.network.packet.Bt3AnimIntentPacket(
                        player.getId(), intent));

        // Air string / freelook miss: counter and pose still advance, no hit
        if (target == null || !target.isAlive()) {
            net.bullettrain.xenopixelsmod.combat.Bt3CombatLimiter.resetCombo(player);
            return;
        }

        faceBodyToLook(player);
        if (player.distanceTo(target) <= 4.5) {
            float base = (float) Math.max(1.0, player.getAttackStrengthScale(0.5f) * 4.0f);
            float mult = (1.0f + scaleStep * 0.12f) * XenoServerConfig.comboDamageScale;
            if (finisher) mult *= XenoServerConfig.finisherDamageScale;
            if (launcher) mult *= XenoServerConfig.kickDamageScale;
            if (data != null) {
                base = (float) Math.max(base, data.getMeleeDamage() * 0.35);
            }
            boolean landed = target.hurt(player.damageSources().playerAttack(player), base * mult);
            if (landed) {
                net.bullettrain.xenopixelsmod.combat.Bt3CinematicRushSystem.armAfterHit(player, target, step);
            }

            Vec3 kbDir = target.position().subtract(player.position());
            Vec3 kbFlat = new Vec3(kbDir.x, 0, kbDir.z);
            if (kbFlat.lengthSqr() < 1.0e-4) {
                Vec3 look = player.getLookAngle();
                kbFlat = new Vec3(look.x, 0, look.z);
            }
            if (kbFlat.lengthSqr() > 1.0e-4) {
                kbFlat = kbFlat.normalize();
                if (beat.launcher()) {
                    CombatKnockback.set(target, ballArcLaunch(kbFlat, 1.0f));
                    startAutomaticBallChase(player, target);
                } else if (intent.isKick()) {
                    CombatKnockback.set(target, ballArcLaunch(kbFlat, 0.7f));
                    if (beat.chase()) {
                        startAutomaticBallChase(player, target);
                    }
                } else {
                    // Ordinary punches are contact hits, not launchers. Keep the heavy finisher's
                    // authored impact, but prevent the regular jab/cross string from sliding the
                    // victim away on every beat.
                    if (finisher) {
                        CombatKnockback.add(target, kbFlat.scale(1.65).add(0, 0.55, 0));
                    }
                }
                if (beat.guardBreak()) {
                    CombatKnockback.add(target, kbFlat.scale(0.35).add(0, 0.18, 0));
                }
            }

            if (launcher) {
                playKickHitSound(player, target, true);
            } else {
                playHitSound(player, target, finisher);
            }
            Vec3 blow = kbFlat.lengthSqr() > 1.0e-4 ? kbFlat : player.getLookAngle();
            CombatFx.impact(player.serverLevel(), target, blow,
                    launcher || finisher ? CombatFx.Weight.HEAVY : CombatFx.Weight.LIGHT);
            if (beat.finisher()) {
                    net.bullettrain.xenopixelsmod.combat.Bt3CombatLimiter.resetCombo(player);
            }
        }
    }

    private static void startAutomaticBallChase(ServerPlayer player, LivingEntity target) {
        DragonHoming.close(player);
        ChaseFlightSystem.startAutomatic(player, target);
    }

    /**
     * @return false when the player cannot pay. A missing DMZ {@link Resources} capability means
     * "cannot act", not "free" — the old {@code res == null → true} gave every move away to a
     * player mid-join data race (or a forged state) while they still dealt fallback damage.
     * Zero-cost actions are always allowed regardless.
     */
    /**
     * Begins a Hakai channel, resolving the target and refusing with a reason when it cannot.
     *
     * <p>Shared by the {@code HAKAI_START} packet and by a cast from a DragonMineZ technique slot,
     * so both routes get identical targeting, range and enable checks. The channel completes on
     * its own timer, so a slot cast needs no release to finish it; only the held key sends a
     * {@code HAKAI_CANCEL} to abort early.
     *
     * @param target the caster's locked target, or {@code null} to search where they are looking
     */
    public static void startHakai(ServerPlayer player, LivingEntity target) {
        if (!net.bullettrain.xenopixelsmod.features.progression.CombatSkills.hakaiUnlocked(player)) {
            hakaiFail(player, "Hakai: unlock it in the skill tree first (/xenoskills)");
            return;
        }
        if (net.bullettrain.xenopixelsmod.combat.HakaiChannelSystem.isChanneling(player)) {
            return;
        }
        if (target == null) {
            target = net.bullettrain.xenopixelsmod.combat.HakaiChannelSystem.findLookTarget(
                    player, XenoServerConfig.hakaiMaxRange);
        }
        if (target == null) {
            hakaiFail(player, "Hakai: look at a living entity (or lock on)");
            return;
        }
        if (!XenoServerConfig.hakaiEnabled) {
            hakaiFail(player, "Hakai is disabled");
            return;
        }
        if (player.distanceTo(target) > XenoServerConfig.hakaiMaxRange) {
            hakaiFail(player, "Hakai: too far");
            return;
        }
        net.bullettrain.xenopixelsmod.combat.HakaiChannelSystem.start(player, target, true);
    }

    private static void hakaiFail(ServerPlayer player, String reason) {
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c" + reason), true);
    }

    private static boolean trySpendKi(Resources res, float cost) {
        if (cost <= 0f) return true;
        if (res == null) return false;
        if (res.getCurrentEnergy() < cost) return false;
        res.removeEnergy(cost);
        return true;
    }

    private static boolean trySpendStamina(Resources res, float cost) {
        if (cost <= 0f) return true;
        if (res == null) return false;
        if (res.getCurrentStamina() < cost) return false;
        res.removeStamina(cost);
        return true;
    }

    private static void teleport(ServerPlayer player, Vec3 dest) {
        player.teleportTo(dest.x, dest.y, dest.z);
        player.fallDistance = 0f;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.connection.resetPosition();
    }

    /**
     * Teleport and face the target in one packet — zeros velocity so DMZ flight
     * cannot carry residual speed after the warp.
     */
    static void teleportFacing(ServerPlayer player, Vec3 dest, LivingEntity target) {
        double dx = target.getX() - dest.x;
        double dz = target.getZ() - dest.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = player.getXRot();
        player.connection.teleport(dest.x, dest.y, dest.z, yaw, pitch);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.fallDistance = 0f;
    }

    /** Position + look teleport without {@code hurtMarked} (chase flight; hurtMarked cranks the camera). */
    static void teleportPos(ServerPlayer player, Vec3 dest, float yaw, float pitch) {
        player.connection.teleport(dest.x, dest.y, dest.z, yaw, pitch);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.setXRot(pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = true;
        player.fallDistance = 0f;
    }

    /**
     * BT3 vanish: land just past the target (their back relative to you),
     * offset left (A) or right (D). Always at the target's height so you don't
     * stay sky-high and "fly away".
     *
     * @param side -1 left, +1 right, 0 center-behind
     */
    public static Vec3 vanishBehind(Entity player, LivingEntity target) {
        return vanishBehind(player, target, 0);
    }

    public static Vec3 vanishBehind(Entity player, LivingEntity target, int side) {
        return vanishPoint(
                player.getX(), player.getZ(),
                target.getX(), target.getY(), target.getZ(),
                target.yBodyRot, side,
                Math.max(0.0, XenoServerConfig.vanishGap),
                Math.max(0.0, XenoServerConfig.vanishSide),
                Math.max(0.0, XenoServerConfig.vanishNearField));
    }

    /**
     * The vanish landing point, as pure geometry.
     *
     * <p>At range the landing direction is the line you came in on, so you end up past the target
     * on the far side from where you stood — the original behaviour, reproduced exactly once
     * {@code sep >= nearField}.
     *
     * <p>Up close that line stops meaning anything. It used to be trusted down to a hundredth of a
     * block, and a vector that short is noise: it flips between frames, and the client — which
     * predicts this same landing from positions roughly a hundred milliseconds behind the
     * server's — could compute a direction opposite to the one the server picked and get yanked
     * across the target. Hovering directly above or below someone made it worse still, because the
     * horizontal separation there is near zero while the fight is very much close range. So the
     * closer you are, the more the direction comes from the target's own body facing, which is
     * both the spot a vanish is supposed to land on and a value that is synchronised between
     * client and server in a way a sub-block position delta is not. The two blend, so there is no
     * snap as you cross the boundary, and {@code nearField} of zero disables the near-field
     * behaviour entirely.
     *
     * @param targetBodyYawDeg the target's {@code yBodyRot}
     * @param side             -1 left, +1 right, 0 centre-behind
     */
    public static Vec3 vanishPoint(double px, double pz, double tx, double ty, double tz,
                                   float targetBodyYawDeg, int side,
                                   double gap, double sideOff, double nearField) {
        double yaw = Math.toRadians(targetBodyYawDeg);
        // Minecraft yaw: facing = (-sin, cos), so the target's back is the negation of that.
        double backX = Math.sin(yaw);
        double backZ = -Math.cos(yaw);

        double ax = tx - px;
        double az = tz - pz;
        double sep = Math.sqrt(ax * ax + az * az);

        double dirX;
        double dirZ;
        if (sep < 1.0e-6) {
            dirX = backX;
            dirZ = backZ;
        } else {
            double t = nearField <= 0.0 ? 1.0 : Math.min(1.0, sep / nearField);
            double bx = ax / sep * t + backX * (1.0 - t);
            double bz = az / sep * t + backZ * (1.0 - t);
            double len = Math.sqrt(bx * bx + bz * bz);
            if (len < 1.0e-6) {
                // Approach and back point opposite ways and cancelled: you are already standing at
                // their back while they face you, and their back is the answer anyway.
                dirX = backX;
                dirZ = backZ;
            } else {
                dirX = bx / len;
                dirZ = bz / len;
            }
        }

        double rightX = -dirZ;
        double rightZ = dirX;
        double s = side < 0 ? -sideOff : (side > 0 ? sideOff : 0.0);
        return new Vec3(
                tx + dirX * gap + rightX * s,
                ty,
                tz + dirZ * gap + rightZ * s);
    }

    /**
     * {@link #vanishBehind} moved off anything solid.
     *
     * <p>Nothing on the vanish path used to check this, so a vanish aimed into terrain teleported
     * the fighter into the terrain. The candidates are a fixed list rather than a search, because
     * the attacking client runs this too in order to predict its own landing and any difference in
     * the order the two sides try spots shows up as a rubber-band. A vanish with nowhere to go
     * leaves the fighter standing where they were, which is a wasted move rather than a
     * suffocation.
     */
    public static Vec3 vanishLanding(Entity player, LivingEntity target, int side) {
        Vec3 preferred = vanishBehind(player, target, side);
        if (!XenoServerConfig.vanishOpenSpotSearch || isSpotOpen(player, preferred)) {
            return preferred;
        }
        Vec3 centre = vanishBehind(player, target, 0);
        double dirX = centre.x - target.getX();
        double dirZ = centre.z - target.getZ();
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len > 1.0e-6) {
            double rightX = -dirZ / len;
            double rightZ = dirX / len;
            for (double lateral : new double[]{0.75, -0.75, 1.5, -1.5}) {
                Vec3 candidate = new Vec3(
                        preferred.x + rightX * lateral, preferred.y, preferred.z + rightZ * lateral);
                if (isSpotOpen(player, candidate)) {
                    return candidate;
                }
            }
            for (double shrink : new double[]{0.75, 0.5}) {
                Vec3 candidate = new Vec3(
                        target.getX() + (preferred.x - target.getX()) * shrink,
                        preferred.y,
                        target.getZ() + (preferred.z - target.getZ()) * shrink);
                if (isSpotOpen(player, candidate)) {
                    return candidate;
                }
            }
        }
        if (side != 0) {
            Vec3 mirrored = vanishBehind(player, target, -side);
            if (isSpotOpen(player, mirrored)) {
                return mirrored;
            }
        }
        return player.position();
    }

    private static Vec3 openRingLanding(Entity player, LivingEntity target, int preferred,
                                        int slots, double radius) {
        for (int step = 0; step < slots; step++) {
            int slot = Math.floorMod(preferred + step, slots);
            double[] off = net.bullettrain.xenopixelsmod.combat.clone.CloneFormation
                    .ringOffset(slot, slots, radius);
            Vec3 candidate = new Vec3(target.getX() + off[0], target.getY(), target.getZ() + off[1]);
            if (isSpotOpen(player, candidate)) return candidate;
        }
        return player.position();
    }

    /** Land on the target (or {@link XenoServerConfig#chaseStopGap} short), at their height. */
    public static Vec3 chaseLanding(Entity player, LivingEntity target) {
        Vec3 flat = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
        if (flat.lengthSqr() < 1.0e-4) {
            flat = new Vec3(0, 0, 1);
        } else {
            flat = flat.normalize();
        }
        double gap = Math.max(0.0, XenoServerConfig.chaseStopGap);
        return new Vec3(
                target.getX() - flat.x * gap,
                target.getY(),
                target.getZ() - flat.z * gap);
    }

    /** Step away from target horizontally; keep player Y for air combat. */
    public static Vec3 backstepDest(Entity player, LivingEntity target) {
        Vec3 away = new Vec3(player.getX() - target.getX(), 0, player.getZ() - target.getZ());
        if (away.lengthSqr() < 1.0e-4) {
            float yaw = player.getYRot() * ((float) Math.PI / 180F);
            away = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw));
        }
        away = away.normalize();
        return new Vec3(
                player.getX() + away.x * BACKSTEP_DIST,
                player.getY(),
                player.getZ() + away.z * BACKSTEP_DIST);
    }

    /** Used by dragon dash only — small lateral nudge if blocked. */
    private static Vec3 findOpenSpot(ServerPlayer player, LivingEntity target, Vec3 preferred) {
        if (isSpotOpen(player, preferred)) return preferred;
        Vec3 toTarget = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
        if (toTarget.lengthSqr() < 1.0e-4) toTarget = new Vec3(0, 0, 1);
        Vec3 toward = toTarget.normalize();
        Vec3 right = new Vec3(-toward.z, 0, toward.x);
        for (double s : new double[]{0.6, -0.6, 1.0, -1.0}) {
            Vec3 tryPos = preferred.add(right.scale(s));
            if (isSpotOpen(player, tryPos)) return tryPos;
        }
        return preferred;
    }

    public static boolean isSpotOpen(Entity player, Vec3 pos) {
        if (player == null || pos == null || player.level() == null) {
            return false;
        }
        var box = player.getBoundingBox().move(
                pos.x - player.getX(),
                pos.y - player.getY(),
                pos.z - player.getZ());
        return player.level().noCollision(player, box);
    }

    static void playItSound(ServerPlayer player, double x, double y, double z, boolean leave) {
        // Server-configured override first, so an operator can point vanish at their own sound
        // without a code change. An id that does not resolve falls through to the usual chain
        // rather than silencing the move.
        String configured = leave ? XenoServerConfig.vanishSoundOut : XenoServerConfig.vanishSoundIn;
        if (configured != null && !configured.isBlank()) {
            ResourceLocation id = ResourceLocation.tryParse(configured);
            SoundEvent custom = id == null ? null : BuiltInRegistries.SOUND_EVENT.get(id);
            if (custom != null) {
                player.level().playSound(null, x, y, z, custom, SoundSource.PLAYERS,
                        1.0f, leave ? 1.0f : 1.1f);
                return;
            }
        }
        SoundEvent dmz = BuiltInRegistries.SOUND_EVENT.get(
                ResourceLocation.fromNamespaceAndPath("dragonminez", "tp"));
        if (dmz != null) {
            player.level().playSound(null, x, y, z, dmz, SoundSource.PLAYERS, 0.95f, leave ? 1.05f : 1.15f);
        } else {
            player.level().playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                    0.7f, leave ? 1.35f : 1.55f);
        }
    }

    private static void playHitSound(ServerPlayer player, LivingEntity target, boolean finisher) {
        playDmzConnect(player, target, finisher);
    }

    private static void playKickHitSound(ServerPlayer player, LivingEntity target, boolean fullCharge) {
        playDmzConnect(player, target, fullCharge);
    }

    /** DMZ unarmed pack only — never vanilla strong/crit/sweep. */
    private static void playDmzConnect(ServerPlayer player, LivingEntity target, boolean heavy) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.4;
        double z = target.getZ();
        SoundEvent hit = dmzHitEvent(player, heavy);
        if (hit != null) {
            player.level().playSound(null, x, y, z, hit, SoundSource.PLAYERS, heavy ? 1.15f : 0.95f,
                    heavy ? 0.9f : 1.05f);
        }
        if (heavy) {
            SoundEvent knock = dmzEvent(com.dragonminez.common.init.MainSounds.KNOCKBACK_CHARACTER);
            if (knock != null) {
                player.level().playSound(null, x, y, z, knock, SoundSource.PLAYERS, 1.0f, 0.95f);
            }
        }
    }

    private static SoundEvent dmzHitEvent(ServerPlayer player, boolean heavy) {
        try {
            if (heavy) {
                return ((player.tickCount & 1) == 0)
                        ? com.dragonminez.common.init.MainSounds.CRITICO1.get()
                        : com.dragonminez.common.init.MainSounds.CRITICO2.get();
            }
            return switch (Math.floorMod(player.tickCount, 6)) {
                case 1 -> com.dragonminez.common.init.MainSounds.GOLPE2.get();
                case 2 -> com.dragonminez.common.init.MainSounds.GOLPE3.get();
                case 3 -> com.dragonminez.common.init.MainSounds.GOLPE4.get();
                case 4 -> com.dragonminez.common.init.MainSounds.GOLPE5.get();
                case 5 -> com.dragonminez.common.init.MainSounds.GOLPE6.get();
                default -> com.dragonminez.common.init.MainSounds.GOLPE1.get();
            };
        } catch (Throwable t) {
            return null;
        }
    }

    private static SoundEvent dmzEvent(
            net.neoforged.neoforge.registries.DeferredHolder<SoundEvent, ? extends SoundEvent> holder) {
        try {
            return holder.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void faceTarget(ServerPlayer player, LivingEntity target) {
        float yaw = yawToward(player, target);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
    }

    /** Keep GeckoLib attacks aligned with DMZ's synced camera/crosshair yaw. */
    private static void faceBodyToLook(ServerPlayer player) {
        float yaw = player.getYRot();
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
    }

    private static float yawToward(net.minecraft.world.entity.Entity from, LivingEntity target) {
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        return (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
    }
}
