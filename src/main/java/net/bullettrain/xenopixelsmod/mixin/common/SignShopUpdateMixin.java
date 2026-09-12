package net.bullettrain.xenopixelsmod.mixin.common;

import net.bullettrain.xenopixelsmod.shop.SignListingProtection;
import net.bullettrain.xenopixelsmod.shop.SignShopData;
import net.bullettrain.xenopixelsmod.shop.SignShopManager;
import net.bullettrain.xenopixelsmod.shop.SignShopReader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers a sign shop when a player finalizes a sign edit.
 *
 * <p>{@code ServerGamePacketListenerImpl.handleSignUpdate} is the narrowest server-side point at
 * which a sign's text is known to be final: the packet has been received, the distance/wax checks
 * have run, and {@code SignBlockEntity.updateSignText} has been applied. Injecting at {@code TAIL}
 * means the store only records what actually landed in the block entity.</p>
 *
 * <p>The stored text is read back from the block entity rather than from the packet, so the
 * registry can never disagree with what the sign really says. A sign that stops being a valid shop
 * is removed from the registry, which is how re-editing a shop back into a plain sign works.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SignShopUpdateMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleSignUpdate", at = @At("HEAD"), cancellable = true)
    private void xenopixels$guardSignListingEdit(ServerboundSignUpdatePacket packet, CallbackInfo ci) {
        if (this.player == null || packet == null) {
            return;
        }
        if (!(this.player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getBlockEntity(packet.getPos()) instanceof SignBlockEntity sign)) {
            return;
        }
        if (SignListingProtection.denyEdit(this.player, sign, incomingLines(packet), packet.isFrontText())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSignUpdate", at = @At("TAIL"))
    private void xenopixels$registerSignShop(ServerboundSignUpdatePacket packet, CallbackInfo ci) {
        if (this.player == null || packet == null) {
            return;
        }
        if (!(this.player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = packet.getPos();
        ResourceLocation dimension = level.dimension().location();
        SignShopManager manager = SignShopManager.get(level.getServer());

        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            return;
        }
        SignShopData data = SignShopReader.fromSignText(sign.getText(packet.isFrontText()), false);
        if (data == null) {
            // The marker was removed, the payload became invalid, or the target no longer resolves.
            manager.remove(dimension, pos);
            return;
        }
        manager.put(dimension, pos, this.player.getUUID(), data);
    }

    private static String[] incomingLines(ServerboundSignUpdatePacket packet) {
        String[] lines = packet.getLines();
        return lines != null ? lines : new String[0];
    }
}