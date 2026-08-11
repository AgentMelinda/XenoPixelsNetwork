package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → client: the members of your party.
 *
 * <p>UUIDs only. The HUD resolves each one against the client's own entity list to read a name,
 * skin and stats, so sending those would be duplicating data the client already has for anyone
 * it can see — and would go stale the moment a member transformed or took a hit.
 *
 * <p>An empty list means "not in a party", which is also what a leaver is sent so their HUD
 * clears immediately rather than waiting for a timeout.
 */
public class PartySyncPacket {

    /** Guard against a malformed packet allocating an absurd list. */
    private static final int MAX_MEMBERS = 16;

    private final List<UUID> members;

    public PartySyncPacket(List<UUID> members) {
        this.members = members == null ? List.of() : members;
    }

    public PartySyncPacket(FriendlyByteBuf buf) {
        int count = Math.min(MAX_MEMBERS, buf.readVarInt());
        List<UUID> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++) read.add(buf.readUUID());
        this.members = read;
    }

    public void encode(FriendlyByteBuf buf) {
        int count = Math.min(MAX_MEMBERS, members.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) buf.writeUUID(members.get(i));
    }

    public List<UUID> members() {
        return members;
    }

    public static void handle(PartySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientScreens.receiveParty.accept(msg));
        ctx.get().setPacketHandled(true);
    }
}
