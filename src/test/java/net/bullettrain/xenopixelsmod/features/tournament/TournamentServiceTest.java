package net.bullettrain.xenopixelsmod.features.tournament;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TournamentServiceTest {
    @Test
    void enqueueCapsAtTwelveAndDisabledFlagBlocksJoin() {
        TournamentSavedData data = new TournamentSavedData();
        assertTrue(TournamentService.enqueueInto(data, uuid(1)));
        for (int i = 2; i <= 12; i++) {
            assertTrue(TournamentService.enqueueInto(data, uuid(i)));
        }
        assertFalse(TournamentService.enqueueInto(data, uuid(13)));
        assertEquals(12, data.queuedCount());
        // Disabled flag is checked by the enabled-aware helper (mirrors enqueue(ServerPlayer)).
        assertFalse(TournamentService.enqueueInto(data, uuid(99), false));
        assertEquals(12, data.queuedCount());
    }

    @Test
    void reportResultRecordsWinnerWithoutGrantingRole() {
        TournamentSavedData data = new TournamentSavedData();
        String matchId = TournamentService.startMatchForTest(data, uuid(1), uuid(2), 0);
        assertTrue(TournamentService.reportResultInto(data, matchId, uuid(1), "admin:test"));
        assertEquals(uuid(1), data.match(matchId).winner());
        // reportResultInto commits only — angel grant is tryAwardAngel / reportResult (see TournamentAwardTest)
        assertTrue(TournamentService.awardHook == null);
    }

    private static UUID uuid(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }
}
