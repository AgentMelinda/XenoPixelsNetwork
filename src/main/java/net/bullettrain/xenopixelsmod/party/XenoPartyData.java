package net.bullettrain.xenopixelsmod.party;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.*;

/**
 * Server-side party management system for future quest sharing and cooperative features.
 * Parties are created by players and can include up to 4 members total.
 * Party data syncs to all members when changes occur.
 */
public class XenoPartyData implements INBTSerializable<CompoundTag> {
    private UUID partyId;
    private UUID leaderUuid;
    private String leaderName;
    private final Set<UUID> memberUuids = new LinkedHashSet<>();
    private final Map<UUID, String> memberNames = new HashMap<>();
    private boolean questSharingEnabled = false;
    
    public static final int MAX_PARTY_SIZE = 4;

    public boolean hasParty() {
        return partyId != null;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public String getLeaderName() {
        return leaderName == null ? "" : leaderName;
    }

    public Set<UUID> getMemberUuids() {
        return Collections.unmodifiableSet(memberUuids);
    }

    public Map<UUID, String> getMemberNames() {
        return Collections.unmodifiableMap(memberNames);
    }

    public boolean isQuestSharingEnabled() {
        return questSharingEnabled;
    }

    public void setQuestSharingEnabled(boolean enabled) {
        this.questSharingEnabled = enabled;
    }

    public boolean isLeader(UUID playerUuid) {
        return leaderUuid != null && leaderUuid.equals(playerUuid);
    }

    public boolean isMember(UUID playerUuid) {
        return memberUuids.contains(playerUuid);
    }

    /**
     * Creates a new party with the given player as leader.
     * @param player The player creating the party
     * @return true if party was created successfully, false if player already in a party
     */
    public boolean createParty(Player player) {
        if (hasParty()) {
            return false;
        }
        partyId = UUID.randomUUID();
        leaderUuid = player.getUUID();
        leaderName = player.getName().getString();
        memberUuids.clear();
        memberUuids.add(player.getUUID());
        memberNames.clear();
        memberNames.put(player.getUUID(), player.getName().getString());
        questSharingEnabled = false;
        return true;
    }

    /**
     * Disbands the current party.
     */
    public void disbandParty() {
        partyId = null;
        leaderUuid = null;
        leaderName = "";
        memberUuids.clear();
        memberNames.clear();
        questSharingEnabled = false;
    }

    /**
     * Adds a player to the party.
     * @param player The player to add
     * @return true if added successfully, false if party is full or player already in party
     */
    public boolean addMember(Player player) {
        if (!hasParty()) {
            return false;
        }
        if (memberUuids.size() >= MAX_PARTY_SIZE) {
            return false;
        }
        if (memberUuids.contains(player.getUUID())) {
            return false;
        }
        memberUuids.add(player.getUUID());
        memberNames.put(player.getUUID(), player.getName().getString());
        return true;
    }

    /**
     * Removes a player from the party.
     * @param playerUuid The UUID of the player to remove
     * @return true if removed successfully
     */
    public boolean removeMember(UUID playerUuid) {
        if (!hasParty()) {
            return false;
        }
        
        boolean removed = memberUuids.remove(playerUuid);
        memberNames.remove(playerUuid);
        
        // If leader leaves, assign new leader
        if (removed && leaderUuid != null && leaderUuid.equals(playerUuid)) {
            if (!memberUuids.isEmpty()) {
                UUID newLeader = memberUuids.iterator().next();
                leaderUuid = newLeader;
                leaderName = memberNames.getOrDefault(newLeader, "");
            } else {
                disbandParty();
            }
        }
        
        // Disband if no members left
        if (memberUuids.isEmpty()) {
            disbandParty();
        }
        
        return removed;
    }

    /**
     * Gets the size of the party.
     * @return Number of members in the party
     */
    public int getPartySize() {
        return memberUuids.size();
    }

    /**
     * Checks if a player can join (party exists and has space).
     * @return true if player can join
     */
    public boolean canJoin() {
        return hasParty() && memberUuids.size() < MAX_PARTY_SIZE;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (partyId != null) {
            tag.putUUID("PartyId", partyId);
        }
        if (leaderUuid != null) {
            tag.putUUID("LeaderUuid", leaderUuid);
            tag.putString("LeaderName", leaderName);
        }
        tag.putBoolean("QuestSharing", questSharingEnabled);
        
        ListTag memberList = new ListTag();
        for (UUID uuid : memberUuids) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("Uuid", uuid);
            memberTag.putString("Name", memberNames.getOrDefault(uuid, ""));
            memberList.add(memberTag);
        }
        tag.put("Members", memberList);
        
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag.hasUUID("PartyId")) {
            partyId = tag.getUUID("PartyId");
        } else {
            partyId = null;
        }
        
        if (tag.hasUUID("LeaderUuid")) {
            leaderUuid = tag.getUUID("LeaderUuid");
            leaderName = tag.getString("LeaderName");
        } else {
            leaderUuid = null;
            leaderName = "";
        }
        
        questSharingEnabled = tag.getBoolean("QuestSharing");
        
        memberUuids.clear();
        memberNames.clear();
        if (tag.contains("Members", Tag.TAG_LIST)) {
            ListTag memberList = tag.getList("Members", Tag.TAG_COMPOUND);
            for (int i = 0; i < memberList.size(); i++) {
                CompoundTag memberTag = memberList.getCompound(i);
                UUID uuid = memberTag.getUUID("Uuid");
                String name = memberTag.getString("Name");
                memberUuids.add(uuid);
                memberNames.put(uuid, name);
            }
        }
    }

    /**
     * Copies data from another party data instance.
     */
    public void copyFrom(XenoPartyData other) {
        this.partyId = other.partyId;
        this.leaderUuid = other.leaderUuid;
        this.leaderName = other.leaderName;
        this.memberUuids.clear();
        this.memberUuids.addAll(other.memberUuids);
        this.memberNames.clear();
        this.memberNames.putAll(other.memberNames);
        this.questSharingEnabled = other.questSharingEnabled;
    }
}
