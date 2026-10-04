package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.NpcAPI;

import static org.junit.jupiter.api.Assertions.*;

class NativeNpcApiTest {
    /** An instance with no server, as before startup or after shutdown. Never registered. */
    private final NativeNpcApi offline = new NativeNpcApi(() -> null);

    @Test
    void modConstructionRegistersTheNativeImplementationOnce() {
        // neoForge.unitTest constructs the mod before tests run.
        assertTrue(NpcAPI.IsAvailable());
        NpcAPI registered = NpcAPI.Instance();
        assertInstanceOf(NativeNpcApi.class, registered);
        NativeNpcApi.register();
        assertSame(registered, NpcAPI.Instance(), "a second register() keeps the first instance");
    }

    @Test
    void worldDependentCallsFailClearlyWithoutARunningServer() {
        var error = assertThrows(IllegalStateException.class, offline::getIWorlds);
        assertTrue(error.getMessage().contains("getIWorlds"));
        assertThrows(IllegalStateException.class, () -> offline.getIWorld("minecraft:overworld"));
        assertThrows(IllegalStateException.class, offline::getLevelDir);
    }

    @Test
    void worldFreeConversionsWorkWithoutAServer() {
        assertEquals(3, offline.getIPos(3.7, 0, 0).getX());
        CompoundTag tag = new CompoundTag();
        offline.getINbt(tag).setInteger("n", 5);
        assertEquals(5, tag.getInt("n"));
        assertEquals(7, offline.stringToNbt("{a:7}").getInteger("a"));
        assertThrows(CustomNPCsException.class, () -> offline.stringToNbt("{a:"));
        assertNull(offline.getIEntity(null));
        assertNull(offline.getIDamageSource(null));
        assertSame(offline.events(), offline.events());
    }

    @Test
    void unsupportedFacilitiesNameTheirMethod() {
        var error = assertThrows(UnsupportedOperationException.class, offline::getRecipes);
        assertTrue(error.getMessage().contains("NpcAPI.getRecipes"));
        assertThrows(UnsupportedOperationException.class, () -> offline.getRandomName(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> offline.registerScriptEvent(Object.class));
    }

    @Test
    void invalidArgumentsFailBeforeTouchingAServer() {
        assertThrows(IllegalArgumentException.class, () -> offline.getIWorld("not a valid id!"));
        assertThrows(IllegalArgumentException.class, () -> offline.executeCommand(null, "say hi"));
    }
}
