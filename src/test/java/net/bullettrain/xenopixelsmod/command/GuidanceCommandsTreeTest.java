package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 2026-09-28 owner: speed and gravity settable by command too, not only the computer GUI and CC. */
class GuidanceCommandsTreeTest {
    @Test
    void theGuidanceComputerSettingsHaveCommands() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(GuidanceCommands.command());
        var root = dispatcher.getRoot().getChild("xenoguidance");
        assertNotNull(root);
        for (String name : new String[] {"system", "computer", "speed", "gravity", "drag"}) {
            assertNotNull(root.getChild(name), name);
        }
        assertNotNull(root.getChild("speed").getChild("level"));
        assertNotNull(root.getChild("gravity").getChild("metersPerSecondSquared"));
        assertNotNull(root.getChild("drag").getChild("coefficient"));
    }
}
