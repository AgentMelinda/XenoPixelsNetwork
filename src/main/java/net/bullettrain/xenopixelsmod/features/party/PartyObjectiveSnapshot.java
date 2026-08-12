package net.bullettrain.xenopixelsmod.features.party;

/** Wire-safe party objective presented by an optional future quest/NPC provider. */
public record PartyObjectiveSnapshot(
        String sourceId,
        String objectiveId,
        String title,
        int progress,
        int goal,
        String state,
        boolean canStart
) {
    public static final PartyObjectiveSnapshot EMPTY =
            new PartyObjectiveSnapshot("", "", "", 0, 0, "", false);

    public boolean present() {
        return sourceId != null && !sourceId.isBlank() && title != null && !title.isBlank();
    }
}
