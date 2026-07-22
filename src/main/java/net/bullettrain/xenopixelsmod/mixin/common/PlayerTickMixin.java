package net.bullettrain.xenopixelsmod.mixin.common;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.SyncXenoStatsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.tickCount % 5 != 0) return; // every 0.25s

        serverPlayer.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
            // simple regen example – replace with real DragonMineZ values later
            if (data.getKi() < data.getMaxKi()) data.setKi(data.getKi() + 0.4f);
            if (data.getStamina() < data.getMaxStamina()) data.setStamina(data.getStamina() + 0.6f);

            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SyncXenoStatsPacket(
                            serverPlayer.getHealth(),
                            serverPlayer.getMaxHealth(),
                            data.getKi(),
                            data.getMaxKi(),
                            data.getStamina(),
                            data.getMaxStamina()
                    )
            );
        });
    }
}