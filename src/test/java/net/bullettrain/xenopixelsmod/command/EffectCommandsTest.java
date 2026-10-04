package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.bullettrain.xenopixelsmod.fx.effek.EffectSlot;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-29: /xenofx play <slot> [scale] plays any effect where you look, facing your look, so
 * plume direction and sizes can be checked in game without launching missiles or Sparking.
 */
class EffectCommandsTest {
    @Test
    void playTakesASlotAndAnOptionalScale() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(EffectCommands.command());
        var root = dispatcher.getRoot().getChild("xenofx");
        assertNotNull(root);
        assertNotNull(root.getChild("list"));
        var slot = root.getChild("play").getChild("slot");
        assertNotNull(slot);
        assertNotNull(slot.getChild("scale"));
    }

    @Test
    void slotsAreNamedAsTheirFolders() {
        assertEquals(EffectSlot.SHIP_THRUSTER, EffectCommands.slot("ship_thruster"));
        assertEquals(EffectSlot.SPARKING_AURA, EffectCommands.slot("SPARKING_AURA"));
        assertNull(EffectCommands.slot("nope"));
    }
}
