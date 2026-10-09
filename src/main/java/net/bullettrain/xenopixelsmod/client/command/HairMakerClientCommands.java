package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.bullettrain.xenopixelsmod.client.maker.MakerAccess;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client open for the advanced Hair Editor ({@code /xenohairui}).
 *
 * <p>Same screen as {@code /xenomaker hair}. Apply path cites
 * {@code UpdateCustomHairC2S#handle} (path READY / runtime unverified); replace-current-style
 * wipe. Export codes remain lab-encoded.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class HairMakerClientCommands {
    private HairMakerClientCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenohairui")
                .executes(ctx -> open(ctx.getSource())));
    }

    private static int open(CommandSourceStack source) {
        if (!MakerAccess.canOpenCosmeticMaker()) {
            source.sendFailure(MakerAccess.denyCosmetic());
            return 0;
        }
        ClientScreens.openHairMaker.run();
        source.sendSuccess(() -> Component.literal(
                "Opened Hair Editor (replace-current-style Apply; path READY / runtime unverified)."),
                false);
        return 1;
    }
}
