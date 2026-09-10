package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CustomNPCs {@code npc.say()} / {@code saySurrounding} is PacketChatBubble to everyone
 * within 20 blocks. Ops standing there see it as chat spam. {@code npcSayEnabled=false}
 * drops both entry points.
 *
 * <p>The My NPCs twin of the CustomNPCs mixin of the same name. My NPCs is CustomNPCs with
 * its root package renamed, so the two are identical but for the types they name; this one
 * is gated on the {@code mynpcs} mod id and its twin on {@code customnpcs}, so exactly one
 * applies. Fix bugs in both.
 */
@Mixin(targets = "espi.mynpcs.entity.EntityNPCInterface", remap = false)
public abstract class EntityNpcSayMixin {

    @Inject(method = "saySurrounding", at = @At("HEAD"), cancellable = true)
    private void xenopixels$muteSaySurrounding(CallbackInfo ci) {
        if (!XenoServerConfig.npcSayEnabled) {
            ci.cancel();
        }
    }

    @Inject(
            method = "say(Lnet/minecraft/world/entity/player/Player;Lespi/mynpcs/controllers/data/Line;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void xenopixels$muteSay(CallbackInfo ci) {
        if (!XenoServerConfig.npcSayEnabled) {
            ci.cancel();
        }
    }
}
