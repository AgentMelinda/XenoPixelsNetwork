package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.common.network.C2S.DeleteTechniqueC2S;
import com.dragonminez.common.network.NetworkHandler;
import net.bullettrain.xenopixelsmod.client.combat.DeleteTechniqueConfirm;
import net.bullettrain.xenopixelsmod.mixin.common.DeleteTechniqueC2SAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@code NetworkHandler#sendToServer}
 * Reason: Skills delete X has no confirm and sits next to Bind to Slot.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x
 * Side: client. Body is an id compare + one chat line.
 */
@Mixin(value = NetworkHandler.class, remap = false)
public abstract class DeleteTechniqueConfirmMixin {

    @Inject(method = "sendToServer", at = @At("HEAD"), cancellable = true)
    private static void xenopixels$confirmDelete(Object message, CallbackInfo ci) {
        if (!(message instanceof DeleteTechniqueC2S packet)) return;
        String id = ((DeleteTechniqueC2SAccessor) packet).xenopixels$techniqueId();
        if (!DeleteTechniqueConfirm.tryConfirm(id)) {
            ci.cancel();
        }
    }
}
