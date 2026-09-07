package net.bullettrain.xenopixelsmod.features.party;
import com.dragonminez.server.world.data.PartySavedData;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PartyInviteDefaultsTest {
    @Test void laterInvitesPreserveLeaderSettingOnNativeParty() {
        UUID leader = UUID.randomUUID();
        var party = new PartySavedData.PartyInstance(UUID.randomUUID(), leader, List.of(leader), true);
        assertTrue(PartyManager.applyCreationDefaults(party, true, false));
        assertFalse(party.isPvpEnabled());
        party.setPvpEnabled(true);
        assertFalse(PartyManager.applyCreationDefaults(party, false, false));
        assertTrue(party.isPvpEnabled());
        party.setPvpEnabled(false);
        assertFalse(PartyManager.applyCreationDefaults(party, false, true));
        assertFalse(party.isPvpEnabled());
    }
}
