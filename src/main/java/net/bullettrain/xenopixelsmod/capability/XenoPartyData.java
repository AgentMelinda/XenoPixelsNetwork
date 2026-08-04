package net.bullettrain.xenopixelsmod.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

import java.util.*;

/**
 * Server-side party system capability for quest integration.
 * 
 * <p>Parties are created when players form a group, persist across sessions,
 * and integrate with the quest system for cooperative objectives.</p>
 */
@AutoRegisterCapability
public class XenoPartyData {
    private UUID partyId;
    private UUID leaderUuid;
    private String leaderName = "";
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Map<UUID, String> memberNames = new HashMap<>();
    private String activeQuestId = "";
    private int activeQuestProgress;
    private int activeQuestTarget;
    private long createdAt;
    private boolean questsEnabled = true;

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

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public String getMemberName(UUID uuid) {
        return memberNames.getOrDefault(uuid, "Unknown");
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean isLeader(Player player) {
        return leaderUuid != null && leaderUuid.equals(player.getUUID());
    }

    public boolean isMember(Player player) {
        return members.contains(player.getUUID());
    }

    /**
     * Creates a new party with this player as leader.
     */
    public void createParty(Player leader) {
        if (hasParty()) {
            return; // Already in a party
        }
        this.partyId = UUID.randomUUID();
        this.leaderUuid = leader.getUUID();
        this.leaderName = leader.getName().getString();
        this.members.clear();
        this.members.add(leader.getUUID());
        this.memberNames.put(leader.getUUID(), leader.getName().getString());
        this.createdAt = System.currentTimeMillis();
        this.activeQuestId = "";
        this.activeQuestProgress = 0;
        this.activeQuestTarget = 0;
    }

    /**
     * Disbands the party.
     */
    public void disbandParty() {
        this.partyId = null;
        this.leaderUuid = null;
        this.leaderName = "";
        this.members.clear();
        this.memberNames.clear();
        this.activeQuestId = "";
        this.activeQuestProgress = 0;
        this.activeQuestTarget = 0;
    }

    /**
     * Adds a player to the party.
     */
    public boolean addMember(Player player) {
        if (!hasParty()) {
            return false;
        }
        if (members.contains(player.getUUID())) {
            return false; // Already a member
        }
        members.add(player.getUUID());
        memberNames.put(player.getUUID(), player.getName().getString());
        return true;
    }

    /**
     * Removes a player from the party.
     */
    public boolean removeMember(Player player) {
        UUID uuid = player.getUUID();
        if (!members.remove(uuid)) {
            return false;
        }
        memberNames.remove(uuid);
        
        // If leader leaves, transfer leadership or disband
        if (uuid.equals(leaderUuid) && !members.isEmpty()) {
            UUID newLeader = members.iterator().next();
            leaderUuid = newLeader;
            leaderName = memberNames.getOrDefault(newLeader, "Unknown");
        } else if (members.isEmpty()) {
            disbandParty();
        }
        return true;
    }

    /**
     * Transfers leadership to another member.
     */
    public boolean transferLeadership(Player newLeader) {
        if (!isLeader(newLeader) && members.contains(newLeader.getUUID())) {
            leaderUuid = newLeader.getUUID();
            leaderName = newLeader.getName().getString();
            return true;
        }
        return false;
    }

    public String getActiveQuestId() {
        return activeQuestId == null ? "" : activeQuestId;
    }

    public int getActiveQuestProgress() {
        return activeQuestProgress;
    }

    public int getActiveQuestTarget() {
        return activeQuestTarget;
    }

    public boolean hasActiveQuest() {
        return !activeQuestId.isEmpty() && activeQuestTarget > 0;
    }

    /**
     * Starts a party quest.
     */
    public void startQuest(String questId, int target) {
        if (!questsEnabled) {
            return;
        }
        this.activeQuestId = questId == null ? "" : questId;
        this.activeQuestProgress = 0;
        this.activeQuestTarget = Math.max(1, target);
    }

    /**
     * Clears the active party quest.
     */
    public void clearQuest() {
        this.activeQuestId = "";
        this.activeQuestProgress = 0;
        this.activeQuestTarget = 0;
    }

    /**
     * Adds progress to the active party quest.
     * @return true if quest completed
     */
    public boolean addQuestProgress(int amount) {
        if (!hasActiveQuest()) {
            return false;
        }
        activeQuestProgress = Math.min(activeQuestTarget, activeQuestProgress + Math.max(0, amount));
        return activeQuestProgress >= activeQuestTarget;
    }

    public boolean isQuestsEnabled() {
        return questsEnabled;
    }

    public void setQuestsEnabled(boolean enabled) {
        this.questsEnabled = enabled;
    }

    public void copyFrom(XenoPartyData other) {
        if (other == null) {
            return;
        }
        this.partyId = other.partyId;
        this.leaderUuid = other.leaderUuid;
        this.leaderName = other.leaderName;
        this.members.clear();
        this.members.addAll(other.members);
        this.memberNames.clear();
        this.memberNames.putAll(other.memberNames);
        this.activeQuestId = other.activeQuestId;
        this.activeQuestProgress = other.activeQuestProgress;
        this.activeQuestTarget = other.activeQuestTarget;
        this.createdAt = other.createdAt;
        this.questsEnabled = other.questsEnabled;
    }

    public void saveNBT(CompoundTag tag) {
        if (partyId != null) {
            tag.putUUID("PartyId", partyId);
        }
        if (leaderUuid != null) {
            tag.putUUID("LeaderUuid", leaderUuid);
            tag.putString("LeaderName", getLeaderName());
        }
        
        ListTag memberList = new ListTag();
        for (UUID uuid : members) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("Uuid", uuid);
            memberTag.putString("Name", getMemberName(uuid));
            memberList.add(memberTag);
        }
        tag.put("Members", memberList);
        
        tag.putString("ActiveQuestId", getActiveQuestId());
        tag.putInt("ActiveQuestProgress", activeQuestProgress);
        tag.putInt("ActiveQuestTarget", activeQuestTarget);
        tag.putLong("CreatedAt", createdAt);
        tag.putBoolean("QuestsEnabled", questsEnabled);
    }

    public void loadNBT(CompoundTag tag) {
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
        
        members.clear();
        memberNames.clear();
        if (tag.contains("Members", Tag.TAG_LIST)) {
            ListTag memberList = tag.getList("Members", Tag.TAG_COMPOUND);
            for (int i = 0; i < memberList.size(); i++) {
                CompoundTag memberTag = memberList.getCompound(i);
                UUID uuid = memberTag.getUUID("Uuid");
                String name = memberTag.getString("Name");
                members.add(uuid);
                memberNames.put(uuid, name);
            }
        }
        
        activeQuestId = tag.getString("ActiveQuestId");
        activeQuestProgress = tag.getInt("ActiveQuestProgress");
        activeQuestTarget = tag.getInt("ActiveQuestTarget");
        createdAt = tag.getLong("CreatedAt");
        questsEnabled = tag.getBoolean("QuestsEnabled");
    }
}
