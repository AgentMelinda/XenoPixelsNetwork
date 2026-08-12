package net.bullettrain.xenopixelsmod.features.party;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Extension point for a future CustomNPC or quest integration.
 *
 * <p>No provider is installed by XenoPixels itself. The party screen hides its objective panel
 * until another integration registers one, so this API does not pretend an unfinished quest
 * engine exists.</p>
 */
public interface PartyObjectiveProvider {
    PartyObjectiveSnapshot snapshot(ServerPlayer viewer, UUID partyId);

    /** Server-authoritative request from the party leader. */
    boolean start(ServerPlayer leader, UUID partyId, String objectiveId);
}
