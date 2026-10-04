package net.bullettrain.xenopixelsmod.features.tournament;

import net.bullettrain.xenopixelsmod.features.playerrole.PlayerRoleId;
import net.bullettrain.xenopixelsmod.features.playerrole.PlayerRoleSavedData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TournamentAwardTest {
    @AfterEach
    void clearAwardHook() {
        TournamentService.awardHook = null;
    }

    @Test
    void adminResultGrantsAngelWithTournamentSource() {
        TournamentSavedData data = new TournamentSavedData();
        PlayerRoleSavedData roles = new PlayerRoleSavedData();
        List<String> sources = new ArrayList<>();
        TournamentService.awardHook = (id, source) -> {
            roles.setRole(id, PlayerRoleId.ANGEL);
            sources.add(source);
        };

        UUID winner = uuid(1);
        String matchId = TournamentService.startMatchForTest(data, winner, uuid(2), 0);
        assertTrue(TournamentService.reportResultInto(data, matchId, winner, "admin:test"));
        TournamentService.tryAwardAngel(winner, matchId, null, true);

        assertEquals(uuid(1), data.match(matchId).winner());
        assertEquals(PlayerRoleId.ANGEL, roles.roleOf(winner));
        assertEquals(1, sources.size());
        assertTrue(sources.get(0).startsWith("tournament:"));
        assertEquals("tournament:" + matchId, sources.get(0));
    }

    @Test
    void awardsDisabledSkipsAngelGrant() {
        TournamentSavedData data = new TournamentSavedData();
        PlayerRoleSavedData roles = new PlayerRoleSavedData();
        List<String> sources = new ArrayList<>();
        TournamentService.awardHook = (id, source) -> {
            roles.setRole(id, PlayerRoleId.ANGEL);
            sources.add(source);
        };

        UUID winner = uuid(1);
        String matchId = TournamentService.startMatchForTest(data, winner, uuid(2), 0);
        assertTrue(TournamentService.reportResultInto(data, matchId, winner, "admin:test"));
        TournamentService.tryAwardAngel(winner, matchId, null, false);

        assertEquals(uuid(1), data.match(matchId).winner());
        assertEquals(PlayerRoleId.NONE, roles.roleOf(winner));
        assertTrue(sources.isEmpty());
    }

    @Test
    void invalidWinnerDoesNotAward() {
        TournamentSavedData data = new TournamentSavedData();
        PlayerRoleSavedData roles = new PlayerRoleSavedData();
        List<String> sources = new ArrayList<>();
        TournamentService.awardHook = (id, source) -> {
            roles.setRole(id, PlayerRoleId.ANGEL);
            sources.add(source);
        };

        String matchId = TournamentService.startMatchForTest(data, uuid(1), uuid(2), 0);
        assertFalse(TournamentService.reportResultInto(data, matchId, uuid(99), "admin:test"));
        // Production only awards after a successful commit; mirror that here.
        assertEquals(PlayerRoleId.NONE, roles.roleOf(uuid(99)));
        assertTrue(sources.isEmpty());
    }

    private static UUID uuid(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }
}
