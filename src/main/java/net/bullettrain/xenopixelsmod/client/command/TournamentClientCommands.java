package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
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
 * Dedicated client open for the tournament queue UI.
 *
 * <p>Uses {@code /xenotourneyui} (not {@code /xenotourney …}) so the server
 * {@code /xenotourney join|leave|status|…} tree is not shadowed. Primary open path remains
 * server {@code /xenotourney status}, which syncs the S2C snapshot and sends
 * {@code OpenQueueScreenPacket} on channel {@code tournament}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class TournamentClientCommands {
    private TournamentClientCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenotourneyui")
                .executes(ctx -> open(ctx.getSource())));
    }

    private static int open(CommandSourceStack source) {
        ClientScreens.openTournamentQueue.run();
        source.sendSuccess(() -> Component.literal(
                "Opened tournament queue UI (last S2C snapshot)."), false);
        return 1;
    }
}
