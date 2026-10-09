package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.common.network.C2S.CombatAttackRequestC2S;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.bullettrain.xenopixelsmod.combat.v3.V3Melee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@code CombatAttackRequestC2S.handle(Supplier)}.
 * Reason: V3 melee is admitted by its session-bound input channel and executed from server-owned
 *         fields. Raw DMZ attack packets are refused before they can enqueue independent work.
 * Version: NeoForge 1.21.1 / DMZ 2.1.3
 * Side: common (logical server).
 */
@Mixin(value = CombatAttackRequestC2S.class, remap = false)
public abstract class CombatAttackRequestV3GateMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$refuseRawV3Request(Supplier<NetworkEvent.Context> supplier, CallbackInfo ci) {
        NetworkEvent.Context context = supplier == null ? null : supplier.get();
        if (context != null && V3Melee.intercepts(context.getSender())) {
            context.setPacketHandled(true);
            ci.cancel();
        }
    }
}
