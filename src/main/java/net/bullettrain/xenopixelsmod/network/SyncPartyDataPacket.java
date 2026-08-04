package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.party.XenoPartyData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * Packet to sync party data from server to client.
 */
public class SyncPartyDataPacket {
    private final UUID partyId;
    private final UUID leaderUuid;
    private final String leaderName;
    private final Set<UUID> memberUuids;
    private final Map<UUID, String> memberNames;
    private final boolean questSharingEnabled;

    public SyncPartyDataPacket(UUID partyId, UUID leaderUuid, String leaderName, 
                               Set<UUID> memberUuids, Map<UUID, String> memberNames, 
                               boolean questSharingEnabled) {
        this.partyId = partyId;
        this.leaderUuid = leaderUuid;
        this.leaderName = leaderName;
        this.memberUuids = new LinkedHashSet<>(memberUuids);
        this.memberNames = new HashMap<>(memberNames);
        this.questSharingEnabled = questSharingEnabled;
    }

    public SyncPartyDataPacket(FriendlyByteBuf buf) {
        this.partyId = buf.readBoolean() ? buf.readUUID() : null;
        this.leaderUuid = buf.readBoolean() ? buf.readUUID() : null;
        this.leaderName = buf.readUtf(64);
        int memberCount = buf.readInt();
        this.memberUuids = new LinkedHashSet<>();
        this.memberNames = new HashMap<>();
        for (int i = 0; i < memberCount; i++) {
            UUID uuid = buf.readUUID();
            String name = buf.readUtf(64);
            memberUuids.add(uuid);
            memberNames.put(uuid, name);
        }
        this.questSharingEnabled = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        if (partyId != null) {
            buf.writeBoolean(true);
            buf.writeUUID(partyId);
        } else {
            buf.writeBoolean(false);
        }
        
        if (leaderUuid != null) {
            buf.writeBoolean(true);
            buf.writeUUID(leaderUuid);
        } else {
            buf.writeBoolean(false);
        }
        
        buf.writeUtf(leaderName, 64);
        buf.writeInt(memberUuids.size());
        for (UUID uuid : memberUuids) {
            buf.writeUUID(uuid);
            buf.writeUtf(memberNames.getOrDefault(uuid, ""), 64);
        }
        buf.writeBoolean(questSharingEnabled);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            net.bullettrain.xenopixelsmod.client.PartyClientState.setPartyData(
                partyId, leaderUuid, leaderName, memberUuids, memberNames, questSharingEnabled
            );
        });
        context.setPacketHandled(true);
    }

    public UUID getPartyId() { return partyId; }
    public UUID getLeaderUuid() { return leaderUuid; }
    public String getLeaderName() { return leaderName; }
    public Set<UUID> getMemberUuids() { return Collections.unmodifiableSet(memberUuids); }
    public Map<UUID, String> getMemberNames() { return Collections.unmodifiableMap(memberNames); }
    public boolean isQuestSharingEnabled() { return questSharingEnabled; }
}
