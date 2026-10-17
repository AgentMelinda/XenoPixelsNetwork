package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.space.SpaceHandoffOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the last rendered planet/orbit frame before vanilla replaces the ClientLevel. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerSpaceHandoffMixin {
    @Inject(method = "handleRespawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            shift = At.Shift.AFTER))
    private void xenopixels$captureSpaceHandoffFrame(ClientboundRespawnPacket packet, CallbackInfo ci) {
        SpaceHandoffOverlay.captureBeforeRespawn();
    }
}
