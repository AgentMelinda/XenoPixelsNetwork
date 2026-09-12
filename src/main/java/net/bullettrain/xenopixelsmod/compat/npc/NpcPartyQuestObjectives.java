package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.features.party.PartyManager;
import net.bullettrain.xenopixelsmod.features.party.PartyObjectiveProvider;
import net.bullettrain.xenopixelsmod.features.party.PartyObjectiveSnapshot;
import net.bullettrain.xenopixelsmod.features.party.PartyObjectives;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Surfaces an active Xeno/NPC quest on the party screen. */
public final class NpcPartyQuestObjectives implements PartyObjectiveProvider {
    private NpcPartyQuestObjectives() {}

    public static void register() {
        PartyObjectives.register(new NpcPartyQuestObjectives());
    }

    @Override
    public PartyObjectiveSnapshot snapshot(ServerPlayer viewer, UUID partyId) {
        XenoPlayerData data = activeQuest(viewer);
        if (data == null) {
            for (UUID id : PartyManager.membersOf(viewer)) {
                ServerPlayer member = viewer.getServer().getPlayerList().getPlayer(id);
                data = activeQuest(member);
                if (data != null) break;
            }
        }
        if (data == null) return PartyObjectiveSnapshot.EMPTY;
        return new PartyObjectiveSnapshot(
                "npc",
                data.getQuestId(),
                data.getQuestId(),
                data.getQuestProgress(),
                data.getQuestTarget(),
                "active",
                false);
    }

    @Override
    public boolean start(ServerPlayer leader, UUID partyId, String objectiveId) {
        return false;
    }

    private static XenoPlayerData activeQuest(ServerPlayer player) {
        if (player == null) return null;
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        return data != null && data.hasActiveQuest() ? data : null;
    }
}
