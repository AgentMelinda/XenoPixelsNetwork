package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.client.ClientPacketHandlers;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.util.XenoIdentifierDiagnostics;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import com.dragonminez.compat.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SyncServerConfigPacket {
    /**
     * Upper bound on decoded map entries. Real configs hold at most a few hundred form
     * multipliers; an unbounded {@code readVarInt()} count would let a forged packet grow
     * these maps until the buffer is exhausted.
     */
    private static final int MAX_MAP_ENTRIES = 4096;

    private final XenoServerConfig.Data data;

    public SyncServerConfigPacket(XenoServerConfig.Data data) {
        this.data = data != null ? data : new XenoServerConfig.Data();
    }

    public static void encode(SyncServerConfigPacket msg, FriendlyByteBuf buf) {
        XenoServerConfig.Data d = msg.data;
        buf.writeBoolean(d.dmzHudEnabled);
        buf.writeBoolean(d.dmzContentBootstrap);
        buf.writeBoolean(d.bt3CombatEnabled);
        buf.writeBoolean(d.bt3ComboEnabled);
        buf.writeBoolean(d.bt3CinematicRushEnabled);
        buf.writeBoolean(d.bt3VanishEnabled);
        buf.writeBoolean(d.bt3ChaseDashEnabled);
        buf.writeBoolean(d.chaseFlightEnabled);
        buf.writeBoolean(d.bt3BackstepEnabled);
        buf.writeBoolean(d.bt3FinisherEnabled);
        buf.writeBoolean(d.bt3ChargeAttackEnabled);
        buf.writeBoolean(d.bt3DragonDashEnabled);
        buf.writeBoolean(d.bt3GuardEnabled);
        buf.writeBoolean(d.bt3SuperCounterEnabled);
        buf.writeBoolean(d.bt3KiBlastCancelEnabled);
        buf.writeBoolean(d.bt3ZBurstEnabled);
        buf.writeBoolean(d.bt3LockCycleEnabled);
        buf.writeBoolean(d.bt3ComboPunchesOnly);
        buf.writeBoolean(d.protectDmzMasters);
        buf.writeBoolean(d.protectMastersFromCombatKnockback);
        buf.writeBoolean(d.migrateCustomNpcsWorldData);
        buf.writeBoolean(d.npcDmzStatsAuthoritative == null || d.npcDmzStatsAuthoritative);
        buf.writeVarInt(d.npcScriptTickInterval == null ? 1 : d.npcScriptTickInterval);
        buf.writeBoolean(d.bt3RushChainEnabled);
        buf.writeBoolean(d.bt3SonicSwayEnabled);
        buf.writeBoolean(d.bt3UltimateEnabled);
        buf.writeBoolean(d.bt3SparkingEnabled);
        buf.writeBoolean(d.bt3TransformImpactEnabled);
        buf.writeFloat(d.chaseSuccessChance);
        buf.writeFloat(d.guardDamageReduction);
        buf.writeFloat(d.guardStaminaPerHit);
        buf.writeFloat(d.guardStaminaPerSec);
        buf.writeVarInt(d.guardBreakStunTicks);
        buf.writeVarInt(d.superCounterWindowTicks);
        buf.writeFloat(d.superCounterKiCost);
        buf.writeFloat(d.superCounterDamageScale);
        buf.writeFloat(d.kiBlastCancelKiCost);
        buf.writeFloat(d.kiBlastCancelDamageScale);
        buf.writeFloat(d.zBurstKiCost);
        buf.writeFloat(d.zBurstDamageScale);
        buf.writeDouble(d.zBurstRange);
        buf.writeFloat(d.rushChainKiCost);
        buf.writeFloat(d.rushChainDamageScale);
        buf.writeDouble(d.rushChainRange);
        buf.writeDouble(d.rushKiCost);
        buf.writeVarInt(d.rushCooldownTicks);
        buf.writeBoolean(d.zanzokenEnabled);
        buf.writeFloat(d.zanzokenKiCost);
        buf.writeVarInt(d.zanzokenWindowTicks);
        buf.writeVarInt(d.zanzokenIFramesTicks);
        buf.writeVarInt(d.zanzokenCooldownTicks);
        buf.writeBoolean(d.zanzokenGhostAfterimage);
        buf.writeVarInt(d.zanzokenGhostFadeMode == null ? 2 : d.zanzokenGhostFadeMode);
        buf.writeFloat(d.zanzokenGhostAlpha == null ? 0.55f : d.zanzokenGhostAlpha);
        buf.writeVarInt(d.zanzokenRingClones);
        buf.writeDouble(d.zanzokenRingRadius);
        buf.writeVarInt(d.zanzokenRingTicks);
        buf.writeBoolean(d.multiFormEnabled);
        buf.writeVarInt(d.multiFormBodies);
        buf.writeFloat(d.multiFormKiCost);
        buf.writeFloat(d.sonicSwayStaminaCost);
        buf.writeVarInt(d.sonicSwayIFramesTicks);
        buf.writeVarInt(d.sonicSwayCooldownTicks);
        buf.writeFloat(d.ultimateKiCost);
        buf.writeFloat(d.ultimateDamageScale);
        buf.writeVarInt(d.ultimateCooldownTicks);
        buf.writeFloat(d.sparkingBuildPerHit);
        buf.writeFloat(d.sparkingBuildOnHurt);
        buf.writeVarInt(d.sparkingDurationTicks);
        buf.writeVarInt(d.sparkingChargeTicks);
        buf.writeVarInt(d.sparkingCooldownTicks);
        buf.writeFloat(d.sparkingDamageMult);
        buf.writeFloat(d.transformImpactRadius);
        buf.writeFloat(d.transformImpactKnock);
        buf.writeFloat(d.formStatMultiplier != null ? d.formStatMultiplier : 1.0f);
        writeFormMap(buf, d.formPerFormMultipliers);
        writeFormMap(buf, d.formPerStatMultipliers);
        writeNestedFormMap(buf, d.formPerFormStatMultipliers);
        buf.writeBoolean(d.kiOverchargeEnabled);
        buf.writeVarInt(d.kiOverchargeThreshold);
        buf.writeFloat(d.kiOverchargeSizePerPercent);
        buf.writeFloat(d.kiOverchargeDamagePerPercent);
        buf.writeFloat(d.kiOverchargeExplosionPerPercent);
        buf.writeFloat(d.kiOverchargeMultiplier);
        buf.writeFloat(d.kiOverchargeMaxScale);
        buf.writeFloat(d.kiProjectileMaxSize);
        buf.writeFloat(d.kiProjectileMaxSpeed);
        buf.writeFloat(d.kiOverchargeSpeedPerPercent);
        buf.writeBoolean(d.kiFullGameplayScaling);
        buf.writeFloat(d.kiDestructionMaxRadius);
        buf.writeVarInt(d.kiDestructionBlocksPerTick);
        buf.writeDouble(d.vanishMaxRange);
        buf.writeDouble(d.chaseMaxRange);
        buf.writeDouble(d.backstepMaxRange);
        buf.writeDouble(d.chargeAttackRange);
        buf.writeDouble(d.dragonDashRange);
        buf.writeFloat(d.vanishKiCost);
        buf.writeFloat(d.chaseKiCost);
        buf.writeFloat(d.backstepKiCost);
        buf.writeFloat(d.comboKiCost);
        buf.writeFloat(d.finisherKiCost);
        buf.writeFloat(d.dragonDashKiCost);
        buf.writeFloat(d.fistChargeStaminaCost);
        buf.writeFloat(d.kickChargeStaminaCost);
        buf.writeFloat(d.chargeStaminaCost);
        buf.writeFloat(d.chargeHoldStaminaPerSec);
        buf.writeFloat(d.dragonDashStaminaCost);
        buf.writeFloat(d.kickVerticalExtraStamina);
        buf.writeFloat(d.comboDamageScale);
        buf.writeFloat(d.finisherDamageScale);
        buf.writeFloat(d.chargeDamageScale);
        buf.writeFloat(d.kickDamageScale);
        buf.writeFloat(d.kickUpLaunch);
        buf.writeFloat(d.kickDownLaunch);
        buf.writeFloat(d.kickDownRangeBonus);
        buf.writeVarInt(d.maxComboSteps);
        buf.writeVarInt(d.chargeMaxTicks);
        buf.writeBoolean(d.kiBlastHoldToFire);
        buf.writeVarInt(Math.max(1, d.kiBlastCooldownTicks));
        buf.writeVarInt(Math.max(0, d.barrageDurationTicks));
        buf.writeVarInt(Math.max(0, d.barrageCooldownTicks));
        buf.writeVarInt(Math.max(0, d.kiDurationTicks));
        writeIntMap(buf, d.kiDurationByType);
        buf.writeBoolean(d.chargeOverchargeEnabled);
        buf.writeFloat(d.chargeOverchargeMaxPercent);
        buf.writeVarInt(d.chargeOverchargeMinLevel);
        buf.writeFloat(d.chargeOverchargeSizePerPercent);
        buf.writeFloat(d.chargeOverchargeSpeedPerPercent);
        buf.writeFloat(d.chargeOverchargeMaxDamageScale);
        buf.writeFloat(d.chargeFormSizeFactor);
        buf.writeFloat(d.chargeFormSizeLogCap);
        writeFormMap(buf, d.chargeFormSizeByForm);
        buf.writeBoolean(d.chargeOverchargeGriefEnabled);
        buf.writeFloat(d.chargeOverchargeCraterMinPercent);
        buf.writeVarInt(d.chargeOverchargeCraterMaxRadius);
        buf.writeVarInt(d.chargeOverchargeCraterIntervalTicks);
        buf.writeVarInt(d.chargeOverchargeMaxRocks);
        buf.writeBoolean(d.chargeOverchargeDiskSliceEnabled);
        buf.writeBoolean(d.chargeOverchargeCameraEnabled);
        buf.writeBoolean(d.chargeOverchargeVoicesEnabled);
        buf.writeVarInt(Math.max(0, d.guidanceControlRange));
        buf.writeFloat(d.guidanceTurnRate);
        buf.writeFloat(d.guidanceCameraRate);
        buf.writeVarInt(Math.max(0, d.guidanceHoldGraceTicks));
        buf.writeFloat(d.guidanceLookRayMin);
        buf.writeVarInt(Math.max(0, d.barrageExtraTicks1));
        buf.writeVarInt(Math.max(0, d.barrageExtraTicks2));
        buf.writeVarInt(Math.max(0, d.barrageExtraTicks3));
        buf.writeVarInt(Math.max(0, d.barrageCooldownReduce1));
        buf.writeVarInt(Math.max(0, d.barrageCooldownReduce2));
        buf.writeVarInt(Math.max(0, d.barrageCooldownReduce3));
        buf.writeFloat(d.barrageKiPerTick);
        buf.writeBoolean(d.beamSurgeEnabled);
        buf.writeFloat(d.beamSurgeKiPerTick);
        buf.writeFloat(d.beamSurgeStaminaPerTick);
        buf.writeFloat(d.beamSurgeCostGrowth);
        buf.writeFloat(d.beamSurgeSizeGain);
        buf.writeFloat(d.beamSurgeDamageGain);
        buf.writeFloat(d.beamSurgeReachGain);
        buf.writeFloat(d.beamSurgeSearchRadius);
        buf.writeFloat(d.beamSurgeCeiling);
        buf.writeFloat(d.beamSurgeCeilingPerMastery);
        buf.writeFloat(d.beamSurgeRampPerTick);
        buf.writeFloat(d.beamSurgeRampPerMastery);
        buf.writeFloat(d.beamSurgeMaxLength);
        buf.writeBoolean(d.lockOnThroughBlocks == null || d.lockOnThroughBlocks);
        buf.writeDouble(d.chaseStopGap);
        buf.writeDouble(d.vanishGap);
        buf.writeDouble(d.vanishSide);
        buf.writeVarInt(Math.max(0, d.comboLaunchKickEvery));
        buf.writeVarInt(Math.max(2, Math.min(20, d.comboMashIntervalTicks)));
        buf.writeVarInt(net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog
                .clampGeneration(d.comboAnimGeneration));
        buf.writeDouble(d.vanishNearField);
        buf.writeBoolean(d.vanishOpenSpotSearch);
        buf.writeFloat(d.comboLauncherUp);
        buf.writeFloat(d.comboLauncherHoriz);
        buf.writeDouble(d.hakaiMaxRange);
        buf.writeBoolean(d.hakaiFadeEnabled);
        buf.writeFloat(d.hakaiFadeMinAlpha);
        buf.writeFloat(d.hakaiFadeCurve);
        buf.writeVarInt(d.hakaiFadeRestoreTicks);
        buf.writeFloat(d.hakaiFadeSpeed);
        buf.writeFloat(d.hakaiFadeBand);
        buf.writeInt(d.hakaiFxColor);
        buf.writeInt(d.hakaiFxRimColor);
        buf.writeBoolean(d.hakaiFxEnabled == null || d.hakaiFxEnabled);
        buf.writeBoolean(d.hakaiDustEnabled == null || d.hakaiDustEnabled);
        buf.writeBoolean(d.hakaiSilhouetteEnabled == null || d.hakaiSilhouetteEnabled);
        buf.writeInt(d.hakaiSilhouetteColor);
        buf.writeInt(d.hakaiGlowColor);
    }

    public static SyncServerConfigPacket decode(FriendlyByteBuf buf) {
        XenoServerConfig.Data d = new XenoServerConfig.Data();
        d.dmzHudEnabled = buf.readBoolean();
        d.dmzContentBootstrap = buf.readBoolean();
        d.bt3CombatEnabled = buf.readBoolean();
        d.bt3ComboEnabled = buf.readBoolean();
        d.bt3CinematicRushEnabled = buf.readBoolean();
        d.bt3VanishEnabled = buf.readBoolean();
        d.bt3ChaseDashEnabled = buf.readBoolean();
        d.chaseFlightEnabled = buf.readBoolean();
        d.bt3BackstepEnabled = buf.readBoolean();
        d.bt3FinisherEnabled = buf.readBoolean();
        d.bt3ChargeAttackEnabled = buf.readBoolean();
        d.bt3DragonDashEnabled = buf.readBoolean();
        d.bt3GuardEnabled = buf.readBoolean();
        d.bt3SuperCounterEnabled = buf.readBoolean();
        d.bt3KiBlastCancelEnabled = buf.readBoolean();
        d.bt3ZBurstEnabled = buf.readBoolean();
        d.bt3LockCycleEnabled = buf.readBoolean();
        d.bt3ComboPunchesOnly = buf.readBoolean();
        d.protectDmzMasters = buf.readBoolean();
        d.protectMastersFromCombatKnockback = buf.readBoolean();
        d.migrateCustomNpcsWorldData = buf.readBoolean();
        d.npcDmzStatsAuthoritative = buf.readBoolean();
        d.npcScriptTickInterval = buf.readVarInt();
        d.bt3RushChainEnabled = buf.readBoolean();
        d.bt3SonicSwayEnabled = buf.readBoolean();
        d.bt3UltimateEnabled = buf.readBoolean();
        d.bt3SparkingEnabled = buf.readBoolean();
        d.bt3TransformImpactEnabled = buf.readBoolean();
        d.chaseSuccessChance = buf.readFloat();
        d.guardDamageReduction = buf.readFloat();
        d.guardStaminaPerHit = buf.readFloat();
        d.guardStaminaPerSec = buf.readFloat();
        d.guardBreakStunTicks = buf.readVarInt();
        d.superCounterWindowTicks = buf.readVarInt();
        d.superCounterKiCost = buf.readFloat();
        d.superCounterDamageScale = buf.readFloat();
        d.kiBlastCancelKiCost = buf.readFloat();
        d.kiBlastCancelDamageScale = buf.readFloat();
        d.zBurstKiCost = buf.readFloat();
        d.zBurstDamageScale = buf.readFloat();
        d.zBurstRange = buf.readDouble();
        d.rushChainKiCost = buf.readFloat();
        d.rushChainDamageScale = buf.readFloat();
        d.rushChainRange = buf.readDouble();
        d.rushKiCost = buf.readDouble();
        d.rushCooldownTicks = buf.readVarInt();
        d.zanzokenEnabled = buf.readBoolean();
        d.zanzokenKiCost = buf.readFloat();
        d.zanzokenWindowTicks = buf.readVarInt();
        d.zanzokenIFramesTicks = buf.readVarInt();
        d.zanzokenCooldownTicks = buf.readVarInt();
        d.zanzokenGhostAfterimage = buf.readBoolean();
        d.zanzokenGhostFadeMode = buf.readVarInt();
        d.zanzokenGhostAlpha = buf.readFloat();
        d.zanzokenRingClones = buf.readVarInt();
        d.zanzokenRingRadius = buf.readDouble();
        d.zanzokenRingTicks = buf.readVarInt();
        d.multiFormEnabled = buf.readBoolean();
        d.multiFormBodies = buf.readVarInt();
        d.multiFormKiCost = buf.readFloat();
        d.sonicSwayStaminaCost = buf.readFloat();
        d.sonicSwayIFramesTicks = buf.readVarInt();
        d.sonicSwayCooldownTicks = buf.readVarInt();
        d.ultimateKiCost = buf.readFloat();
        d.ultimateDamageScale = buf.readFloat();
        d.ultimateCooldownTicks = buf.readVarInt();
        d.sparkingBuildPerHit = buf.readFloat();
        d.sparkingBuildOnHurt = buf.readFloat();
        d.sparkingDurationTicks = buf.readVarInt();
        d.sparkingChargeTicks = buf.readVarInt();
        d.sparkingCooldownTicks = buf.readVarInt();
        d.sparkingDamageMult = buf.readFloat();
        d.transformImpactRadius = buf.readFloat();
        d.transformImpactKnock = buf.readFloat();
        d.formStatMultiplier = buf.readFloat();
        d.formPerFormMultipliers = readFormMap(buf);
        d.formPerStatMultipliers = readFormMap(buf);
        d.formPerFormStatMultipliers = readNestedFormMap(buf);
        d.kiOverchargeEnabled = buf.readBoolean();
        d.kiOverchargeThreshold = buf.readVarInt();
        d.kiOverchargeSizePerPercent = buf.readFloat();
        d.kiOverchargeDamagePerPercent = buf.readFloat();
        d.kiOverchargeExplosionPerPercent = buf.readFloat();
        d.kiOverchargeMultiplier = buf.readFloat();
        d.kiOverchargeMaxScale = buf.readFloat();
        d.kiProjectileMaxSize = buf.readFloat();
        d.kiProjectileMaxSpeed = buf.readFloat();
        d.kiOverchargeSpeedPerPercent = buf.readFloat();
        d.kiFullGameplayScaling = buf.readBoolean();
        d.kiDestructionMaxRadius = buf.readFloat();
        d.kiDestructionBlocksPerTick = buf.readVarInt();
        d.vanishMaxRange = buf.readDouble();
        d.chaseMaxRange = buf.readDouble();
        d.backstepMaxRange = buf.readDouble();
        d.chargeAttackRange = buf.readDouble();
        d.dragonDashRange = buf.readDouble();
        d.vanishKiCost = buf.readFloat();
        d.chaseKiCost = buf.readFloat();
        d.backstepKiCost = buf.readFloat();
        d.comboKiCost = buf.readFloat();
        d.finisherKiCost = buf.readFloat();
        d.dragonDashKiCost = buf.readFloat();
        d.fistChargeStaminaCost = buf.readFloat();
        d.kickChargeStaminaCost = buf.readFloat();
        d.chargeStaminaCost = buf.readFloat();
        d.chargeHoldStaminaPerSec = buf.readFloat();
        d.dragonDashStaminaCost = buf.readFloat();
        d.kickVerticalExtraStamina = buf.readFloat();
        d.comboDamageScale = buf.readFloat();
        d.finisherDamageScale = buf.readFloat();
        d.chargeDamageScale = buf.readFloat();
        d.kickDamageScale = buf.readFloat();
        d.kickUpLaunch = buf.readFloat();
        d.kickDownLaunch = buf.readFloat();
        d.kickDownRangeBonus = buf.readFloat();
        d.maxComboSteps = buf.readVarInt();
        d.chargeMaxTicks = buf.readVarInt();
        d.kiBlastHoldToFire = buf.readBoolean();
        d.kiBlastCooldownTicks = Math.max(1, buf.readVarInt());
        d.barrageDurationTicks = Math.max(0, buf.readVarInt());
        d.barrageCooldownTicks = Math.max(0, buf.readVarInt());
        d.kiDurationTicks = Math.max(0, buf.readVarInt());
        d.kiDurationByType = readIntMap(buf);
        d.chargeOverchargeEnabled = buf.readBoolean();
        d.chargeOverchargeMaxPercent = buf.readFloat();
        d.chargeOverchargeMinLevel = buf.readVarInt();
        d.chargeOverchargeSizePerPercent = buf.readFloat();
        d.chargeOverchargeSpeedPerPercent = buf.readFloat();
        d.chargeOverchargeMaxDamageScale = buf.readFloat();
        d.chargeFormSizeFactor = buf.readFloat();
        d.chargeFormSizeLogCap = buf.readFloat();
        d.chargeFormSizeByForm = readFormMap(buf);
        d.chargeOverchargeGriefEnabled = buf.readBoolean();
        d.chargeOverchargeCraterMinPercent = buf.readFloat();
        d.chargeOverchargeCraterMaxRadius = buf.readVarInt();
        d.chargeOverchargeCraterIntervalTicks = buf.readVarInt();
        d.chargeOverchargeMaxRocks = buf.readVarInt();
        d.chargeOverchargeDiskSliceEnabled = buf.readBoolean();
        d.chargeOverchargeCameraEnabled = buf.readBoolean();
        d.chargeOverchargeVoicesEnabled = buf.readBoolean();
        d.guidanceControlRange = Math.max(0, buf.readVarInt());
        d.guidanceTurnRate = buf.readFloat();
        d.guidanceCameraRate = buf.readFloat();
        d.guidanceHoldGraceTicks = Math.max(0, buf.readVarInt());
        d.guidanceLookRayMin = buf.readFloat();
        d.barrageExtraTicks1 = Math.max(0, buf.readVarInt());
        d.barrageExtraTicks2 = Math.max(0, buf.readVarInt());
        d.barrageExtraTicks3 = Math.max(0, buf.readVarInt());
        d.barrageCooldownReduce1 = Math.max(0, buf.readVarInt());
        d.barrageCooldownReduce2 = Math.max(0, buf.readVarInt());
        d.barrageCooldownReduce3 = Math.max(0, buf.readVarInt());
        d.barrageKiPerTick = buf.readFloat();
        d.beamSurgeEnabled = buf.readBoolean();
        d.beamSurgeKiPerTick = buf.readFloat();
        d.beamSurgeStaminaPerTick = buf.readFloat();
        d.beamSurgeCostGrowth = buf.readFloat();
        d.beamSurgeSizeGain = buf.readFloat();
        d.beamSurgeDamageGain = buf.readFloat();
        d.beamSurgeReachGain = buf.readFloat();
        d.beamSurgeSearchRadius = buf.readFloat();
        d.beamSurgeCeiling = buf.readFloat();
        d.beamSurgeCeilingPerMastery = buf.readFloat();
        d.beamSurgeRampPerTick = buf.readFloat();
        d.beamSurgeRampPerMastery = buf.readFloat();
        d.beamSurgeMaxLength = buf.readFloat();
        d.lockOnThroughBlocks = buf.readBoolean();
        d.chaseStopGap = buf.readDouble();
        d.vanishGap = buf.readDouble();
        d.vanishSide = buf.readDouble();
        d.comboLaunchKickEvery = Math.max(0, buf.readVarInt());
        d.comboMashIntervalTicks = Math.max(2, Math.min(20, buf.readVarInt()));
        d.comboAnimGeneration = net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog
                .clampGeneration(buf.readVarInt());
        d.vanishNearField = buf.readDouble();
        d.vanishOpenSpotSearch = buf.readBoolean();
        d.comboLauncherUp = buf.readFloat();
        d.comboLauncherHoriz = buf.readFloat();
        d.hakaiMaxRange = buf.readDouble();
        d.hakaiFadeEnabled = buf.readBoolean();
        d.hakaiFadeMinAlpha = buf.readFloat();
        d.hakaiFadeCurve = buf.readFloat();
        d.hakaiFadeRestoreTicks = buf.readVarInt();
        d.hakaiFadeSpeed = buf.readFloat();
        d.hakaiFadeBand = buf.readFloat();
        d.hakaiFxColor = buf.readInt();
        d.hakaiFxRimColor = buf.readInt();
        d.hakaiFxEnabled = buf.readBoolean();
        d.hakaiDustEnabled = buf.readBoolean();
        d.hakaiSilhouetteEnabled = buf.readBoolean();
        d.hakaiSilhouetteColor = buf.readInt();
        d.hakaiGlowColor = buf.readInt();
        return new SyncServerConfigPacket(d);
    }

    public static void handle(SyncServerConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketHandlers.handleServerConfig(msg.data));
        ctx.get().setPacketHandled(true);
    }

    private static void writeFormMap(FriendlyByteBuf buf, Map<String, Float> map) {
        if (map == null || map.isEmpty()) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(map.size());
        for (Map.Entry<String, Float> e : map.entrySet()) {
            buf.writeUtf(e.getKey() != null ? e.getKey() : "", 256);
            buf.writeFloat(e.getValue() != null ? e.getValue() : 1.0f);
        }
    }

    private static void writeIntMap(FriendlyByteBuf buf, Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(map.size());
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            buf.writeUtf(e.getKey() != null ? e.getKey() : "", 64);
            buf.writeVarInt(e.getValue() != null ? e.getValue() : 0);
        }
    }

    private static Map<String, Integer> readIntMap(FriendlyByteBuf buf) {
        int n = Math.max(0, Math.min(MAX_MAP_ENTRIES, buf.readVarInt()));
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf(64);
            int v = buf.readVarInt();
            if (k == null || k.isBlank()) {
                XenoIdentifierDiagnostics.reportIfMalformed(k,
                        "SyncServerConfigPacket intMap key index=" + i);
            }
            if (k != null && !k.isBlank()) map.put(k.toLowerCase(), v);
        }
        return map;
    }

    private static Map<String, Float> readFormMap(FriendlyByteBuf buf) {
        int n = Math.max(0, Math.min(MAX_MAP_ENTRIES, buf.readVarInt()));
        Map<String, Float> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf(256);
            float v = buf.readFloat();
            if (k == null || k.isBlank()) {
                XenoIdentifierDiagnostics.reportIfMalformed(k,
                        "SyncServerConfigPacket formMap key index=" + i);
            }
            if (k != null && !k.isBlank()) {
                map.put(k, v);
            }
        }
        return map;
    }

    private static void writeNestedFormMap(FriendlyByteBuf buf, Map<String, Map<String, Float>> map) {
        if (map == null || map.isEmpty()) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(map.size());
        for (Map.Entry<String, Map<String, Float>> e : map.entrySet()) {
            buf.writeUtf(e.getKey() != null ? e.getKey() : "", 256);
            writeFormMap(buf, e.getValue());
        }
    }

    private static Map<String, Map<String, Float>> readNestedFormMap(FriendlyByteBuf buf) {
        int n = Math.max(0, Math.min(MAX_MAP_ENTRIES, buf.readVarInt()));
        Map<String, Map<String, Float>> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf(256);
            Map<String, Float> inner = readFormMap(buf);
            if (k == null || k.isBlank()) {
                XenoIdentifierDiagnostics.reportIfMalformed(k,
                        "SyncServerConfigPacket nestedFormMap key index=" + i);
            }
            if (k != null && !k.isBlank()) {
                map.put(k, inner);
            }
        }
        return map;
    }
}
