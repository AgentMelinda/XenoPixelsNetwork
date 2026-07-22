package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

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
        buf.writeFloat(d.chaseSuccessChance);
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
        d.chaseSuccessChance = buf.readFloat();
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
                XenoServerClientState.apply(msg.data)));
        ctx.get().setPacketHandled(true);
    }
}
