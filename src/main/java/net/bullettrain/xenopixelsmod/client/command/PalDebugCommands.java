package net.bullettrain.xenopixelsmod.client.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * PAL was removed from this mod. {@code /xenobt3} is the live GeckoLib diagnostic.
 */
@EventBusSubscriber(modid = "xenopixelsmod", value = Dist.CLIENT)
public final class PalDebugCommands {

    private PalDebugCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenopal")
                .executes(ctx -> gone(ctx.getSource())));
    }

    private static int gone(CommandSourceStack source) {
        source.sendFailure(Component.literal(
                "PlayerAnimationLibrary is not used. Combat poses are DragonMineZ GeckoLib. Try /xenobt3 list"));
        return 0;
    }
}
