package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.junit.jupiter.api.Test;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionNameArgumentTest {
    @Test void namespacedAndQuotedRegionNamesKeepFollowingArguments() throws Exception {
        CommandDispatcher<String> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<String>literal("set").then(RequiredArgumentBuilder.<String, String>argument("region", new RegionNameArgument())
                .then(RequiredArgumentBuilder.<String, String>argument("state", StringArgumentType.word()).executes(ctx -> {
                    assertEquals(ctx.getSource(), StringArgumentType.getString(ctx, "region"));
                    assertEquals("denied", StringArgumentType.getString(ctx, "state"));
                    return 1;
                }))));
        assertEquals(1, dispatcher.execute("set dragonminez:otherworld denied", "dragonminez:otherworld"));
        assertEquals(1, dispatcher.execute("set palace denied", "palace"));
        assertEquals(1, dispatcher.execute("set \"King Kai\" denied", "King Kai"));
    }
}
