package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.dmz.DmzSagaSpawnCompat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator diagnostics and recovery for DragonMineZ saga enemies. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DmzSagaCommands {
    private DmzSagaCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenodmz")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("saga")
                        .then(Commands.literal("diagnose")
                                .executes(context -> diagnose(context.getSource(), context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> diagnose(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("respawn")
                                .executes(context -> respawn(context.getSource(), context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> respawn(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))));
    }

    private static int diagnose(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.literal("DMZ saga spawn diagnostics for "
                + player.getGameProfile().getName()), false);
        for (String line : DmzSagaSpawnCompat.diagnose(player)) {
            source.sendSuccess(() -> Component.literal("- " + line), false);
        }
        return 1;
    }

    private static int respawn(CommandSourceStack source, ServerPlayer player) {
        DmzSagaSpawnCompat.RepairResult result = DmzSagaSpawnCompat.repairActiveQuest(player);
        if (!result.activeQuest()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "DMZ saga spawn repair: spawned=" + result.spawned()
                        + ", missing=" + result.missing()
                        + ", result=" + result.message()), true);
        return result.missing() == 0 ? 1 : 0;
    }
}
