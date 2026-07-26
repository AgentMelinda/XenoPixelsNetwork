package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.client.ClientPacketHandlers;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SyncServerConfigPacket {
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
        buf.writeBoolean(d.bt3VanishEnabled);
        buf.writeBoolean(d.bt3ChaseDashEnabled);
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
        buf.writeFloat(d.sonicSwayStaminaCost);
        buf.writeVarInt(d.sonicSwayIFramesTicks);
        buf.writeVarInt(d.sonicSwayCooldownTicks);
        buf.writeFloat(d.ultimateKiCost);
        buf.writeFloat(d.ultimateDamageScale);
        buf.writeVarInt(d.ultimateCooldownTicks);
        buf.writeFloat(d.sparkingBuildPerHit);
        buf.writeFloat(d.sparkingBuildOnHurt);
        buf.writeVarInt(d.sparkingDurationTicks);
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
    }

    public static SyncServerConfigPacket decode(FriendlyByteBuf buf) {
        XenoServerConfig.Data d = new XenoServerConfig.Data();
        d.dmzHudEnabled = buf.readBoolean();
        d.dmzContentBootstrap = buf.readBoolean();
        d.bt3CombatEnabled = buf.readBoolean();
        d.bt3ComboEnabled = buf.readBoolean();
        d.bt3VanishEnabled = buf.readBoolean();
        d.bt3ChaseDashEnabled = buf.readBoolean();
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
        d.sonicSwayStaminaCost = buf.readFloat();
        d.sonicSwayIFramesTicks = buf.readVarInt();
        d.sonicSwayCooldownTicks = buf.readVarInt();
        d.ultimateKiCost = buf.readFloat();
        d.ultimateDamageScale = buf.readFloat();
        d.ultimateCooldownTicks = buf.readVarInt();
        d.sparkingBuildPerHit = buf.readFloat();
        d.sparkingBuildOnHurt = buf.readFloat();
        d.sparkingDurationTicks = buf.readVarInt();
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
        return new SyncServerConfigPacket(d);
    }

    public static void handle(SyncServerConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketHandlers.handleServerConfig(msg.data)));
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

    private static Map<String, Float> readFormMap(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, Float> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf(256);
            float v = buf.readFloat();
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
        int n = buf.readVarInt();
        Map<String, Map<String, Float>> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf(256);
            Map<String, Float> inner = readFormMap(buf);
            if (k != null && !k.isBlank()) {
                map.put(k, inner);
            }
        }
        return map;
    }
}
