package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.dragonminez.compat.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: begin / cancel charge pose so nearby players see DMZ charge animations while holding.
 */
public class ChargeAnimPacket {
    public enum Phase {
        START,
        CANCEL
    }

    private final Phase phase;
    private final DmzAnimHelper.ChargeStyle style;

    public ChargeAnimPacket(Phase phase, DmzAnimHelper.ChargeStyle style) {
        this.phase = phase;
        this.style = style != null ? style : DmzAnimHelper.ChargeStyle.FIST_LIGHT;
    }

    public static void encode(ChargeAnimPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.phase);
        buf.writeEnum(msg.style);
    }

    public static ChargeAnimPacket decode(FriendlyByteBuf buf) {
        return new ChargeAnimPacket(buf.readEnum(Phase.class), buf.readEnum(DmzAnimHelper.ChargeStyle.class));
    }

    public static void handle(ChargeAnimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.phase == Phase.CANCEL) {
                DmzAnimHelper.broadcastChargeStop(player);
                return;
            }
            if (!XenoServerConfig.bt3CombatEnabled) return;
            if (msg.phase == Phase.START) {
                if ((msg.style == DmzAnimHelper.ChargeStyle.FIST_LIGHT || msg.style == DmzAnimHelper.ChargeStyle.FIST_HEAVY)
                        && !net.bullettrain.xenopixelsmod.combat.FistInputPolicy.emptyHands(
                                player.getMainHandItem().isEmpty(), player.getOffhandItem().isEmpty(),
                                com.dragonminez.common.combat.logic.player.PlayerAttackHelper.isKiWeaponActive(player))) return;
                if (msg.style == DmzAnimHelper.ChargeStyle.DRAGON && !XenoServerConfig.bt3DragonDashEnabled) return;
                if (msg.style != DmzAnimHelper.ChargeStyle.DRAGON && !XenoServerConfig.bt3ChargeAttackEnabled) return;
                DmzAnimHelper.broadcastChargeStart(player, msg.style);
            } else {
                DmzAnimHelper.broadcastChargeStop(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
