package net.bullettrain.xenopixelsmod.network;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
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
        SPARKING
    }

    /** Distance past the target (behind them on approach line). */
    public static final double VANISH_GAP = 1.35;
    /** Left/right offset for A vs D vanish. */
    public static final double VANISH_SIDE = 1.05;
    public static final double CHASE_GAP = 1.35;
    public static final double BACKSTEP_DIST = 3.2;
    public static final double DRAGON_LAUNCH = 6.5;

    private final Action action;
    private final int targetId;
    private final int comboStep;
    /** 0..100 charge percent for charge attacks / dragon dash. */
    private final int chargePercent;
    /** Kick vertical bias: +1 hold W (up), -1 hold S (down), 0 neutral. */
    private final int verticalBias;

    public Bt3CombatPacket(Action action, int targetId, int comboStep) {
        this(action, targetId, comboStep, 0, 0);
    }

    public Bt3CombatPacket(Action action, int targetId, int comboStep, int chargePercent) {
        this(action, targetId, comboStep, chargePercent, 0);
    }

    public Bt3CombatPacket(Action action, int targetId, int comboStep, int chargePercent, int verticalBias) {
        this.action = action;
        this.targetId = targetId;
        this.comboStep = comboStep;
        this.chargePercent = Math.max(0, Math.min(100, chargePercent));
        this.verticalBias = verticalBias < 0 ? -1 : (verticalBias > 0 ? 1 : 0);
    }

    public static void encode(Bt3CombatPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeVarInt(msg.targetId);
        buf.writeVarInt(msg.comboStep);
        buf.writeVarInt(msg.chargePercent);
        buf.writeByte(msg.verticalBias);
    }

    public static Bt3CombatPacket decode(FriendlyByteBuf buf) {
        return new Bt3CombatPacket(
                buf.readEnum(Action.class),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readByte());
    }

    public static void handle(Bt3CombatPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!XenoServerConfig.bt3CombatEnabled) return;

            LivingEntity target = null;
            if (msg.targetId > 0) {
                Entity raw = player.level().getEntity(msg.targetId);
                if (raw instanceof LivingEntity living && living.isAlive()) {
                    target = living;
                }
            }

            // Combo / kick / guard / ki-blast may run without a target.
            if (target == null
                    && msg.action != Action.CHARGE_KICK
                    && msg.action != Action.COMBO_HIT
                    && msg.action != Action.GUARD
                    && msg.action != Action.KI_BLAST_CANCEL
                    && msg.action != Action.SONIC_SWAY
                    && msg.action != Action.SPARKING
                    && msg.action != Action.ULTIMATE) {
                return;
            }
            // Never let BT3 tools hit DMZ masters
            if (target != null && net.bullettrain.xenopixelsmod.event.DmzMasterProtection.isDmzMaster(target)
                    && msg.action != Action.GUARD && msg.action != Action.SPARKING) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§7Masters cannot be attacked"), true);
                return;
            }

            LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
            StatsData data = opt.orElse(null);
            Resources res = data != null ? data.getResources() : null;

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
                case BACKSTEP -> {
                    if (!XenoServerConfig.bt3BackstepEnabled || target == null) return;
                    handleBackstep(player, target, res);
                }
                case COMBO_HIT -> {
                    // Freelook or lock-on — target optional for counter advance
                    if (!XenoServerConfig.bt3ComboEnabled) return;
                    if (target != null && player.distanceTo(target) > 48.0) return;
                    handleCombo(player, target, msg.comboStep, res, data);
                }
                case CHARGE_FIST -> {
                    // Punch: freelook or lock-on, needs a target
                    if (!XenoServerConfig.bt3ChargeAttackEnabled || target == null) return;
                    handleChargeAttack(player, target, res, data, false, msg.chargePercent, 0);
                }
                case CHARGE_KICK -> {
                    // Kick: freelook or air
                    if (!XenoServerConfig.bt3ChargeAttackEnabled) return;
                    if (target != null) {
                        handleChargeAttack(player, target, res, data, true, msg.chargePercent, msg.verticalBias);
                    } else {
                        handleAerialKick(player, res, data, msg.chargePercent, msg.verticalBias);
                    }
                }
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
                    handleSonicSway(player, res, msg.comboStep);
                }
                case ULTIMATE -> {
                    if (!XenoServerConfig.bt3UltimateEnabled) return;
                    handleUltimate(player, target, res, data);
                }
                case SPARKING -> {
                    if (!XenoServerConfig.bt3SparkingEnabled) return;
                    net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem.tryActivate(player);
                }
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

        Vec3 dest = vanishBehind(player, target, side);
        // Face-on teleport: syncs camera + kills residual flight (teleportTo alone desyncs DMZ flight)
        teleportFacing(player, dest, target);
        playItSound(player, dest.x, dest.y, dest.z, false);
        // Stamped after the teleport so the shade is left behind the fighter rather than being
        // spawned on top of them for a tick.
net.bullettrain.xenopixelsmod.combat.VanishShadeFx.spawn(player, from);
        CombatFx.cue(player.serverLevel(), from, CombatFxKind.VANISH_CLAP, 1.0f);
    }

    private static void handleChase(ServerPlayer player, LivingEntity target, Resources res) {
        double dist = player.distanceTo(target);
        if (dist > XenoServerConfig.chaseMaxRange || dist < 2.5) return;
        if (!trySpendKi(res, XenoServerConfig.chaseKiCost)) return;

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

        Vec3 dest = chaseLanding(player, target);
        teleportFacing(player, dest, target);
        playItSound(player, dest.x, dest.y, dest.z, false);
        player.level().playSound(null, dest.x, dest.y, dest.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.4f);
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
     * Free-form aerial / no-target kick: animation + look-direction boost.
     * Still hits anything in a short cone in front.
     */
    private static void handleAerialKick(ServerPlayer player, Resources res, StatsData data,
                                         int chargePercent, int verticalBias) {
        float charge = Math.max(0.25f, Math.min(1f, chargePercent / 100f));
        float stamCost = XenoServerConfig.kickReleaseStamina(charge, verticalBias);
        if (!trySpendStamina(res, stamCost)) return;

        boolean full = charge >= 0.95f;
        DmzAnimHelper.playChargeRelease(player, DmzAnimHelper.ChargeStyle.KICK, full);

        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0e-4) {
            flat = new Vec3(0, 0, 1);
        }
        flat = flat.normalize();

        double range = kickHitRange(charge, verticalBias);
        // No self-boost on air kick either — stay put; only targets get launch

        float base = (float) Math.max(2.0, player.getAttackStrengthScale(0.5f) * 5.0f);
        if (data != null) {
            base = (float) Math.max(base, data.getMeleeDamage() * 0.45);
        }
        float mult = XenoServerConfig.chargeDamageScale * XenoServerConfig.kickDamageScale * (0.55f + 0.7f * charge);

        boolean anyHit = false;
        double inflate = verticalBias < 0 ? 1.85 : 1.35;
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
            living.setDeltaMovement(kickTargetLaunch(flat, charge, verticalBias));
            living.hurtMarked = true;
            living.hasImpulse = true;
            playKickHitSound(player, living, full);
            anyHit = true;
        }

        // Swing whoosh always; impact already played per hit
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                anyHit
                        ? (full ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG)
                        : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, anyHit ? 1.0f : 0.7f, full ? 0.8f : 1.05f);
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
        faceTarget(player, target);

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
                target.setDeltaMovement(kickTargetLaunch(kbFlat, charge, verticalBias));
            } else {
                double kb = 0.85 * (0.6 + charge);
                double up = 0.18 + charge * 0.15;
                target.setDeltaMovement(target.getDeltaMovement().add(kbFlat.scale(kb).add(0, up, 0)));
            }
            target.hurtMarked = true;
            target.hasImpulse = true;
        }

        // Impact SFX + particles at target
        if (kick) {
            playKickHitSound(player, target, full);
        } else {
            playHitSound(player, target, full);
            if (full) {
                player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.9f);
            }
        }
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            // Shockwave disc faces the way the blow travelled, so a charged hit reads as a
            // direction rather than as a puff of crits at the target's chest.
            CombatFx.impact(sl, target, target.position().subtract(player.position()),
                    full ? CombatFx.Weight.HEAVY : CombatFx.Weight.LIGHT);
        }
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
        double horiz = (0.9 + charge) * (verticalBias == 0 ? 1.0 : 0.55);
        double up;
        if (verticalBias > 0) {
            up = XenoServerConfig.kickUpLaunch * (0.85 + charge * 0.9);
            horiz *= 0.45;
        } else if (verticalBias < 0) {
            up = -XenoServerConfig.kickDownLaunch * (0.7 + charge * 0.8);
            horiz *= 0.65;
        } else {
            up = 0.35 + charge * 0.25;
        }
        return awayFlat.normalize().scale(horiz).add(0, up, 0);
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
        target.setDeltaMovement(away.scale(launch * 0.22).add(0, up, 0));
        target.hurtMarked = true;
        target.hasImpulse = true;
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
        player.level().playSound(null, chasePos.x, chasePos.y, chasePos.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    /** Returns true if chase succeeds based on {@link XenoServerConfig#chaseSuccessChance}. */
    private static boolean rollChaseSuccess(ServerPlayer player) {
        float chance = XenoServerConfig.chaseSuccessChance;
        if (chance >= 1f) return true;
        if (chance <= 0f) return false;
        return player.getRandom().nextFloat() < chance;
    }

    private static void handleSuperCounter(ServerPlayer player, LivingEntity target, Resources res, StatsData data,
                                           int side) {
        if (player.distanceTo(target) > XenoServerConfig.vanishMaxRange + 2.0) return;
        if (!trySpendKi(res, XenoServerConfig.superCounterKiCost)) return;

        net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.setGuarding(player, false);

        Vec3 from = player.position();
        playItSound(player, from.x, from.y, from.z, true);
        int s = side < 0 ? -1 : (side > 0 ? 1 : 0);
        Vec3 dest = vanishBehind(player, target, s);
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
            target.setDeltaMovement(flat.normalize().scale(0.9).add(0, 0.35, 0));
            target.hurtMarked = true;
            target.hasImpulse = true;
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
            hit.setDeltaMovement(hit.getDeltaMovement().add(look.scale(0.45).add(0, 0.12, 0)));
            hit.hurtMarked = true;
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
        if (player.distanceTo(target) > XenoServerConfig.rushChainRange) return;
        // Prefer air targets; still allow if recently knocked (high Y or not on ground)
        boolean airborne = !target.onGround() || target.getDeltaMovement().y > 0.08;
        if (!airborne && target.getY() - player.getY() < 0.8) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Rush: target must be airborne"), true);
            return;
        }
        if (!trySpendKi(res, XenoServerConfig.rushChainKiCost)) return;

        Vec3 from = player.position();
        playItSound(player, from.x, from.y, from.z, true);
        Vec3 land = new Vec3(target.getX(), target.getY(), target.getZ())
                .add(target.getDeltaMovement().scale(2.0));
        // Stay near them in the air
        Vec3 flat = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
        if (flat.lengthSqr() > 1.0e-4) {
            flat = flat.normalize().scale(-1.1);
            land = land.add(flat);
        }
        teleportFacing(player, land, target);
        playItSound(player, land.x, land.y, land.z, false);
        DmzAnimHelper.broadcastDash(player, 0);

        if (player.distanceTo(target) <= 5.5) {
            float base = (float) Math.max(2.0, player.getAttackStrengthScale(0.5f) * 4.5f);
            if (data != null) base = (float) Math.max(base, data.getMeleeDamage() * 0.42);
            float mult = XenoServerConfig.rushChainDamageScale
                    * net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem.damageMult(player);
            target.hurt(player.damageSources().playerAttack(player), base * mult);
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.15, 0));
            target.hurtMarked = true;
            playHitSound(player, target, false);
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§dRUSH CHAIN"), true);
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
                target.setDeltaMovement(flat.normalize().scale(1.8).add(0, 0.7, 0));
                target.hurtMarked = true;
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
            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                CombatFx.impact(sl, target, target.position().subtract(player.position()),
                        CombatFx.Weight.HEAVY);
            }
        }
        player.level().playSound(null, land.x, land.y, land.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.85f, 1.25f);
    }

    private static void handleCombo(ServerPlayer player, LivingEntity target, int step, Resources res, StatsData data) {
        // Deflection first, and it consumes the swing. Checked here rather than on a key of its
        // own because this runs on every attack press including a whiff, which is exactly when a
        // player is punching at an incoming blast rather than at a body.
        if (net.bullettrain.xenopixelsmod.combat.KiDeflect.tryDeflect(player, res)) return;

        // maxComboSteps = finisher every N hits; counter itself free-runs up to 99
        int finisherEvery = Math.max(1, XenoServerConfig.maxComboSteps);
        int countCap = 99;
        step = Math.max(1, Math.min(countCap, step));
        boolean finisher = XenoServerConfig.bt3FinisherEnabled && step % finisherEvery == 0;
        int scaleStep = Math.min(step, finisherEvery * 4);

        float cost = finisher ? XenoServerConfig.finisherKiCost : XenoServerConfig.comboKiCost;
        if (res != null && res.getCurrentEnergy() >= cost) {
            res.removeEnergy(cost);
        }

        // Air string / freelook miss: counter still advances, no hit
        if (target == null || !target.isAlive()) {
            return;
        }

        // Punch-only string: force left/right punches (no DMZ kick mix) for trackers
        if (XenoServerConfig.bt3ComboPunchesOnly) {
            DmzAnimHelper.broadcastComboPunch(player, step, finisher);
        }

        // No player lunge, step-in, or forced face — lock-on or freelook, stay put
        if (player.distanceTo(target) <= 4.5) {
            float base = (float) Math.max(1.0, player.getAttackStrengthScale(0.5f) * 4.0f);
            float mult = (1.0f + scaleStep * 0.12f) * XenoServerConfig.comboDamageScale;
            if (finisher) mult *= XenoServerConfig.finisherDamageScale;
            if (data != null) {
                base = (float) Math.max(base, data.getMeleeDamage() * 0.35);
            }
            target.hurt(player.damageSources().playerAttack(player), base * mult);

            Vec3 kbDir = target.position().subtract(player.position());
            Vec3 kbFlat = new Vec3(kbDir.x, 0, kbDir.z);
            if (kbFlat.lengthSqr() > 1.0e-4) {
                kbFlat = kbFlat.normalize();
                double kb = finisher ? 1.65 : 0.25 + Math.min(scaleStep, 8) * 0.08;
                double up = finisher ? 0.55 : 0.12;
                target.setDeltaMovement(target.getDeltaMovement().add(kbFlat.scale(kb).add(0, up, 0)));
                target.hurtMarked = true;
                target.hasImpulse = true;
            }

            playHitSound(player, target, finisher);
            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                // LIGHT for the string, HEAVY only on the finisher — a rush chain that punched
                // at finisher weight every hit would leave the camera permanently shaking.
                CombatFx.impact(sl, target, target.position().subtract(player.position()),
                        finisher ? CombatFx.Weight.HEAVY : CombatFx.Weight.LIGHT);
            }
        }
    }

    private static boolean trySpendKi(Resources res, float cost) {
        if (res == null) return true;
        if (res.getCurrentEnergy() < cost) return false;
        res.removeEnergy(cost);
        return true;
    }

    private static boolean trySpendStamina(Resources res, float cost) {
        if (res == null) return true;
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
    private static void teleportFacing(ServerPlayer player, Vec3 dest, LivingEntity target) {
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
        Vec3 toEnemy = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
        if (toEnemy.lengthSqr() < 1.0e-4) {
            // Fallback: opposite of target look (stand at their back)
            Vec3 look = target.getLookAngle();
            toEnemy = new Vec3(-look.x, 0, -look.z);
            if (toEnemy.lengthSqr() < 1.0e-4) {
                toEnemy = new Vec3(0, 0, 1);
            }
        }
        Vec3 toward = toEnemy.normalize(); // player → enemy; +toward past enemy = behind them
        Vec3 right = new Vec3(-toward.z, 0, toward.x);
        double s = side < 0 ? -VANISH_SIDE : (side > 0 ? VANISH_SIDE : 0.0);
        // Behind + side; Y locked to target so aerial combat lands next to them
        return new Vec3(
                target.getX() + toward.x * VANISH_GAP + right.x * s,
                target.getY(),
                target.getZ() + toward.z * VANISH_GAP + right.z * s);
    }

    /** Land short of target on the approach line (in front of them), at their height. */
    public static Vec3 chaseLanding(Entity player, LivingEntity target) {
        Vec3 flat = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
        if (flat.lengthSqr() < 1.0e-4) {
            flat = new Vec3(0, 0, 1);
        } else {
            flat = flat.normalize();
        }
        return new Vec3(
                target.getX() - flat.x * CHASE_GAP,
                target.getY(),
                target.getZ() - flat.z * CHASE_GAP);
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

    private static void playItSound(ServerPlayer player, double x, double y, double z, boolean leave) {
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
                ResourceLocation.fromNamespaceAndPath("dragonminez", leave ? "evasion1" : "evasion2"));
        if (dmz != null) {
            player.level().playSound(null, x, y, z, dmz, SoundSource.PLAYERS, 0.95f, leave ? 1.05f : 1.15f);
        } else {
            player.level().playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                    0.7f, leave ? 1.35f : 1.55f);
        }
    }

    private static void playHitSound(ServerPlayer player, LivingEntity target, boolean finisher) {
        SoundEvent punch = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("dragonminez", "fist_punch"));
        SoundEvent knock = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("dragonminez", "knockback_character"));
        if (finisher) {
            if (knock != null) {
                player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        knock, SoundSource.PLAYERS, 1.0f, 0.95f);
            }
            player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.85f);
        } else if (punch != null) {
            player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    punch, SoundSource.PLAYERS, 0.85f, 1.05f);
        } else {
            player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9f, 1.1f);
        }
    }

    /** Always plays a clear impact at the target when a kick connects. */
    private static void playKickHitSound(ServerPlayer player, LivingEntity target, boolean fullCharge) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.4;
        double z = target.getZ();

        SoundEvent punch = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("dragonminez", "fist_punch"));
        SoundEvent knock = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("dragonminez", "knockback_character"));

        // DMZ impact if available
        if (punch != null) {
            player.level().playSound(null, x, y, z, punch, SoundSource.PLAYERS, 1.15f, fullCharge ? 0.85f : 1.0f);
        }
        if (fullCharge && knock != null) {
            player.level().playSound(null, x, y, z, knock, SoundSource.PLAYERS, 1.0f, 0.9f);
        }

        // Always play vanilla impact so hit is never silent
        player.level().playSound(null, x, y, z,
                fullCharge ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS, 1.15f, fullCharge ? 0.75f : 0.95f);
        player.level().playSound(null, x, y, z,
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.85f, 1.1f);
    }

    private static void faceTarget(ServerPlayer player, LivingEntity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
    }
}
