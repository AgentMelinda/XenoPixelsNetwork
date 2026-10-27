package net.bullettrain.xenopixelsmod.command;

import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Self-service access to DMZ's saved flight speed percentage, using its native stats synchronization. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class FlightSpeedCommands {
    private FlightSpeedCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenoflight")
                .executes(ctx -> speed(ctx.getSource(), false))
                .then(Commands.literal("status").executes(ctx -> speed(ctx.getSource(), false)))
                .then(Commands.literal("reset").executes(ctx -> speed(ctx.getSource(), true))));
    }

    private static int speed(CommandSourceStack source, boolean reset) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (stats == null) {
            source.sendFailure(Component.literal("DragonMineZ character stats are not available."));
            return 0;
        }
        if (reset) {
            stats.getResources().setFlightSpeedLimit(100);
            // Same synchronization used by DMZ 2.2's SetFlightSpeedLimitC2S handler.
            StatsSyncS2C.sendRequested(player);
        }
        int limit = stats.getResources().getFlightSpeedLimit();
        source.sendSuccess(() -> Component.literal("DMZ Flight Speed: " + limit + "%"
                + (reset ? " (restored to 100%; DMZ controls normal flight and Ctrl boost)"
                         : ". Use /xenoflight reset to restore 100%.")), false);
        return 1;
    }
}
