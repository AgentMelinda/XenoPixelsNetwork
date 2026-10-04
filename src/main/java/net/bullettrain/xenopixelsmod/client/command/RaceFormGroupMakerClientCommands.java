package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client open for the race form-group maker green shell ({@code /xenoraceformui}).
 *
 * <p>Unknown race ids are refused by {@code RaceFormGroupGuard} inside the screen (KD15).
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class RaceFormGroupMakerClientCommands {
    private RaceFormGroupMakerClientCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenoraceformui")
                .executes(ctx -> open(ctx.getSource(), "saiyan", ""))
                .then(Commands.argument("race", StringArgumentType.word())
                        .executes(ctx -> open(ctx.getSource(),
                                StringArgumentType.getString(ctx, "race"), ""))
                        .then(Commands.argument("group", StringArgumentType.word())
                                .executes(ctx -> open(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "race"),
                                        StringArgumentType.getString(ctx, "group"))))));
    }

    private static int open(CommandSourceStack source, String race, String group) {
        ClientScreens.openRaceFormGroupMaker.accept(race, group);
        source.sendSuccess(() -> Component.literal(
                "Opened race form-group maker for " + race
                        + (group == null || group.isBlank() ? "" : "/" + group) + "."), false);
        return 1;
    }
}
