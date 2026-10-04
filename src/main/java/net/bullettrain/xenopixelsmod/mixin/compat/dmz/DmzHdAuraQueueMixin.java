package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.client.aura.HdAuraClient;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.BakedGeoModel;

/**
 * Where DragonMineZ queues a visible aura ({@code DMZAuraLayer} / hand FP).
 * The HD aura learns from here who has an aura this frame, and in HD-only mode both the
 * third-person {@code addAura} and first-person {@code addFirstPersonAura} queues are cancelled
 * so the classic DMZ sheet cannot come back (2026-10-03: TP leak when {@code plays()} was false,
 * and FP was never cancelled). Lightning {@code addSpark} is left alone.
 */
@Mixin(targets = "com.dragonminez.client.render.util.PlayerEffectQueue", remap = false)
public abstract class DmzHdAuraQueueMixin {

    @Inject(method = "addAura(Lnet/minecraft/client/player/AbstractClientPlayer;"
            + "Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lcom/mojang/blaze3d/vertex/PoseStack;FI)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$hdAura(AbstractClientPlayer player, BakedGeoModel model, PoseStack pose,
                                          float partialTick, int light, CallbackInfo ci) {
        HdAuraClient.seen(player);
        // Hide DMZ only while HD-only is actually working. If HD failed this session, leave the
        // classic sheet so the body is never blank (owner 2026-10-03 created-world blank aura).
        // Do not gate on plays() — that re-opened the TP DMZ leak when second-aura was off.
        if (HdAuraClient.replacesDmzAura()) {
            ci.cancel();
        }
    }

    @Inject(method = "addFirstPersonAura(Lnet/minecraft/client/player/AbstractClientPlayer;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;FI)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$hdFirstPersonAura(AbstractClientPlayer player, PoseStack pose,
                                                     float partialTick, int light, CallbackInfo ci) {
        HdAuraClient.seen(player);
        if (HdAuraClient.replacesDmzAura()) {
            ci.cancel();
        }
    }
}
