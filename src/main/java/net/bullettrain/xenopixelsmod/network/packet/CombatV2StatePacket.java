package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * S2C, to the fighter only: what their v2 fight looks like right now.
 *
 * <p>This is the single source the combat prompt draws from. Windows are sent as ticks
 * remaining rather than as configuration, so a client cannot disagree with the server about
 * whether a branch is still open; it only counts the number down between packets.
 *
 * @param state            {@code V2State} ordinal
 * @param branchMask       {@code ComboInput} bits open from the current combo beat, with the
 *                         {@code BranchFlavor} of light and heavy above them; one byte in all
 * @param windowTicksLeft  ticks left to pick a branch; 0 when no window is open
 * @param windowTicksTotal the window's full length, for the timer bar
 * @param counterTicksLeft ticks left on the super-counter window
 * @param homingTicksLeft  ticks left to chase a launched target for free
 * @param homingTargetId   entity id of that target, or -1
 * @param grabReady        grab is off cooldown and the fighter is free to start one
 * @param grabRange        how close a target must be, so the client can show the prompt in reach
 * @param grabTicksLeft    for the grabber, ticks until the throw; for the victim, ticks left to tech
 * @param counterAttackerId entity id of whoever landed the hit that opened the counter window, or
 *                          -1. A counter only answers the fighter's own lock, so the client shows
 *                          the prompt only when this is the entity it is locked on
 * @param dashTicksLeft    ticks left to cross the dash target, during flight or after the hit
 * @param dashTargetId     validated dash target, or -1 when no continuation is available
 */
public record CombatV2StatePacket(
        int state,
        int branchMask,
        int windowTicksLeft,
        int windowTicksTotal,
        int counterTicksLeft,
        int homingTicksLeft,
        int homingTargetId,
        boolean grabReady,
        float grabRange,
        int grabTicksLeft,
        int counterAttackerId,
        int dashTicksLeft,
        int dashTargetId) {

    public CombatV2StatePacket(FriendlyByteBuf buf) {
        this(buf.readByte(), buf.readUnsignedByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(state);
        buf.writeByte(branchMask);
        buf.writeVarInt(windowTicksLeft);
        buf.writeVarInt(windowTicksTotal);
        buf.writeVarInt(counterTicksLeft);
        buf.writeVarInt(homingTicksLeft);
        buf.writeVarInt(homingTargetId);
        buf.writeBoolean(grabReady);
        buf.writeFloat(grabRange);
        buf.writeVarInt(grabTicksLeft);
        buf.writeVarInt(counterAttackerId);
        buf.writeVarInt(dashTicksLeft);
        buf.writeVarInt(dashTargetId);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() ->
                net.bullettrain.xenopixelsmod.client.combat.v2.V2ClientState.apply(this));
        ctx.setPacketHandled(true);
    }
}
