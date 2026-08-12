package net.bullettrain.xenopixelsmod.features.party;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Single optional provider registry for future quest/CustomNPC integration. */
public final class PartyObjectives {
    private static volatile PartyObjectiveProvider provider;

    private PartyObjectives() {
    }

    public static void register(PartyObjectiveProvider value) {
        provider = value;
    }

    public static void clear(PartyObjectiveProvider value) {
        if (provider == value) provider = null;
    }

    public static PartyObjectiveSnapshot snapshot(ServerPlayer viewer, UUID partyId) {
        PartyObjectiveProvider p = provider;
        if (p == null || viewer == null || partyId == null) return PartyObjectiveSnapshot.EMPTY;
        try {
            PartyObjectiveSnapshot snapshot = p.snapshot(viewer, partyId);
            return snapshot == null ? PartyObjectiveSnapshot.EMPTY : snapshot;
        } catch (RuntimeException ignored) {
            return PartyObjectiveSnapshot.EMPTY;
        }
    }

    public static boolean start(ServerPlayer leader, UUID partyId, String objectiveId) {
        PartyObjectiveProvider p = provider;
        if (p == null || leader == null || partyId == null) return false;
        try {
            return p.start(leader, partyId, objectiveId == null ? "" : objectiveId);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
