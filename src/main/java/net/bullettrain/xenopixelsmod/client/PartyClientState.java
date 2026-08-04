package net.bullettrain.xenopixelsmod.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;

/**
 * Client-side state for party system.
 * Stores synced party data from server for HUD rendering.
 */
@OnlyIn(Dist.CLIENT)
public class PartyClientState {
    private static UUID partyId;
    private static UUID leaderUuid;
    private static String leaderName = "";
    private static final Set<UUID> memberUuids = new LinkedHashSet<>();
    private static final Map<UUID, String> memberNames = new HashMap<>();
    private static boolean questSharingEnabled = false;

    public static boolean hasParty() {
        return partyId != null;
    }

    public static UUID getPartyId() {
        return partyId;
    }

    public static UUID getLeaderUuid() {
        return leaderUuid;
    }

    public static String getLeaderName() {
        return leaderName;
    }

    public static Set<UUID> getMemberUuids() {
        return Collections.unmodifiableSet(memberUuids);
    }

    public static Map<UUID, String> getMemberNames() {
        return Collections.unmodifiableMap(memberNames);
    }

    public static boolean isQuestSharingEnabled() {
        return questSharingEnabled;
    }

    public static int getPartySize() {
        return memberUuids.size();
    }

    public static boolean isLeader(UUID playerUuid) {
        return leaderUuid != null && leaderUuid.equals(playerUuid);
    }

    public static boolean isMember(UUID playerUuid) {
        return memberUuids.contains(playerUuid);
    }

    /**
     * Sets party data from server sync packet.
     */
    public static void setPartyData(UUID partyId, UUID leaderUuid, String leaderName,
                                    Set<UUID> memberUuids, Map<UUID, String> memberNames,
                                    boolean questSharingEnabled) {
        PartyClientState.partyId = partyId;
        PartyClientState.leaderUuid = leaderUuid;
        PartyClientState.leaderName = leaderName == null ? "" : leaderName;
        PartyClientState.memberUuids.clear();
        PartyClientState.memberUuids.addAll(memberUuids);
        PartyClientState.memberNames.clear();
        PartyClientState.memberNames.putAll(memberNames);
        PartyClientState.questSharingEnabled = questSharingEnabled;
    }

    /**
     * Clears all party data (when leaving party or disconnecting).
     */
    public static void clear() {
        partyId = null;
        leaderUuid = null;
        leaderName = "";
        memberUuids.clear();
        memberNames.clear();
        questSharingEnabled = false;
    }
}
