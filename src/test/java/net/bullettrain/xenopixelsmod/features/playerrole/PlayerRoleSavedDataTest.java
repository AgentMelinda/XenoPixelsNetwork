package net.bullettrain.xenopixelsmod.features.playerrole;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerRoleSavedDataTest {
    @Test
    void roundTripPersistsAngelAndUnknownBecomesNone() {
        PlayerRoleSavedData data = new PlayerRoleSavedData();
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        data.setRole(id, PlayerRoleId.ANGEL);
        CompoundTag tag = data.save(new CompoundTag(), null);
        PlayerRoleSavedData loaded = PlayerRoleSavedData.load(tag, null);
        assertEquals(PlayerRoleId.ANGEL, loaded.roleOf(id));
        assertEquals(PlayerRoleId.NONE, PlayerRoleId.parse("not-a-role"));
        assertEquals(PlayerRoleId.NONE, loaded.roleOf(UUID.randomUUID()));
    }
}
