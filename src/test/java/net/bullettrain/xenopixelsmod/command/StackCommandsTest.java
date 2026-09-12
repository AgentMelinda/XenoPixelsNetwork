package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shape test for the {@code /stack} command tree.
 *
 * <p>Only the parse tree is exercised here; actually running a branch needs a live server, a
 * DragonMineZ stats capability and a loaded NPC, so those paths stay manual.
 */
class StackCommandsTest {
    private static CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeAll
    static void buildTree() {
        dispatcher = new CommandDispatcher<>();
        StackCommands.register(dispatcher);
    }

    @Test
    void bothPlayerAndNpcBranchesOfferOnOffAndToggle() {
        CommandNode<CommandSourceStack> stack = dispatcher.getRoot().getChild("stack");
        assertNotNull(stack, "/stack must be registered");
        assertEquals(Set.of("player", "npc"), childNames(stack));
        assertEquals(Set.of("on", "off", "toggle"), childNames(stack.getChild("player")));
        assertEquals(Set.of("on", "off", "toggle"), childNames(stack.getChild("npc")));
    }

    @Test
    void playerOnAndToggleTakeGroupFormAndAnOptionalPlayer() {
        for (String verb : List.of("on", "toggle")) {
            CommandNode<CommandSourceStack> group =
                    dispatcher.getRoot().getChild("stack").getChild("player").getChild(verb)
                            .getChild("group");
            assertNotNull(group, "/stack player " + verb + " <group>");
            CommandNode<CommandSourceStack> form = group.getChild("form");
            assertNotNull(form, "/stack player " + verb + " <group> <form>");
            assertNotNull(form.getCommand(), "the two-argument form must be executable on self");
            CommandNode<CommandSourceStack> player = form.getChild("player");
            assertNotNull(player, "/stack player " + verb + " <group> <form> [player]");
            assertNotNull(player.getCommand());
        }
    }

    @Test
    void playerOffRunsOnSelfAndOnANamedPlayer() {
        CommandNode<CommandSourceStack> off =
                dispatcher.getRoot().getChild("stack").getChild("player").getChild("off");
        assertNotNull(off.getCommand(), "/stack player off must work with no argument");
        assertNotNull(off.getChild("player"));
        assertNotNull(off.getChild("player").getCommand());
    }

    @Test
    void everyNpcBranchRequiresAnExplicitEntityTarget() {
        CommandNode<CommandSourceStack> npc =
                dispatcher.getRoot().getChild("stack").getChild("npc");
        for (String verb : List.of("on", "toggle")) {
            CommandNode<CommandSourceStack> form =
                    npc.getChild(verb).getChild("group").getChild("form");
            assertNotNull(form);
            assertTrue(form.getCommand() == null,
                    "/stack npc " + verb + " must not default to the caller");
            assertNotNull(form.getChild("entity").getCommand());
        }
        assertTrue(npc.getChild("off").getCommand() == null);
        assertNotNull(npc.getChild("off").getChild("entity").getCommand());
    }

    @Test
    void bareStackPrintsUsage() {
        assertNotNull(dispatcher.getRoot().getChild("stack").getCommand());
    }

    private static Set<String> childNames(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());
    }
}
