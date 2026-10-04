package net.bullettrain.xenopixelsmod.features.tournament;

import java.util.UUID;

/**
 * One KotH challenge. {@link #winner()} is null until an admin reports the result.
 *
 * <p>No client win path — only {@code /xenotourney result} (or a later auto-KO PR) commits winners.
 */
public record MatchResult(
        String matchId,
        UUID entrantA,
        UUID entrantB,
        int arenaIndex,
        UUID winner,
        String reportedBy
) {
    public MatchResult {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId required");
        }
        reportedBy = reportedBy == null ? "" : reportedBy;
    }

    public boolean isComplete() {
        return winner != null;
    }

    public MatchResult withWinner(UUID nextWinner, String nextReportedBy) {
        return new MatchResult(matchId, entrantA, entrantB, arenaIndex, nextWinner, nextReportedBy);
    }
}
