package net.bullettrain.xenopixelsmod.mixin.compat.cnpcgecko;

import net.bullettrain.xenopixelsmod.compat.npc.CnpcGeckoPayloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.goodbird.cnpcgeckoaddon.network.PacketSyncAnimation", remap = false)
public abstract class PacketSyncAnimationTypeMixin {
    @Inject(method = "type", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$namespacedType(CallbackInfoReturnable<CustomPacketPayload.Type<?>> cir) {
        cir.setReturnValue(CnpcGeckoPayloads.SYNC_ANIMATION);
    }
}
