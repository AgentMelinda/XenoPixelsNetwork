package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.combat.ClientLockState;
import net.bullettrain.xenopixelsmod.combat.targeting.LockOnValidator;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Server &rarr; client: an explicit lock request was refused, and why.
 *
 * <p>Distinct from silence: a request dropped for being <i>rate-limited</i> gets no feedback at
 * all (a laggy client legitimately bursts, and that is not something worth telling the player
 * about), but a request that named an ineligible target — out of range, no line of sight, an
 * ally, an invalid target — gets this, so the pilot's HUD can say why rather than the crosshair
 * just silently not locking.
 */
public class TargetLockErrorPacket {
    private final LockOnValidator.Reason reason;

    public TargetLockErrorPacket(LockOnValidator.Reason reason) {
        this.reason = reason == null ? LockOnValidator.Reason.OK : reason;
    }

    public TargetLockErrorPacket(FriendlyByteBuf buf) {
        this.reason = buf.readEnum(LockOnValidator.Reason.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(reason);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientLockState.acceptError(reason));
        ctx.setPacketHandled(true);
    }
}
