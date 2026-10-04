package net.bullettrain.xenopixelsmod.npc.script.api.xeno.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerXenoEventsTest {
    @Test
    void mainHandFilterAcceptsOnlyTheMainHand() {
        assertTrue(PlayerXenoEvents.mainHand(net.minecraft.world.InteractionHand.MAIN_HAND));
        assertFalse(PlayerXenoEvents.mainHand(net.minecraft.world.InteractionHand.OFF_HAND));
    }

    @Test
    void breakExperienceIsMatchedToTheSameBreak() {
        var key = PlayerXenoEvents.breakKey("minecraft:overworld", new net.minecraft.core.BlockPos(1, 2, 3), java.util.UUID.randomUUID(), 40L);
        PlayerXenoEvents.rememberBreakExp(key, 7);
        assertEquals(7, PlayerXenoEvents.takeBreakExp(key));
        assertNull(PlayerXenoEvents.takeBreakExp(key), "taken once");
    }
}
